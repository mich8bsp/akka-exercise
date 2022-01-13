import com.secful.scraper.{HtmlImageElement, HtmlUtils}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.net.URL
import scala.util.Success

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

  private val source = new URL("https://salt-security.com/index.html")

  it should "parse images from valid html" in {
    HtmlUtils.parseImages(validHtml, source) shouldBe Success(List[HtmlImageElement](
      HtmlImageElement(new URL("https://salt-security.com/testy.png"))
    ))
  }

  it should "parse images from valid html with source" in {
    HtmlUtils.parseImages(validHtmlRelative, source) shouldBe Success(List[HtmlImageElement](
      HtmlImageElement(new URL("https://salt-security.com/testy.png"))
    ))
  }

  it should "parse empty list from valid html without images" in {
    HtmlUtils.parseImages(validHtmlNoImages, source) shouldBe Success(Nil)
  }

  it should "fail parsing invalid html" in {
    HtmlUtils.parseImages(invalidHtml, source).isFailure shouldBe true
  }

}
