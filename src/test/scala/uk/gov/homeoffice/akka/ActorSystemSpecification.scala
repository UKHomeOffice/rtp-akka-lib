package uk.gov.homeoffice.akka

import java.util.UUID
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKitBase}
import org.specs2.execute.Result
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AfterAll
import com.typesafe.config.{Config, ConfigFactory}

trait ActorSystemSpecification extends TestKitBase with ImplicitSender with AfterAll {
  this: SpecificationLike =>

  sequential

  implicit lazy val config: Config = ConfigFactory.load

  implicit lazy final val system: ActorSystem = ActorSystem(UUID.randomUUID().toString, config)

  implicit def any2Success[R](r: R): Result = success

  def afterAll() = system.terminate()
}