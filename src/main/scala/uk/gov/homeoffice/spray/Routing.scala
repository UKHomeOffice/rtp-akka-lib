package uk.gov.homeoffice.spray

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{Directives, Route}
import spray.json.DefaultJsonProtocol
import uk.gov.homeoffice.json.JsonFormats

/**
  * Mix this trait into your "Routing" to define endpoints.
  * Note that Marshallers provides some implicit functionality to handle responses to clients calling endpoints.
  * Even though custom code can be created to handle the responses, the implicits can automatically handle JsonError responses, which is a good default for Routings that deal mainly with JSON.
  * See Marshallers ScalaDoc for more information.
  */

trait Routing extends Directives with Marshallers with JsonFormats with SprayJsonSupport with DefaultJsonProtocol {
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