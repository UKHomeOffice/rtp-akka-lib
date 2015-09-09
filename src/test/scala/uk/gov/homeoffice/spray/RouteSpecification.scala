package uk.gov.homeoffice.spray

import akka.actor.ActorSystem
import spray.httpx.Json4sSupport
import spray.routing.HttpService
import spray.testkit.{RouteTest, Specs2RouteTest}
import org.specs2.mutable.Specification
import uk.gov.homeoffice.json.JsonFormats
import concurrent.duration._
import akka.testkit._

trait RouteSpecification extends Specification with Specs2RouteTest with HttpService with JsonFormats with Json4sSupport {
  this: RouteTest =>

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds dilated)

  val actorRefFactory = system
}