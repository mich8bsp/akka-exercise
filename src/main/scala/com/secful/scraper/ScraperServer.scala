package com.secful.scraper

import akka.actor.typed.ActorSystem
import com.secful.scraper.Scraper.WebsiteContext

import java.net.URL
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ScraperServer {

  def main(args: Array[String]): Unit = {
//    implicit val actorSystem: ActorSystem = ActorSystem("scraper-server-system")
    import scala.concurrent.ExecutionContext.Implicits.global


    Scraper.scrapeWebsite(WebsiteContext(
      name = "salt",
      url = new URL("https://salt.security")
    )) onComplete {
      case Success(res) => res match {
        case Left(error) => println(s"Failed to download files ${error}")
        case Right(files) => println(s"Successfully downloaded ${files.size} files: ${files.mkString("\n")}")
      }
      case Failure(e) => println(s"Failed to download files ${e.getMessage}")
    }
  }

}
