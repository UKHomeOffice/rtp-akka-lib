package uk.gov.homeoffice.spray

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory._
import org.json4s.JsonAST.{JObject, JString}
import org.json4s.jackson.JsonMethods._
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.http.{HttpEntity, HttpResponse}
import spray.httpx.Json4sSupport
import spray.routing._
import uk.gov.homeoffice.configuration.HasConfig
import uk.gov.homeoffice.json.JsonFormats

/**
  * Example of booting a Spray microservice
  */
object ExampleBoot extends App with SprayBoot with ExampleConfig {
  implicit lazy val sprayActorSystem = ActorSystem("example-boot-actor-system")

  bootRoutings(ExampleRouting1 ~ ExampleRouting2 ~ ExampleRoutingError)(FailureHandling.exceptionHandler)
}

/**
  * For your microservice, this configuration should be declared in a Typesafe configuration file such as application.conf
  */
trait ExampleConfig {
  this: HasConfig =>

  override implicit val config = load(parseString("""
    spray.can.server {
      name = "example-spray-can"
      host = "0.0.0.0"
      port = 9100
      request-timeout = 1s
      service = "example-http-routing-service"
      remote-address-header = on
    }"""))
}

/**
  * Routing example 1
  * curl http://localhost:9100/example1
  */
object ExampleRouting1 extends ExampleRouting1

trait ExampleRouting1 extends Routing {
  val route =
    pathPrefix("example1") {
      pathEndOrSingleSlash {
        get {
          complete { JObject("status" -> JString("Congratulations 1")) }
        }
      }
    }
}

/**
  * Routing example 2
  * curl http://localhost:9100/example2
  */
object ExampleRouting2 extends ExampleRouting2

trait ExampleRouting2 extends Routing {
  val route =
    pathPrefix("example2") {
      pathEndOrSingleSlash {
        get {
          complete { JObject("status" -> JString("Congratulations 2")) }
        }
      }
    }
}

/**
  * Routing example to see failure handling
  */
object ExampleRoutingError extends ExampleRoutingError

trait ExampleRoutingError extends Routing {
  val route =
    pathPrefix("example-error") {
      pathEndOrSingleSlash {
        get {
          complete { throw new TestException("This sounds daft, but your error was a success!") }
        }
      }
    }
}

/**
  * Example of specific handing of failures
  */
object FailureHandling extends Directives with JsonFormats with Json4sSupport {
  val exceptionHandler = ExceptionHandler {
    case e: TestException => complete {
      HttpResponse(status = InternalServerError, entity = HttpEntity(`application/json`, pretty(render(JObject("test" -> JString(e.getMessage))))))
    }
  }
}

class TestException(s: String) extends Exception(s)