package uk.gov.homeoffice.spray

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler, Route, RouteConcatenation}
import akka.http.scaladsl.settings.RoutingSettings
import akka.util.Timeout
import grizzled.slf4j.Logging
import uk.gov.homeoffice.configuration.{ConfigFactorySupport, HasConfig}

import scala.concurrent.duration._

trait AkkaHttpBoot[T] extends RouteConcatenation with HasConfig with ConfigFactorySupport with Logging {

  implicit def actorSystem: ActorSystem[T]

  implicit val executionContext = actorSystem.executionContext

  implicit def routing2Seq(r: Routing): Seq[Routing] = Seq(r)

  def bootRoutings(routings: Seq[Routing])(implicit exceptionHandler: ExceptionHandler = ExceptionHandler.default(RoutingSettings(actorSystem)),
                                           rejectionHandler: RejectionHandler = RejectionHandler.default): Unit = {
    require(routings.nonEmpty, "No routes declared")
    info(s"""Booting ${routings.size} ${if (routings.size > 1) "routes" else "route"}""")

    val routes = routings.tail.foldLeft(routings.head.route) { (route, routing) => route ~ routing.route }

    bootHttpService(routes)
  }

  def bootHttpService(routes: Route)(implicit actorSystem: ActorSystem[T]): Unit = {
    Http().newServerAt(config.text("spray.can.server.host", "0.0.0.0"), config.int("spray.can.server.port", 9100)).bind(routes)
    sys addShutdownHook {
      implicit val timeout: Timeout = Timeout(30 seconds)
    }
  }
}