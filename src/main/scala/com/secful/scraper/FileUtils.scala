package com.secful.scraper

import java.io.FileOutputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.{Files, Path}

object FileUtils {

  def getFileNameFromUrl(url: URL): String = {
    if (url.toString.endsWith("/")) {
      throw new Exception(s"Could not get file name from a directory url ${url}")
    } else {
      url.toString.split("/").last
    }
  }

  def writeFile(path: Path,
                content: ByteBuffer,
                overrideIfExists: Boolean = false): Unit = {
    if(overrideIfExists){
      Files.deleteIfExists(path)
    }
    val fileChannel = new FileOutputStream(path.toFile).getChannel
    try{
      fileChannel.write(content)
    }finally {
      fileChannel.close()
    }
  }
}
