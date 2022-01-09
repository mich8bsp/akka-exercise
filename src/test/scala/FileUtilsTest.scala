import com.secful.scraper.FileUtils
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.net.URL

class FileUtilsTest extends AnyFlatSpec with BeforeAndAfterAll with Matchers{

  it should "extract file name from url" in {
    FileUtils.getFileNameFromUrl(new URL("https://something/a.jpg")) shouldBe "a.jpg"
    an[Exception] should be thrownBy FileUtils.getFileNameFromUrl(new URL("https://something/something/"))
  }

}
