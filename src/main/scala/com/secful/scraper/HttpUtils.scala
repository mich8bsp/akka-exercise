package com.secful.scraper

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.scaladsl.Sink
import akka.util.ByteString

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

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
}
