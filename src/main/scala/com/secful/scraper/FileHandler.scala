package com.secful.scraper

import akka.actor.{Actor, Props}
import com.secful.scraper.FileSystemActor.StoreImagesRequest
import com.secful.scraper.Scraper.{GetScrapedImagesRequest, WebsiteContext}

import java.nio.file.{Files, Path}
import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

class FileHandler {

  def writeImages(images: Seq[Image], website: WebsiteContext)
                 (implicit ec: ExecutionContext): Future[Seq[Either[ImageFileWritingError, Path]]] = {
    import scala.jdk.CollectionConverters._

    val outputDir: Path = Path.of(s"/tmp/${website.name}")
    Files.createDirectories(outputDir, PosixFilePermissions.asFileAttribute(PosixFilePermission.values().toSet.asJava))

    Future.sequence(images.map(image => {
      val imageWriteTask = Future {
        val imagePath = Path.of(s"${outputDir.toString}/${image.fileName}")
        FileUtils.writeFile(imagePath,
          image.contents.asByteBuffer,
          overrideIfExists = true)
        imagePath
      }
      imageWriteTask.map(Right(_)).recover({
        case e: Throwable => Left(ImageFileWritingError(website, image, e.getMessage))
      })
    }))
  }

  def listImages(website: WebsiteContext)
                (implicit ec: ExecutionContext): Future[Either[ImageFilesReadingError, Seq[Path]]] = Future {
    FileUtils.listFilesInDir(Path.of(s"/tmp/${website.name}"))
  }.map(Right(_))
    .recover({
      case e: Throwable => Left(ImageFilesReadingError(website, e.getMessage))
    })
}


class FileSystemActor extends Actor {
  private val threadPool = Executors.newFixedThreadPool(5)
  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(threadPool)
  private val fileHandler = new FileHandler

  override def postStop(): Unit = {
    threadPool.shutdown()
  }


  override def receive: Receive = {
    case StoreImagesRequest(website, images) =>

      val capturedSender = sender()

      val writeResultFuture: Future[Seq[Either[ImageFileWritingError, Path]]] = fileHandler.writeImages(images, website)
      writeResultFuture.onComplete({
        case Success(res) => capturedSender ! res
        case Failure(e) =>
          println(s"Unexpected error when writing image files to FS: ${e.getMessage}")
          capturedSender ! Left(ImageFilesWritingError(website, images, "Unexpected error"))
      })

    case GetScrapedImagesRequest(website) =>
      val capturedSender = sender()
      val imagesFuture: Future[Either[ScraperError, Seq[Path]]] = fileHandler.listImages(website)

        imagesFuture.onComplete({
          case Success(res) => capturedSender ! res
          case Failure(e) =>
            println(s"Unexpected error while fetching scraped image files from FS: ${e.getMessage}")
            capturedSender ! Left(ImageFilesReadingError(website = website, reason = "Unexpected error"))
        })
  }
}

object FileSystemActor {
  def props: Props = Props(new FileSystemActor)

  case class StoreImagesRequest(website: WebsiteContext, images: Seq[Image])
}