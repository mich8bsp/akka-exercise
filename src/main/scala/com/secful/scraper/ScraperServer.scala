package com.secful.scraper

import com.secful.scraper.Scraper.WebsiteContext
import com.secful.scraperservice.scraper_service.{GetScrapedRequest, ScrapeRequest, ScrapedImages, ScraperGrpc}
import io.grpc.{Server, ServerBuilder, Status}

import scala.concurrent.{ExecutionContext, Future}

private class ScraperImpl(implicit ec: ExecutionContext) extends ScraperGrpc.Scraper {
  override def scrape(request: ScrapeRequest): Future[ScrapedImages] = {
    val websiteFut: Future[WebsiteContext] = request.website.map(ScraperServiceModelConversions.fromProto)
      .map(Future.successful)
      .getOrElse(Future.failed(
        Status.INVALID_ARGUMENT
          .augmentDescription("Missing website parameter in scrape request")
          .asRuntimeException()
      ))
    websiteFut.flatMap(website => {
      Scraper.scrapeWebsite(website).map({
        case Left(error) => throw Status.INTERNAL
          .augmentDescription(error.toString)
          .asRuntimeException()
        case Right(images) => ScrapedImages(images.map(_.toString))
      })
    })
  }

  override def getScraped(request: GetScrapedRequest): Future[ScrapedImages] =  {
    val websiteFut: Future[WebsiteContext] = request.website.map(ScraperServiceModelConversions.fromProto)
      .map(Future.successful)
      .getOrElse(Future.failed(
        Status.INVALID_ARGUMENT
          .augmentDescription("Missing website parameter in get scraped images request")
          .asRuntimeException()
      ))

    websiteFut.flatMap(website => {
      Scraper.getScrapedImages(website).map({
        case Left(error) => throw Status.INTERNAL
          .augmentDescription(error.toString)
          .asRuntimeException()
        case Right(images) => ScrapedImages(images.map(_.toString))
      })
    })
  }
}


class ScraperServer(implicit ec: ExecutionContext) {
  self =>
  private var server: Option[Server] = None

  private def start(): Unit = {
    server = Some(ServerBuilder.forPort(ScraperServer.port)
      .addService(ScraperGrpc.bindService(new ScraperImpl, ec))
      .build
      .start)
    println(s"Server started, listening on ${ScraperServer.port}")
    sys.addShutdownHook {
      println(s"Server shutting down on JVM stop")
      self.stop()
      println(s"Server shutdown complete")
    }
  }

  private def blockUntilShutdown(): Unit = {
    server.foreach(_.awaitTermination())
  }

  private def stop(): Unit = {
    server.foreach(_.shutdown())
    server = None
  }

}


object ScraperServer {

  val port = 8080

  def main(args: Array[String]): Unit = {
    val server = new ScraperServer()(ExecutionContext.global)
    server.start()
    server.blockUntilShutdown()
  }

}
