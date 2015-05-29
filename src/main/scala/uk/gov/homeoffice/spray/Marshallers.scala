package uk.gov.homeoffice.spray

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.http.{HttpEntity, HttpResponse}
import spray.httpx.marshalling.ToResponseMarshaller
import org.json4s.JValue
import org.json4s.JsonAST.{JObject, JString}
import org.json4s.native.JsonMethods._
import org.scalactic.{Bad, Good, Or}
import uk.gov.homeoffice.json.JsonError

trait Marshallers {
  implicit val orMarshaller = ToResponseMarshaller.of[JValue Or JsonError](`application/json`) { (value, contentType, ctx) =>
    value match {
      case Good(_) =>
        ctx.marshalTo(HttpResponse(status = OK))

      case Bad(jsonError) =>
        ctx.marshalTo(HttpResponse(status = UnprocessableEntity, entity = HttpEntity(`application/json`, pretty(render(JObject("status" -> JString(jsonError.error)))))))
    }
  }

  implicit def futureOrMarshaller[G, B]: ToResponseMarshaller[Future[JValue Or JsonError]] = ToResponseMarshaller.of[Future[JValue Or JsonError]](`application/json`) { (value, contentType, ctx) =>
    value.onComplete {
      case Success(v) => v match {
        case Good(_) =>
          ctx.marshalTo(HttpResponse(status = OK))

        case Bad(jsonError) =>
          ctx.marshalTo(HttpResponse(status = UnprocessableEntity, entity = HttpEntity(`application/json`, pretty(render(JObject("status" -> JString(jsonError.error)))))))
      }

      case Failure(error) =>
        println(error)
        ctx.handleError(error)
    }
  }
}