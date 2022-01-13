package com.secful.scraper

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import cats.data.EitherT
import cats.implicits._
import com.secful.scraper.FileSystemActor.StoreImagesRequest
import com.secful.scraper.HtmlParsingActor.{ParseWebsiteImages, WebsiteImageElements}
import com.secful.scraper.ImageDownloadActor.DownloadImagesRequest
import com.secful.scraper.Scraper.{GetScrapedImagesRequest, ScrapeImagesRequest, WebsiteContext}

import java.net.URL
import java.nio.file.Path
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


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
      .map(_.map(_.toValidatedNec).sequence
          .leftMap(errors => ImagesDownloadError.combine(errors.toList))
          .toEither)
  }

  def storeImages(images: Seq[Image])
                 (implicit website: WebsiteContext): Future[Either[ScraperError, Seq[Path]]] = {
    (fsActor ? StoreImagesRequest(website, images))
      .mapTo[Seq[Either[ScraperError, Path]]]
      .map(_.map(_.toValidatedNec).sequence
        .leftMap(errors => ImageFilesWritingError.combine(errors.toList))
        .toEither)
  }

  def getStoredImages(website: WebsiteContext): Future[Either[ScraperError, Seq[Path]]] = {
    (fsActor ? GetScrapedImagesRequest(website))
      .mapTo[Either[ScraperError, Seq[Path]]]
  }

  override def receive: Receive = {
    case r: ScrapeImagesRequest =>
      implicit val websiteContext: WebsiteContext = r.website
      val capturedSender = sender()

      val scrapingResult: EitherT[Future, ScraperError, Seq[Path]] = for {
        htmlElements <- EitherT(parseWebsiteImages)
        imageUrls = htmlElements.elements.map(_.url)
        images <- {
          if (imageUrls.isEmpty) {
            EitherT.fromEither[Future](Right[ScraperError, Seq[Image]](Seq[Image]()))
          } else {
            EitherT(downloadImages(imageUrls))
          }
        }
        storedFiles <- {
          if (images.isEmpty) {
            EitherT.fromEither[Future](Right[ScraperError, Seq[Path]](Seq[Path]()))
          } else {
            EitherT(storeImages(images))
          }
        }
      }yield {
        storedFiles
      }

      scrapingResult.value.onComplete({
        case Success(res) => capturedSender ! res
        case Failure(e) =>
          println(s"Unhandled error while scraping ${e.getMessage}")
          capturedSender ! Left(ScrapingProcessError(websiteContext, "Unexpected error"))
      })

    case r: GetScrapedImagesRequest =>
      val capturedSender = sender()
      val images = getStoredImages(r.website)

      images.onComplete({
        case Success(res) => capturedSender ! res
        case Failure(e) =>
          println(s"Unhandled error while fetching scraped images ${e.getMessage}")
          capturedSender ! Left(ImageFilesReadingError(r.website, "Unexpected error"))
      })
  }
}

object Scraper {
  private val scraperSystem = ActorSystem("scraper-system")
  private val scraperActor: ActorRef = scraperSystem.actorOf(Props(new ScraperActor), "scraper-actor")
  private [scraper] implicit val timeout: Timeout = 15.seconds

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