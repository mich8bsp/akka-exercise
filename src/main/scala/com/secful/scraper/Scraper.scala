package com.secful.scraper

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.Timeout
import com.secful.scraper.FileSystemActor.StoreImagesRequest
import com.secful.scraper.HtmlParsingActor.{ParseWebsiteImages, WebsiteImageElements}
import com.secful.scraper.ImageDownloadActor.DownloadImagesRequest

import java.net.URL
import java.nio.file.{Files, Path}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.concurrent.duration.DurationInt
import cats.data.EitherT
import cats.implicits._
import com.secful.scraper.Scraper.{GetScrapedImagesRequest, ScrapeImagesRequest, WebsiteContext}

import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import java.util.concurrent.Executors

class HtmlParsingActor extends Actor {
  implicit val as = context.system
  private val threadPool = Executors.newFixedThreadPool(5)
  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(threadPool)

  override def postStop(): Unit = {
    threadPool.shutdown()
  }

  override def receive: Receive = {
    case ParseWebsiteImages(website@WebsiteContext(_, url)) =>
      val capturedSender = sender()

      val parsedImagesFuture: Future[Either[ScraperError, WebsiteImageElements]] = (for {
        htmlPage <- EitherT(HttpUtils.fetchResource(url)
          .map(r => Right(r.utf8String))
          .recover({
            case e: Throwable => Left(HtmlParsingError(website, s"Failed to fetch website: ${e.getMessage}"))
          }))
        elements <- EitherT.fromEither[Future](HtmlUtils.parseImages(htmlPage, Some(url))
        .leftMap(err => HtmlParsingError(website, s"Failed to parse images: $err")))
      }yield {
        WebsiteImageElements(elements)
      }).value

      parsedImagesFuture.onComplete(res => capturedSender ! res.get)
  }
}

object HtmlParsingActor {
  def props: Props = Props(new HtmlParsingActor)

  case class ParseWebsiteImages(website: WebsiteContext)
  case class WebsiteImageElements(elements: Seq[HtmlImageElement])
}

class ImageDownloadActor extends Actor {
  implicit val as = context.system
  private val threadPool = Executors.newFixedThreadPool(10)
  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(threadPool)

  override def postStop(): Unit = {
    threadPool.shutdown()
  }

  override def receive: Receive = {
    case DownloadImagesRequest(website, urls) =>
      val capturedSender = sender()

      val downloadedImages: Future[Seq[Either[ImageDownloadError, Image]]] = Source(urls)
        .mapAsync(10)(url => HttpUtils.fetchResource(url)
          .map(bf => Image(FileUtils.getFileNameFromUrl(url), bf))
          .map(Right(_))
          .recover({
            case e: Throwable => Left(ImageDownloadError(website, url, e.getMessage))
          })
        )
        .toMat(Sink.seq)(Keep.right)
        .run()

      downloadedImages.onComplete(res => capturedSender ! res.get)
  }
}

object ImageDownloadActor {
  def props: Props = Props(new ImageDownloadActor)

  case class DownloadImagesRequest(website: WebsiteContext, urls: Seq[URL])
}

class FileSystemActor extends Actor {
  private val threadPool = Executors.newFixedThreadPool(5)
  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(threadPool)

  override def postStop(): Unit = {
    threadPool.shutdown()
  }

  def writeImages(images: Seq[Image], outputDir: Path)(implicit websiteContext: WebsiteContext): Future[Seq[Either[ImageFileWritingError, Path]]] = {
    Future.sequence(images.map(image => {
      val imageWriteTask = Future {
        val imagePath = Path.of(s"${outputDir.toString}/${image.fileName}")
        FileUtils.writeFile(imagePath,
          image.contents.asByteBuffer,
          overrideIfExists = true)
        imagePath
      }
      imageWriteTask.map(Right(_)).recover({
        case e: Throwable => Left(ImageFileWritingError(websiteContext, image, e.getMessage))
      })
    }))
  }

  override def receive: Receive = {
    case StoreImagesRequest(website, images) =>
      import scala.jdk.CollectionConverters._

      val capturedSender = sender()
      val outputDir: Path = Path.of(s"/tmp/${website.name}")
      Files.createDirectories(outputDir, PosixFilePermissions.asFileAttribute(PosixFilePermission.values().toSet.asJava))

      val writeResultFuture: Future[Seq[Either[ImageFileWritingError, Path]]] = writeImages(images, outputDir)(website)
      writeResultFuture.onComplete(res => capturedSender ! res.get)
    case GetScrapedImagesRequest(website) =>
      val capturedSender = sender()
      val imagesFuture: Future[Either[ScraperError, Seq[Path]]] = Future {
        FileUtils.listFilesInDir(Path.of(s"/tmp/${website.name}"))
      }.map(Right(_))
        .recover({
          case e: Throwable => Left(ImageFilesReadingError(website, e.getMessage))
        })

      imagesFuture.onComplete(res => capturedSender ! res.get)
  }
}

