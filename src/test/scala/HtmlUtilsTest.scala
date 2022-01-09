import com.secful.scraper.{HtmlImageElement, HtmlUtils}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.URL

class HtmlUtilsTest extends AnyFlatSpec with BeforeAndAfterAll with Matchers{

  private val validHtml =
    s"""
       |<html>
       |  <body>
       |    <div>
       |      <img src="https://salt-security.com/testy.png" alt="sdfs">
       |    </div>
       |  </body>
       |</html>
       |""".stripMargin

  private val validHtmlRelative =
    s"""
       |<html>
       |  <body>
       |    <div>
       |      <img src="testy.png" alt="sdfs">
       |    </div>
       |  </body>
       |</html>
       |""".stripMargin

  private val validHtmlNoImages =
    s"""
       |<html>
       |</html>
       |""".stripMargin

  private val invalidHtml =   s"""
                                 |<html>
                                 |  <body>
                                 |    <div>
                                 |      <img src="https://salt-security.com/tes
                                 |""".stripMargin

  it should "parse images from valid html" in {
    HtmlUtils.parseImages(validHtml) shouldBe Right(List[HtmlImageElement](
      HtmlImageElement(new URL("https://salt-security.com/testy.png"))
    ))
  }

  it should "parse images from valid html with source" in {
    HtmlUtils.parseImages(validHtmlRelative, Some(new URL("https://salt-security.com/index.html"))) shouldBe Right(List[HtmlImageElement](
      HtmlImageElement(new URL("https://salt-security.com/testy.png"))
    ))
  }

  it should "parse empty list from valid html without images" in {
    HtmlUtils.parseImages(validHtmlNoImages) shouldBe Right(Nil)
  }

  it should "fail parsing invalid html" in {
    HtmlUtils.parseImages(invalidHtml).left.map(_.split(":").head) shouldBe Left("Invalid HTML page")
  }

}
