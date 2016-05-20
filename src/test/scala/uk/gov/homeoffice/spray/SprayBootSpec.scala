package uk.gov.homeoffice.spray

import akka.actor.{ActorRef, ActorSystem}
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.http.{HttpEntity, HttpResponse}
import spray.httpx.Json4sSupport
import spray.routing._
import org.json4s.JsonAST.{JObject, JString}
import org.json4s.jackson.JsonMethods._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import com.typesafe.config.ConfigFactory
import uk.gov.homeoffice.configuration.HasConfig
import uk.gov.homeoffice.json.JsonFormats

class SprayBootSpec extends Specification {
  trait Context extends Scope with SprayBoot with App with LocalConfig {
    implicit lazy val sprayActorSystem = ActorSystem("spray-boot-spec-spray-can")

    override def bootHttpService(routeHttpService: ActorRef) = {}
  }

  "Booting Spray" should {
    "be successful" in new Context {
      bootRoutings(ExampleRouting)
      ok
    }

    "fail because of not providing any routes" in new Context {
      bootRoutings(Seq.empty[Routing]) must throwAn[IllegalArgumentException](message = "requirement failed: No routes declared")
    }

    "allow the HttpRouting to be configured with its own failure handling" in new Context {
      object FailureHandling extends Directives with JsonFormats with Json4sSupport {
        val exceptionHandler = ExceptionHandler {
          case e: TestException => complete {
            HttpResponse(status = InternalServerError, entity = HttpEntity(`application/json`, pretty(render(JObject("test" -> JString(e.getMessage))))))
          }
        }
      }

      class TestException(s: String) extends Exception(s)

      bootRoutings(ExampleRouting)(FailureHandling.exceptionHandler)
      ok
    }
  }
}

trait LocalConfig extends HasConfig {
  override val config = ConfigFactory.load(ConfigFactory.parseString("""
    spray.can.server {
      name = "spray-it-spray-can"
      host = "0.0.0.0"
      port = 9100
      request-timeout = 1s
      service = "http-routing-service"
      remote-address-header = on
    }
  """))
}