package uk.gov.homeoffice.akka

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKitBase}
import com.typesafe.config.{Config, ConfigFactory}
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AroundEach
import grizzled.slf4j.Logging

trait ActorSystemSpecification extends AroundEach with TestKitBase with ImplicitSender with Logging {
  this: SpecificationLike =>

  isolated
  sequential

  implicit lazy val config: Config = ConfigFactory.load

  implicit lazy val system: ActorSystem = ActorSystem(UUID.randomUUID().toString, config)

  implicit def any2Success[R](r: R): Result = success

  override def around[R: AsResult](r: => R): Result = try {
    AsResult(r)
  } finally {
    info(s"Shutting down actor system $system")
    Await.ready(system.terminate(), 2 seconds)
  }
}