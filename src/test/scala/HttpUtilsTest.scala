import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.secful.scraper.HttpUtils
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.matchers.should.Matchers

import java.net.URL

class HttpUtilsTest() extends TestKit(ActorSystem("HttpUtilsTest"))
  with AsyncFlatSpecLike
  with BeforeAndAfterAll
{

  behavior of "fetch resource"

  it should "fetch html resource" in {
    HttpUtils.fetchResource(new URL("https://salt.security")).map { bs =>
      assert(bs.utf8String.startsWith("<!DOCTYPE html>"))
    }
  }

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }
}
