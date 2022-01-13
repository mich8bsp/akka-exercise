package com.secful.scraper

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.secful.scraper.ImageDownloadActor.DownloadImagesRequest
import com.secful.scraper.Scraper.WebsiteContext

import java.net.URL
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

class ImageDownloader {
  def downloadImages(urls: Seq[URL], website: WebsiteContext)
                    (implicit ec: ExecutionContext,
                     actorSystem: ActorSystem): Future[Seq[Either[ImageDownloadError, Image]]] = {
    Source(urls)
      .mapAsync(10)(url => HttpUtils.fetchResource(url)
        .map(bf => Image(FileUtils.getFileNameFromUrl(url), bf))
        .map(Right(_))
        .recover({
          case e: Throwable => Left(ImageDownloadError(website, url, e.getMessage))
        })
      )
      .toMat(Sink.seq)(Keep.right)
      .run()
  }
}



class ImageDownloadActor extends Actor {
  implicit val as = context.system
  private val threadPool = Executors.newFixedThreadPool(10)
  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(threadPool)
  private val imageDownloader = new ImageDownloader

  override def postStop(): Unit = {
    threadPool.shutdown()
  }

  override def receive: Receive = {
    case DownloadImagesRequest(website, urls) =>
      val capturedSender = sender()

      val downloadedImages: Future[Seq[Either[ImageDownloadError, Image]]] = imageDownloader.downloadImages(urls, website)

      downloadedImages.onComplete({
        case Success(res) => capturedSender ! res
        case Failure(e) =>  println(s"Unhandled error while downloading images ${e.getMessage}")
          capturedSender ! Left(ImagesDownloadError(website, urls, "Unexpected error"))
      })
  }
}

object ImageDownloadActor {
  def props: Props = Props(new ImageDownloadActor)

  case class DownloadImagesRequest(website: WebsiteContext, urls: Seq[URL])
}