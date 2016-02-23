package uk.gov.homeoffice.spray

import spray.http.MediaTypes._
import spray.http.StatusCodes._
import org.json4s._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class ExampleRoutingSpec extends Specification with RouteSpecification {
  trait Context extends Scope with ExampleRouting

  "Example routing" should {
    "indicate when a required route is not recognised" in new Context {
      Get("/example/non-existing") ~> route ~> check {
        status must throwAn[Exception]
      }
    }

    "be available" in new Context {
      Get("/example") ~> route ~> check {
        status mustEqual OK
        contentType.mediaType mustEqual `application/json`
        (responseAs[JValue] \ "status").extract[String] mustEqual "Congratulations"
      }
    }
  }
}