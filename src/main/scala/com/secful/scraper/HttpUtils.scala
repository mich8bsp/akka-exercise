package com.secful.scraper

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.scaladsl.Sink
import akka.util.ByteString

import java.net.URL
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

object HttpUtils {

  def fetchResource(url: URL)
                   (implicit ec: ExecutionContext,
                    actorSystem: ActorSystem): Future[ByteString] = {
    for {
      httpResponse <- Http().singleRequest(HttpRequest(uri = url.toString))
      dataBytes <- httpResponse.entity.dataBytes
        .runWith(Sink.seq)
    }yield {
      dataBytes.foldLeft(ByteString.empty)(_ ++ _)
    }
  }

  def main(args: Array[String]): Unit = {
    implicit val as = ActorSystem("test")
    implicit val ec = as.dispatcher
    val res = fetchResource(new URL("https://salt.security"))

    val fetched = ((Await.result(res, 5.seconds)).utf8String)
    println(HtmlUtils.parseImages(fetched))
  }
}
