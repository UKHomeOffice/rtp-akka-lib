package uk.gov.homeoffice.spray


import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.duration.Duration

class ExampleRoutingSpec extends Specification with RouteSpecification {

  trait Context extends Scope with ExampleRouting

  def UnmarshalJsonResponse()(implicit
                              stringResponseMarshaller: akka.http.scaladsl.unmarshalling.FromResponseUnmarshaller[String],
                              stringClassTag: scala.reflect.ClassTag[JValue]
  ): JValue = {
    implicit val timeout = Duration("1 second")
    JsonMethods.parse(responseAs)
  }

  "Example routing" should {
    "indicate when a required route is not recognised" in new Context {
      Get("/example/non-existing") ~> route ~> check {
        status must throwAn[Exception]
      }
    }

    "be available" in new Context {
      Get("/example") ~> route ~> check {
        status mustEqual StatusCodes.OK
        contentType.mediaType mustEqual ContentTypes.`application/json`.mediaType
        (UnmarshalJsonResponse() \ "status").extract[String] mustEqual "Congratulations"
      }
    }
  }
}