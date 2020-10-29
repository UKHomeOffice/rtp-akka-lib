package uk.gov.homeoffice.spray

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import org.json4s.JsonAST.{JObject, JString}
import org.json4s.jackson.JsonMethods.{compact, render}

object ExampleRouting extends ExampleRouting

trait ExampleRouting extends Routing {

  val route =
    pathPrefix("example") {
      pathEndOrSingleSlash {
        get {
          complete {
            HttpResponse(status = StatusCodes.OK, entity = HttpEntity(ContentTypes.`application/json`, compact(render(JObject("status" -> JString("Congratulations"))))))
          }
        }
      } ~
        post {
          path("submission") {
            complete("todo")
          }
        }
    }
}