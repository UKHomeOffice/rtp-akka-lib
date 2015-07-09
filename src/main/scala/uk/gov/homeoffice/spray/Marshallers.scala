package uk.gov.homeoffice.spray

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.http.{HttpEntity, HttpResponse}
import spray.httpx.marshalling.ToResponseMarshaller
import org.json4s.native.Serialization.write
import org.scalactic.{Bad, Good, Or}
import grizzled.slf4j.Logging
import uk.gov.homeoffice.json.{JsonError, JsonFormats}

trait Marshallers extends JsonFormats with Logging {
  implicit val orMarshaller = ToResponseMarshaller.of[_ Or JsonError](`application/json`) { (value, contentType, ctx) =>
    value match {
      case Good(_) =>
        ctx.marshalTo(HttpResponse(status = OK))

      case Bad(jsonError) =>
        ctx.marshalTo(HttpResponse(status = UnprocessableEntity, entity = HttpEntity(`application/json`, write(jsonError.copy(error = jsonError.error.replaceAll("\"", "'"))))))
    }
  }

  implicit val futureOrMarshaller = ToResponseMarshaller.of[Future[_ Or JsonError]](`application/json`) { (value, contentType, ctx) =>
    value.onComplete {
      case Success(v) => v match {
        case Good(_) =>
          ctx.marshalTo(HttpResponse(status = OK))

        case Bad(jsonError) =>
          ctx.marshalTo(HttpResponse(status = UnprocessableEntity, entity = HttpEntity(`application/json`, write(jsonError.copy(error = jsonError.error.replaceAll("\"", "'"))))))
      }

      case Failure(e) =>
        error(e)
        ctx.handleError(e)
    }
  }
}