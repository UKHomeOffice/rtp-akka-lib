package uk.gov.homeoffice.spray

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.{ActorRef, ActorSystem, Props, Terminated}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import org.json4s.JValue
import org.json4s.jackson.JsonMethods._
import grizzled.slf4j.Logging
import spray.can.Http
import spray.can.server.Stats
import spray.http.HttpMethods.GET
import spray.http.MediaTypes._
import spray.http.StatusCodes.OK
import spray.http.{HttpEntity, HttpRequest, HttpResponse, Uri}
import spray.routing._
import uk.gov.homeoffice.configuration.{ConfigFactorySupport, HasConfig}
import uk.gov.homeoffice.duration._

/**
  * Boot your application with your required routings e.g.
  *
  * object ExampleBoot extends App with SprayBoot {
  *   implicit lazy val spraySystem = ActorSystem("name-of-provided-actor-system-for-spray")
  *
  *   bootRoutings(ExampleRouting1 ~ ExampleRouting2)
  * }
  *
  * "bootRoutings" defaults to using Spray defaults for the likes of failure handling.
  * In order to add customisations, provide "bootRoutings" a seconds argument list for required exception and/or rejection handling.
  * Note that the method bootHttpService actually boots the services of the routings and this can be switched off for testing by overridding and doing nothing.
  */
trait SprayBoot extends HttpService with RouteConcatenation with HasConfig with ConfigFactorySupport with Logging {
  this: App =>

  implicit lazy val actorRefFactory = {
    sys addShutdownHook spraySystemShutdownHook
    spraySystem
  }

  def spraySystem: ActorSystem

  implicit def routing2Seq(r: Routing): Seq[Routing] = Seq(r)

  def spraySystemShutdownHook: Future[Terminated] = actorRefFactory.terminate()

  def bootRoutings(routings: Seq[Routing])(implicit exceptionHandler: ExceptionHandler = ExceptionHandler.default,
                                                    rejectionHandler: RejectionHandler = RejectionHandler.Default): Unit = {
    require(routings.nonEmpty, "No routes declared")
    info(s"""Booting ${routings.size} ${if (routings.size > 1) "routes" else "route"}""")

    val routes = routings.tail.foldLeft(routings.head.route) { (route, routing) => route ~ routing.route }

    val routeHttpService = actorRefFactory.actorOf(HttpRouting.props(routes)(exceptionHandler, rejectionHandler),
                                                   config.text("spray.can.server.service", "http-routing-service"))

    bootHttpService(routeHttpService)
  }

  def bootHttpService(routeHttpService: ActorRef): Unit = {
    IO(Http) ! Http.Bind(listener = routeHttpService,
                         interface = config.text("spray.can.server.host", "0.0.0.0"),
                         port = config.int("spray.can.server.port", 9100))

    sys addShutdownHook {
      implicit val timeout: Timeout = Timeout(30 seconds)

      IO(Http) ? Http.CloseAll
    }
  }

  private object HttpRouting {
    def props(route: Route)(implicit eh: ExceptionHandler, rh: RejectionHandler) = Props(new HttpRouting(route)(eh, rh))
  }

  private class HttpRouting(route: Route)(implicit eh: ExceptionHandler, rh: RejectionHandler) extends HttpServiceActor {
    implicit val timeout: Timeout = Timeout(30 seconds)

    def receive: Receive = statisticsReceive orElse routesReceive

    def statisticsReceive: Receive = {
      case HttpRequest(GET, Uri.Path("/service-statistics"), _, _, _) =>
        val client = sender()

        context.actorSelection("/user/IO-HTTP/listener-0") ? Http.GetStats onSuccess {
          case s: Stats =>
            val json: JValue = parse(s"""{
              "statistics": {
                "uptime": "${s.uptime.toPrettyString}",
                "total-requests": "${s.totalRequests}",
                "open-requests": "${s.openRequests}",
                "maximum-open-requests": "${s.maxOpenRequests}",
                "total-connections": "${s.totalConnections}",
                "open-connections": "${s.openConnections}",
                "max-open-connections": "${s.maxOpenConnections}",
                "request-timeouts": "${s.requestTimeouts}"
              }
            }""")

            client ! HttpResponse(OK, HttpEntity(`application/json`, pretty(render(json))))
        }
    }

    def routesReceive: Receive = runRoute(route)
  }
}