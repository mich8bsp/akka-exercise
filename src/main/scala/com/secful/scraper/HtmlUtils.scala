package com.secful.scraper

import akka.util.ByteString
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

import java.net.URL
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.Try

object HtmlUtils {

  def parseImages(page: String, source: Option[URL] = None): Either[String, Seq[HtmlImageElement]] = {
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
        .map(url => {
          val absoluteUrl: URL = if (url.startsWith("http")) {
            new URL(url)
          } else if (url.startsWith("www")){
            new URL(s"https://$url")
          }else {
            new URL(s"${source.map(getSourceDirPath).map(_.toString).getOrElse("")}$url")
          }
          HtmlImageElement(absoluteUrl)
        })
    }).toEither.left.map(e => s"Invalid HTML page: ${e.getMessage}")
  }

  private [HtmlUtils] def getSourceDirPath(source: URL): URL = {
    if(source.toString.contains("/")){
      new URL(source.toString.substring(0, source.toString.lastIndexOf("/") + 1))
    }else{
      source
    }
  }
}

trait HtmlElement

case class HtmlImageElement(url: URL) extends HtmlElement

case class Image(fileName: String, contents: ByteString)