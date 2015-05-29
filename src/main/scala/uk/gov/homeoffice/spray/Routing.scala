package uk.gov.homeoffice.spray

import spray.httpx.Json4sSupport
import spray.routing._
import uk.gov.homeoffice.json.JsonFormats

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