import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import akka.util.ByteString
import com.secful.scraper.FileSystemActor.StoreImagesRequest
import com.secful.scraper.HtmlParsingActor.{ParseWebsiteImages, WebsiteImageElements}
import com.secful.scraper.ImageDownloadActor.DownloadImagesRequest
import com.secful.scraper.Scraper.WebsiteContext
import com.secful.scraper._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.net.URL
import java.nio.file.Path
import scala.concurrent.duration.DurationInt

class ScraperTest extends TestKit(ActorSystem("scraper-test"))
  with AnyWordSpecLike
  with DefaultTimeout
  with ImplicitSender
  with BeforeAndAfterAll
  with Matchers {

  val exampleWebsiteContext: WebsiteContext = WebsiteContext(name = "test", url = new URL("https://www.w3.org/Graphics/PNG/Inline-img.html"))

  val htmlParsingActor: ActorRef = system.actorOf(HtmlParsingActor.props)
  val imageDownloadActor: ActorRef = system.actorOf(ImageDownloadActor.props)
  val fileSystemActor: ActorRef = system.actorOf(FileSystemActor.props)

  val testImageOutputPath = Path.of("/tmp/test/testy.png")

  override def beforeAll(): Unit = {
    if(testImageOutputPath.toFile.exists()){
      testImageOutputPath.toFile.delete()
    }
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "Html parsing actor" should {

    "parse valid html for images" in {
      within(10.seconds) {
        htmlParsingActor ! ParseWebsiteImages(exampleWebsiteContext)
        expectMsg(Right(WebsiteImageElements(Seq(
          HtmlImageElement(new URL("https://www.w3.org/Graphics/PNG/nurbcup2si.png")),
          HtmlImageElement(new URL("https://www.w3.org/Graphics/PNG/666.png")),
        ))))
      }
    }

    "parse invalid html should get an error" in {
      within(5.seconds) {
        htmlParsingActor ! ParseWebsiteImages(WebsiteContext(name = "test", url = new URL("https://invalidurl")))
        expectMsgType[Left[HtmlParsingError, WebsiteImageElements]]
      }
    }
  }

  "Image download actor" should {
    "download all valid images" in {
      within(10.seconds) {
        val invalidImageUrl = new URL("https://Graphics/PNG/invalid.png")
        val request = DownloadImagesRequest(
          WebsiteContext(name = "test", url = new URL("https://www.w3.org/Graphics/PNG/Inline-img.html")),
          Seq(new URL("https://www.w3.org/Graphics/PNG/nurbcup2si.png"),
            new URL("https://www.w3.org/Graphics/PNG/666.png"),
            invalidImageUrl
          )
        )

        imageDownloadActor ! request
        expectMsgPF() {
          case images: Seq[Either[ImageDownloadError, Image]] =>
            images.size shouldBe 3
            images.flatMap(_.toOption).map(_.fileName) shouldBe Seq("nurbcup2si.png", "666.png")
            images.flatMap(_.toOption).map(_.contents).foreach(_.utf8String.nonEmpty shouldBe true)
            images.flatMap(_.left.toOption).map(_.imageUrl) shouldBe Seq(invalidImageUrl)
            images.flatMap(_.left.toOption).map(_.reason).foreach(_.toLowerCase.contains("unknownhost") shouldBe true)
        }
      }
    }

    "not fail for empty list of images to download" in {
      within(5.seconds){
        val request = DownloadImagesRequest(
          WebsiteContext(name = "test", url = new URL("https://www.w3.org/Graphics/PNG/Inline-img.html")),
          Seq()
        )

        imageDownloadActor ! request
        expectMsg(Seq())
      }
    }

  }

  "File System Actor" should {
    "store images to disk" in {
      within(5.seconds) {
        val imageFileName = "testy.png"
        val request = StoreImagesRequest(
          website = exampleWebsiteContext,
          images = Seq(Image(imageFileName, ByteString("dummy image")))
        )

        fileSystemActor ! request
        expectMsg(Seq(Right(testImageOutputPath)))
        val writtenFile = testImageOutputPath.toFile
        writtenFile.exists() shouldBe true
        FileUtils.readFileAsText(testImageOutputPath) shouldBe "dummy image"
      }
    }
  }


}
