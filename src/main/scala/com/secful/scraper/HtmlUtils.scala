package com.secful.scraper

import akka.util.ByteString
import org.jsoup.Jsoup

import java.net.URL
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.Try

object HtmlUtils {

  def parseImages(page: String): Either[String, Seq[HtmlImageElement]] = {
    Try({
      val elements = Jsoup.parse(page)
        .select("img")
      elements.eachAttr("src")
        .asScala
        .toSeq
        .distinct
        .map(url => HtmlImageElement(new URL(url)))
    }).toEither.left.map(e => s"Invalid HTML page: ${e.getMessage}")
  }

}

trait HtmlElement

case class HtmlImageElement(url: URL) extends HtmlElement

case class Image(fileName: String, contents: ByteString)