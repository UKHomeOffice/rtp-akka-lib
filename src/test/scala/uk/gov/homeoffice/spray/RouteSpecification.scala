package uk.gov.homeoffice.spray

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.testkit._
import spray.httpx.Json4sSupport
import spray.routing.HttpService
import spray.testkit.{RouteTest, Specs2RouteTest}
import org.specs2.mutable.SpecificationLike
import uk.gov.homeoffice.json.JsonFormats

trait RouteSpecification extends Specs2RouteTest with HttpService with JsonFormats with Json4sSupport {
  this: SpecificationLike with RouteTest =>

  val actorRefFactory = system

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds dilated)
}