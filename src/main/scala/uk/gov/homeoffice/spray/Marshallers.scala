package uk.gov.homeoffice.spray

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.http.{HttpEntity, HttpResponse}
import spray.httpx.marshalling.{ToResponseMarshallingContext, ToResponseMarshaller}
import org.json4s.JValue
import org.json4s.native.Serialization.write
import org.scalactic.{Bad, Good, Or}
import grizzled.slf4j.Logging
import uk.gov.homeoffice.json.{JsonError, JsonFormats}
import org.json4s.native.JsonMethods._

/**
 * Implicit responses for JsonError are of type <anything> Or JsonError i.e. if not using custom response handling code and expecting the implicit functionality of this trait to be used, a response must match one of the declared marshallers here.
 * So the "orMarshaller" can handle either a Good(<anything>) or a Bad(JsonError).
 */
trait Marshallers extends JsonFormats with Logging {
  val  marshall: ToResponseMarshallingContext => PartialFunction[_ Or JsonError, Unit] = ctx => {
    case Good(j: JValue) =>
      ctx.marshalTo(HttpResponse(status = OK, entity = HttpEntity(`application/json`, compact(render(j)))))

    case Good(g: AnyRef) =>
      ctx.marshalTo(HttpResponse(status = OK, entity = HttpEntity(`application/json`, write(g))))

    case Good(g) =>
      ctx.marshalTo(HttpResponse(status = OK, entity = HttpEntity(`text/plain`, g.toString)))

    case Bad(jsonError) =>
      ctx.marshalTo(HttpResponse(status = UnprocessableEntity, entity = HttpEntity(`application/json`, write(jsonError))))
  }

  implicit val orMarshaller = ToResponseMarshaller.of[_ Or JsonError](`application/json`) { (value, contentType, ctx) =>
    marshall(ctx)(value)
  }

  implicit val futureOrMarshaller = ToResponseMarshaller.of[Future[_ Or JsonError]](`application/json`) { (value, contentType, ctx) =>
    value.onComplete {
      case Success(v) =>
        marshall(ctx)(v)

      case Failure(e) =>
        error(e)
        ctx.handleError(e)
    }
  }
}