package com.secful.scraper

import akka.util.ByteString
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

import java.net.URL
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.Try

object HtmlUtils {

  def parseImages(page: String): Either[String, Seq[HtmlImageElement]] = {
    val parser = Parser.htmlParser
      .setTrackErrors(1)

    Try({
      val dom = Jsoup.parse(page, parser)

      if(!parser.getErrors.isEmpty){
        throw new Exception(parser.getErrors.get(0).getErrorMessage)
      }
      val elements = dom
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