object FileSystemActor {
  def props: Props = Props(new FileSystemActor)

  case class StoreImagesRequest(website: WebsiteContext, images: Seq[Image])
}

class ScraperActor extends Actor {
  import Scraper.timeout
  private implicit val ec: ExecutionContext = context.dispatcher

  private val parserActor: ActorRef = context.actorOf(HtmlParsingActor.props)
  private val downloadActor: ActorRef = context.actorOf(ImageDownloadActor.props)
  private val fsActor: ActorRef = context.actorOf(FileSystemActor.props)

  def parseWebsiteImages(implicit website: WebsiteContext): Future[Either[ScraperError, WebsiteImageElements]] = {
    (parserActor ? ParseWebsiteImages(website))
      .mapTo[Either[ScraperError, WebsiteImageElements]]
  }

  def downloadImages(urls: Seq[URL])
                    (implicit website: WebsiteContext): Future[Either[ScraperError, Seq[Image]]] = {
    (downloadActor ? DownloadImagesRequest(website, urls))
      .mapTo[Seq[Either[ScraperError, Image]]]
      .map(results => {
        val successful = results.flatMap(_.toOption)
        val errors = results.flatMap(_.left.toOption)
        if(errors.nonEmpty){
          Left(ImagesDownloadError.combine(errors))
        }else{
          Right(successful)
        }
      })
  }

  def storeImages(images: Seq[Image])
                 (implicit website: WebsiteContext): Future[Either[ScraperError, Seq[Path]]] = {
    (fsActor ? StoreImagesRequest(website, images))
      .mapTo[Seq[Either[ScraperError, Path]]]
      .map(results => {
        val successful = results.flatMap(_.toOption)
        val errors = results.flatMap(_.left.toOption)
        if(errors.nonEmpty){
          Left(ImageFilesWritingError.combine(errors))
        }else{
          Right(successful)
        }
      })
  }

  def getStoredImages(website: WebsiteContext): Future[Either[ScraperError, Seq[Path]]] = {
    (fsActor ? GetScrapedImagesRequest(website))
      .mapTo[Either[ScraperError, Seq[Path]]]
  }

  override def receive: Receive = {
    case r: ScrapeImagesRequest =>
      implicit val websiteContext: WebsiteContext = r.website
      val capturedSender = sender()

      val scrapingResult = for {
        htmlElements <- EitherT(parseWebsiteImages)
        imageUrls = htmlElements.elements.map(_.url)
        images <- if (imageUrls.isEmpty) {
          EitherT.fromEither[Future](Left(HtmlParsingError(websiteContext, s"Contains no images")))
        } else {
          EitherT(downloadImages(imageUrls))
        }
        storedFiles <- EitherT(storeImages(images))
      }yield {
        storedFiles
      }

      scrapingResult.value.onComplete(res => capturedSender ! res.get)

    case r: GetScrapedImagesRequest =>
      val capturedSender = sender()
      val images = getStoredImages(r.website)

      images.onComplete(res => capturedSender ! res.get)
  }
}

object Scraper {
  private val scraperSystem = ActorSystem("scraper-system")
  private val scraperActor: ActorRef = scraperSystem.actorOf(Props(new ScraperActor), "scraper-actor")
  private [scraper] implicit val timeout: Timeout = 5.seconds

  def scrapeWebsite(websiteContext: WebsiteContext): Future[Either[ScraperError, Seq[Path]]] = {
    (scraperActor ? ScrapeImagesRequest(websiteContext))
      .mapTo[Either[ScraperError, Seq[Path]]]
  }

  def getScrapedImages(websiteContext: WebsiteContext): Future[Either[ScraperError, Seq[Path]]] = {
    (scraperActor ? GetScrapedImagesRequest(websiteContext))
      .mapTo[Either[ScraperError, Seq[Path]]]
  }

  case class ScrapeImagesRequest(website: WebsiteContext)
  case class GetScrapedImagesRequest(website: WebsiteContext)
  case class WebsiteContext(name: String, url: URL)
}

