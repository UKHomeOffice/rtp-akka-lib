package uk.gov.homeoffice.spray

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, ExceptionHandler}
import com.typesafe.config.ConfigFactory
import org.json4s.JsonAST.{JObject, JString}
import org.json4s.jackson.JsonMethods._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import uk.gov.homeoffice.configuration.HasConfig
import uk.gov.homeoffice.json.JsonFormats

class AkkaHttpBootSpec extends Specification {

  trait Context extends Scope with AkkaHttpBoot[Done] with App with LocalConfig {
    implicit lazy val actorSystem = ActorSystem(Behaviors.empty,"spray-boot-spec-spray-can")
  }

  "Booting Spray" should {
    "be successful" in new Context {
      bootRoutings(Seq(ExampleRouting))
      ok
    }

    "fail because of not providing any routes" in new Context {
      bootRoutings(Seq.empty[Routing]) must throwAn[IllegalArgumentException](message = "requirement failed: No routes declared")
    }

    "allow the HttpRouting to be configured with its own failure handling" in new Context {

      object FailureHandling extends Directives with JsonFormats {
        val exceptionHandler = ExceptionHandler {
          case e: TestException => complete {
            HttpResponse(status = StatusCodes.InternalServerError, entity = HttpEntity(ContentTypes.`application/json`, pretty(render(JObject("test" -> JString(e.getMessage))))))
          }
        }
      }

      class TestException(s: String) extends Exception(s)

      bootRoutings(Seq(ExampleRouting))(FailureHandling.exceptionHandler)
      ok
    }
  }
}

trait LocalConfig extends HasConfig {
  override val config = ConfigFactory.load(ConfigFactory.parseString(
    """
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