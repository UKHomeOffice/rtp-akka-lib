package uk.gov.homeoffice.spray

import spray.httpx.Json4sSupport
import spray.routing._
import uk.gov.homeoffice.json.JsonFormats

/**
 * Mix this trait into your "Routing" to define endpoints.
 * Note that Marshallers provides some implicit functionality to handle responses to clients calling endpoints.
 * Even though custom code can be created to handle the responses, the implicits can automatically handle JsonError responses, which is a good default for Routings that deal mainly with JSON.
 */
trait Routing extends Directives with Marshallers with JsonFormats with Json4sSupport {
  def route: Route
}

object Routing {
  implicit class RoutingOps(routing: Routing) {
    def ~(routings: Seq[Routing]) = routing +: routings

    def ~(anotherRouting: Routing) = Seq(routing, anotherRouting)
  }

  implicit class SeqRoutingOps(routings: Seq[Routing]) {
    def ~(otherRoutings: List[Routing]) = routings ++ otherRoutings

    def ~(anotherRouting: Routing) = routings :+ anotherRouting
  }
}