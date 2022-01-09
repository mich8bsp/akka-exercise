import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import akka.util.ByteString
import com.secful.scraper.{HtmlImageElement, HtmlParsingActor, HtmlParsingError, Image, ImageDownloadActor}
import com.secful.scraper.HtmlParsingActor.{ParseWebsiteImages, WebsiteImageElements}
import com.secful.scraper.ImageDownloadActor.DownloadImagesRequest
import com.secful.scraper.Scraper.WebsiteContext
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.net.URL
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

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "Html parsing actor" should {

    "parse valid html for images" in {
      within(5.seconds) {
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
      within(5.seconds) {

        val request = DownloadImagesRequest(
          WebsiteContext(name = "test", url = new URL("https://www.w3.org/Graphics/PNG/Inline-img.html")),
          Seq(new URL("https://www.w3.org/Graphics/PNG/nurbcup2si.png"),
            new URL("https://www.w3.org/Graphics/PNG/666.png"),
            new URL("https://www.w3.org/Graphics/PNG/invalid.png")
          )
        )

        imageDownloadActor ! request
//        expectMsg(Seq(Right(Image("nurbcup2si.png", ByteString.empty))))
      }
    }

  }


}
