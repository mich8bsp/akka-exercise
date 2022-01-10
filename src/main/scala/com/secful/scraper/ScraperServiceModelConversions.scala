package com.secful.scraper

import com.secful.scraper.Scraper.WebsiteContext
import com.secful.scraperservice.scraper_service.Website

import java.net.URL

object ScraperServiceModelConversions {
  def fromProto(w: Website): WebsiteContext = WebsiteContext(
    name = w.name,
    url = new URL(w.url)
  )

  def toProto(w: WebsiteContext): Website = Website(
    name = w.name,
    url = w.url.toString
  )
}
