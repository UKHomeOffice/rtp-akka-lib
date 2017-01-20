package uk.gov.homeoffice.spray

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.pickling.Defaults._
import scala.pickling.json._
import scala.util.{Failure, Success, Try}
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.http.{HttpEntity, HttpResponse}
import spray.httpx.marshalling.{ToResponseMarshaller, ToResponseMarshallingContext}
import org.json4s.JValue
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.write
import org.scalactic.{Bad, Good, Or}
import grizzled.slf4j.Logging
import uk.gov.homeoffice.json.{JsonError, JsonFormats}

/**
 * Implicit responses for JsonError are of type <anything> Or JsonError i.e. if not using custom response handling code and expecting the implicit functionality of this trait to be used, a response must match one of the declared marshallers here.
 * So the "orMarshaller" can handle either a Good(<anything>) or a Bad(JsonError).
 */
trait Marshallers extends JsonFormats with Logging {
  val marshallOr: ToResponseMarshallingContext => PartialFunction[_ Or JsonError, Any] = ctx => {
    val marshalGood: PartialFunction[Good[_], Any] = {
      case Good(j: JValue) =>
        ctx.marshalTo(HttpResponse(status = OK, entity = HttpEntity(`application/json`, compact(render(j)))))

      case Good(a: AnyRef) =>
        val response = Try {
          write(a)
        } getOrElse a.pickle.toString

        ctx.marshalTo(HttpResponse(status = OK, entity = HttpEntity(`application/json`, response)))

      case Good(a) =>
        ctx.marshalTo(HttpResponse(status = OK, entity = HttpEntity(`text/plain`, a.toString)))
    }

    val goodFuture: PartialFunction[_ Or JsonError, Any] = {
      case Good(f: Future[_]) => f.onComplete {
        case Success(v) => marshalGood(Good(v))

        case Failure(t) =>
          ctx.marshalTo(HttpResponse(status = UnprocessableEntity, entity = HttpEntity(`application/json`, write(JsonError(error = Some(t.getMessage), throwable = Some(t))))))
      }
    }

    val good: PartialFunction[_ Or JsonError, Any] = {
      case g @ Good(_) =>
        marshalGood(g)
    }

    val bad: PartialFunction[_ Or JsonError, Any] = {
      case Bad(jsonError) =>
        ctx.marshalTo(HttpResponse(status = UnprocessableEntity, entity = HttpEntity(`application/json`, write(jsonError))))
    }

    goodFuture orElse good orElse bad
  }

  implicit val orMarshaller = ToResponseMarshaller.of[_ Or JsonError](`application/json`) { (value, contentType, ctx) =>
    marshallOr(ctx)(value)
  }

  implicit val futureOrMarshaller = ToResponseMarshaller.of[Future[_ Or JsonError]](`application/json`) { (value, contentType, ctx) =>
    value.onComplete {
      case Success(v) =>
        marshallOr(ctx)(v)

      case Failure(e) =>
        error(e)
        ctx.handleError(e)
    }
  }
}