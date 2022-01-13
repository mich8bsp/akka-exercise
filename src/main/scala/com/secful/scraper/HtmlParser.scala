package com.secful.scraper

import akka.actor.{Actor, ActorSystem, Props}
import cats.data.EitherT
import com.secful.scraper.HtmlParsingActor.{ParseWebsiteImages, WebsiteImageElements}
import com.secful.scraper.Scraper.WebsiteContext

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}
import cats.implicits._

class HtmlParser {

  def parse(website: WebsiteContext)
           (implicit ec: ExecutionContext,
            actorSystem: ActorSystem): Future[Either[ScraperError, WebsiteImageElements]] = {
    (for {
      htmlPage <- EitherT(
        HttpUtils.fetchResource(website.url)
          .map(r => Right(r.utf8String))
          .recover({
            case e: Throwable => Left(HtmlParsingError(website, s"Failed to fetch website: ${e.getMessage}"))
          })
      )
      elements <- EitherT.fromEither[Future](HtmlUtils.parseImages(htmlPage, website.url)
        .toEither
        .leftMap(e => HtmlParsingError(website, s"Failed to parse images: ${e.getMessage}")))
    }yield {
      WebsiteImageElements(elements)
    }).value
  }
}


class HtmlParsingActor extends Actor {
  implicit val as = context.system
  private val threadPool = Executors.newFixedThreadPool(5)
  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(threadPool)
  private val htmlParser = new HtmlParser

  override def postStop(): Unit = {
    threadPool.shutdown()
  }

  override def receive: Receive = {
    case ParseWebsiteImages(website) =>
      val capturedSender = sender()

      val parsedImagesFuture: Future[Either[ScraperError, WebsiteImageElements]] = htmlParser.parse(website)

      parsedImagesFuture.onComplete({
        case Success(res) => capturedSender ! res
        case Failure(e) =>
          println(s"Unhandled error while parsing html ${e.getMessage}")
          capturedSender ! Left(HtmlParsingError(website, "Unexpected error"))
      })
  }
}

object HtmlParsingActor {
  def props: Props = Props(new HtmlParsingActor)

  case class ParseWebsiteImages(website: WebsiteContext)
  case class WebsiteImageElements(elements: Seq[HtmlImageElement])
}