package uk.gov.homeoffice.spray

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import grizzled.slf4j.Logging
import org.json4s.Extraction.decompose
import org.json4s.JValue
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.write
import org.scalactic.{Bad, Good, Or}
import uk.gov.homeoffice.json.{JsonError, JsonFormats}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * Implicit responses for JsonError are of type <anything> Or JsonError i.e. if not using custom response handling code and expecting the implicit functionality of this trait to be used, a response must match one of the declared marshallers here.
  * So the "orMarshaller" can handle either a Good(<anything>) or a Bad(JsonError).
  */
trait Marshallers extends JsonFormats with Logging {
  val marshallOr: PartialFunction[_ Or JsonError, Any] = {
    val marshalGood: PartialFunction[Good[_], Any] = {
      case Good(j: JValue) =>
        HttpResponse(status = StatusCodes.OK, entity = HttpEntity(ContentTypes.`application/json`, compact(render(j))))

      case Good(a: AnyRef) =>
        val response = Try {
          write(a)
        } getOrElse decompose(a).toString

        HttpResponse(status = StatusCodes.OK, entity = HttpEntity(ContentTypes.`application/json`, response))

      case Good(a) =>
        HttpResponse(status = StatusCodes.OK, entity = HttpEntity(ContentTypes `text/plain(UTF-8)`, a.toString))
    }

    val goodFuture: PartialFunction[_ Or JsonError, Any] = {
      case Good(f: Future[_]) => f.onComplete {
        case Success(v) => marshalGood(Good(v))

        case Failure(t) =>
          HttpResponse(status = StatusCodes.UnprocessableEntity, entity = HttpEntity(ContentTypes.`application/json`, write(JsonError(error = Some(t.getMessage), throwable = Some(t)))))
      }
    }

    val good: PartialFunction[_ Or JsonError, Any] = {
      case g@Good(_) =>
        marshalGood(g)
    }

    val bad: PartialFunction[_ Or JsonError, Any] = {
      case Bad(jsonError) =>
        HttpResponse(status = StatusCodes.UnprocessableEntity, entity = HttpEntity(ContentTypes.`application/json`, write(jsonError)))
    }

    goodFuture orElse good orElse bad
  }

  implicit val orMarshaller = Marshaller.withFixedContentType[_ Or JsonError, Any](ContentTypes.`application/json`) { obj =>
    marshallOr(obj)

  }

  implicit val futureOrMarshaller = Marshaller.withFixedContentType[Future[_ Or JsonError], Any](ContentTypes.`application/json`) { value =>
    value.onComplete {
      case Success(v) =>
        marshallOr(v)

      case Failure(e) =>
        error(e)
        HttpResponse(status = StatusCodes.UnprocessableEntity, entity = HttpEntity(ContentTypes.`application/json`, e.getMessage))
    }
  }
}