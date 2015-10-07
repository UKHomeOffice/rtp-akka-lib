package uk.gov.homeoffice.spray

import scala.util.Try
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import spray.can.Http
import spray.routing._
import grizzled.slf4j.Logging
import uk.gov.homeoffice.configuration.HasConfig

/**
 * Boot your application with your required routings e.g.
 *
 * object ExampleBoot extends App with SprayBoot {
 *   bootRoutings(ExampleRouting1 ~ ExampleRouting2)
 * }
 *
 * "bootRoutings" defaults to using Spray defaults for the likes of failure handling.
 * In order to add customisations, provide "bootRoutings" a seconds argument list for required exception and/or rejection handling.
 */
trait SprayBoot extends HttpService with RouteConcatenation with HasConfig with Logging {
  this: App =>

  implicit lazy val actorRefFactory = ActorSystem(Try { config.getString("spray.can.server.name") } getOrElse "spray-can")

  sys.addShutdownHook {
    actorRefFactory.shutdown()
  }

  implicit def routingToSeq(r: Routing): Seq[Routing] = Seq(r)

  def bootRoutings(routings: Seq[Routing])(implicit exceptionHandler: ExceptionHandler = ExceptionHandler.default,
                                                    rejectionHandler: RejectionHandler = RejectionHandler.Default): Unit = {
    require(routings.nonEmpty, "No routes declared")
    info(s"""Booting ${routings.size} ${if (routings.size > 1) "routes" else "route"}""")

    val routes = routings.tail.foldLeft(routings.head.route) { (route, routing) => route ~ routing.route }

    val routeHttpService = actorRefFactory.actorOf(Props(new HttpRouting(routes)(exceptionHandler, rejectionHandler)),
                                                   Try { config.getString("spray.can.server.service") } getOrElse "http-routing-service")

    bootHttpService(routeHttpService)
  }

  private[spray] def bootHttpService(routeHttpService: ActorRef): Unit = {
    IO(Http) ! Http.Bind(listener = routeHttpService,
                         interface = Try { config.getString("spray.can.server.host") } getOrElse "0.0.0.0",
                         port = Try { config.getInt("spray.can.server.port") } getOrElse 9100)

    sys.addShutdownHook {
      IO(Http) ? Http.CloseAll
    }
  }

  private class HttpRouting(route: Route)(implicit eh: ExceptionHandler, rh: RejectionHandler) extends HttpServiceActor {
    def receive: Receive = runRoute(route)
  }
}