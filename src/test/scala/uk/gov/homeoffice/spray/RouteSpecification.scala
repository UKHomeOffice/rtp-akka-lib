package uk.gov.homeoffice.spray

import akka.actor.ActorSystem
import akka.http.scaladsl.testkit.{RouteTest, RouteTestTimeout, Specs2RouteTest}
import akka.testkit._
import org.specs2.mutable.SpecificationLike
import uk.gov.homeoffice.json.JsonFormats

import scala.concurrent.duration._

trait RouteSpecification extends Specs2RouteTest with JsonFormats  {
  this: SpecificationLike with RouteTest =>

  val actorSystem = system

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds dilated)
}