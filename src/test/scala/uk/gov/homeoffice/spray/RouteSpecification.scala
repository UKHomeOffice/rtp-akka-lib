package uk.gov.homeoffice.spray

import spray.httpx.Json4sSupport
import spray.routing.HttpService
import spray.testkit.{RouteTest, Specs2RouteTest}
import org.specs2.mutable.Specification
import uk.gov.homeoffice.json.JsonFormats

trait RouteSpecification extends Specification with Specs2RouteTest with HttpService with JsonFormats with Json4sSupport {
  this: RouteTest =>

  val actorRefFactory = system
}