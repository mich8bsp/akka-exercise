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
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import cats.data.EitherT
import cats.implicits._
import com.secful.scraper.Scraper.{ScrapeImagesRequest, WebsiteContext}

import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import scala.jdk.CollectionConverters._

class HtmlParsingActor extends Actor {
  implicit val as = context.system
  implicit val ec = context.dispatcher

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
  implicit val ec = context.dispatcher

  override def receive: Receive = {
    case DownloadImagesRequest(website, urls) =>
      val capturedSender = sender()

      val downloadedImages: Future[Seq[Either[ImageDownloadError, Image]]] = Source(urls)
        .mapAsync(5)(url => HttpUtils.fetchResource(url)
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
  implicit val ec: ExecutionContext = context.dispatcher

  def writeImages(images: Seq[Image], outputDir: Path)(implicit websiteContext: WebsiteContext): Future[Seq[Either[FileWritingError, Path]]] = {
    Future.sequence(images.map(image => {
      val imageWriteTask = Future {
        val imagePath = Path.of(s"${outputDir.toString}/${image.fileName}")
        FileUtils.writeFile(imagePath,
          image.contents.asByteBuffer,
          overrideIfExists = true)
        imagePath
      }
      imageWriteTask.map(Right(_)).recover({
        case e: Throwable => Left(FileWritingError(websiteContext, image, e.getMessage))
      })
    }))
  }

  override def receive: Receive = {
    case StoreImagesRequest(website, images) => {
      val outputDir: Path = Path.of(s"/tmp/${website.name}")
      Files.createDirectories(outputDir, PosixFilePermissions.asFileAttribute(PosixFilePermission.values().toSet.asJava))

      val writeResult: Future[Seq[Either[FileWritingError, Path]]] = writeImages(images, outputDir)(website)
      sender() ! writeResult
    }
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
      .mapTo[Future[Seq[Either[ScraperError, Path]]]]
      .flatMap(identity)
      .map(results => {
        val successful = results.flatMap(_.toOption)
        val errors = results.flatMap(_.left.toOption)
        if(errors.nonEmpty){
          Left(FilesWritingError.combine(errors))
        }else{
          Right(successful)
        }
      })
  }

  override def receive: Receive = {
    case r: ScrapeImagesRequest =>
      implicit val websiteContext: WebsiteContext = r.website
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

      sender() ! scrapingResult.value
  }
}

object Scraper {
  private val scraperSystem = ActorSystem("scraper-system")
  private val scraperActor: ActorRef = scraperSystem.actorOf(Props(new ScraperActor), "scraper-actor")
  private [scraper] implicit val timeout: Timeout = 5.seconds

  def scrapeWebsite(websiteContext: WebsiteContext): Future[Either[ScraperError, Seq[Path]]] = {
    (scraperActor ? ScrapeImagesRequest(websiteContext))
      .mapTo[Future[Either[ScraperError, Seq[Path]]]]
      .flatMap(identity)(scraperSystem.dispatcher)
  }

  case class ScrapeImagesRequest(website: WebsiteContext)
  case class WebsiteContext(name: String, url: URL)
}

