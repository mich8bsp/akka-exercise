package com.secful.scraper

import com.secful.scraper.Scraper.WebsiteContext

import java.net.URL

sealed trait ScraperError{
  val website: WebsiteContext
  val reason: String
}
case class HtmlParsingError(website: WebsiteContext, reason: String) extends ScraperError {
  override def toString: String = s"Failed to parse website ${website.name} at ${website.url}: $reason"
}
case class ImageDownloadError(website: WebsiteContext, imageUrl: URL, reason: String) extends ScraperError {
  override def toString: String = s"Failed to download image ${imageUrl} from website ${website.name} at ${website.url}: $reason"
}
case class ImagesDownloadError(website: WebsiteContext, imageUrls: Seq[URL], reason: String) extends ScraperError {
  override def toString: String = s"Failed to download ${imageUrls.size} images from website ${website.name} at ${website.url}: $reason"
}
object ImagesDownloadError{
  def combine(errors: Seq[ScraperError]): ImagesDownloadError = {
    require(errors.nonEmpty)
    val imageUrls = errors.flatMap({
      case ImageDownloadError(_, url, _) => Some(url)
      case _ => None
    })
    ImagesDownloadError(errors.head.website, imageUrls, errors.map(_.reason).mkString(" ; "))
  }
}

case class FileWritingError(website: WebsiteContext, image: Image, reason: String) extends ScraperError {
  override def toString: String = s"Failed to write image ${image.fileName} for website ${website.name}: $reason"
}

case class FilesWritingError(website: WebsiteContext, images: Seq[Image], reason: String) extends ScraperError {
  override def toString: String = s"Failed to write ${images.size} images downloaded from ${website.name}: $reason"
}

object FilesWritingError{
  def combine(errors: Seq[ScraperError]): FilesWritingError = {
    require(errors.nonEmpty)
    val images = errors.flatMap({
      case FileWritingError(_, image, _) => Some(image)
      case _ => None
    })
    FilesWritingError(errors.head.website, images, errors.map(_.reason).mkString(" ; "))
  }
}