package com.secful.scraper

import com.secful.scraper.Scraper.WebsiteContext
import com.secful.scraper.ScraperServiceModelConversions.toProto
import com.secful.scraperservice.scraper_service.ScraperGrpc.ScraperStub
import com.secful.scraperservice.scraper_service.{GetScrapedRequest, ScrapeRequest, ScraperGrpc}
import io.grpc.{ManagedChannel, ManagedChannelBuilder, StatusRuntimeException}

import java.net.URL
import java.nio.file.Path
import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

object ScraperClient {

  def apply(host: String, port: Int, useTls: Boolean = true): ScraperClient = {
    val channel: ManagedChannel = {
      val builder = ManagedChannelBuilder.forAddress(host, port)
      if (useTls) {
        builder.useTransportSecurity().build
      } else {
        builder.usePlaintext().build
      }
    }
    val stub = ScraperGrpc.stub(channel)
    new ScraperClient(channel, stub)
  }

  def main(args: Array[String]): Unit = {
    val client = ScraperClient("localhost", 8080, useTls = false)
    val scrapedRes = client.scrapeImages(WebsiteContext("salt", new URL("https://salt.security")))

    println(Await.result(scrapedRes, 30.seconds))
  }
}

class ScraperClient private(
                             private val channel: ManagedChannel,
                             private val stub: ScraperStub
                           ) {
  private val threadpool = Executors.newFixedThreadPool(2)
  private implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(threadpool)

  def shutdown(): Unit = {
    channel.shutdown().awaitTermination(10, TimeUnit.SECONDS)
    threadpool.shutdown()
  }

  def scrapeImages(website: WebsiteContext): Future[Seq[Path]] = {
    val resFuture: Future[Seq[Path]] = stub.scrape(ScrapeRequest(Some(toProto(website))))
      .map(_.imagePaths.map(Path.of(_)))

    resFuture.onComplete({
      case Success(v) => println(s"Successfully scraped ${v.size} images from website ${website.name} at ${website.url}")
      case Failure(e) => e match {
        case rtEx: StatusRuntimeException =>
          println(s"Failed scraping website ${website.name} at ${website.url} for images, code: ${rtEx.getStatus.getCode}, reason: ${rtEx.getStatus.getDescription}")
      }
    })

    resFuture
  }

  def getScrapedImages(website: WebsiteContext): Future[Seq[Path]] = {
    val resFuture: Future[Seq[Path]] = stub.getScraped(GetScrapedRequest(Some(toProto(website))))
      .map(_.imagePaths.map(Path.of(_)))

    resFuture.onComplete({
      case Success(v) => println(s"Successfully fetched ${v.size} scraped images from website ${website.name} at ${website.url}")
      case Failure(e) => e match {
        case rtEx: StatusRuntimeException =>
          println(s"Failed fetching scraped images from website ${website.name} at ${website.url}, code: ${rtEx.getStatus.getCode}, reason: ${rtEx.getStatus.getDescription}")
      }
    })

    resFuture
  }

}
