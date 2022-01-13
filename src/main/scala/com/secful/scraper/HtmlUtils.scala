package com.secful.scraper

import akka.util.ByteString
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

import java.net.URL
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.Try
import cats.implicits._

object HtmlUtils {


  def parseImages(page: String, source: URL): Try[Seq[HtmlImageElement]] = {
    val parser = Parser.htmlParser
      .setTrackErrors(1)

    Try {
      val dom = Jsoup.parse(page, parser)

      parser.getErrors.asScala.toList.toNel match {
        case None =>
          val elements = dom.select("img")
          elements.eachAttr("src")
            .asScala
            .toSeq
            .distinct
            .map(url => HtmlImageElement(pathToAbsoluteUrl(url, source)))
        case Some(errors) =>
          throw new Exception(errors.head.getErrorMessage)
      }
    }
  }

  private def pathToAbsoluteUrl(path: String, source: URL): URL = path match {
    case x if x.startsWith("http") => new URL(path)
    case x if x.startsWith("www") => new URL(s"https://$path")
    case _ => new URL(s"${getSourceDirPath(source)}$path")
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