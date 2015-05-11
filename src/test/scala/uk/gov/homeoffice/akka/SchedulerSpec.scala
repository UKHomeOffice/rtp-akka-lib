package uk.gov.homeoffice.akka

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.{Wakeup, NotScheduled, Scheduled, Scheduler}
import akka.actor.{Actor, Cancellable, Props}
import akka.event.LoggingReceive
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

class SchedulerSpec extends Specification with NoTimeConversions {
  "Actor" should {
    "be scheduled to act as a poller" in new ActorSystemContext {
      val exampleSchedulerActor = system.actorOf(Props(new ExampleSchedulerActor), "exampleSchedulerActor")
      exampleSchedulerActor ! Scheduled
      expectMsg(Scheduled)
    }

    "not be scheduled to act as a poller" in new ActorSystemContext {
      val exampleSchedulerActor = system.actorOf(Props(new ExampleNotSchedulerActor), "exampleSchedulerActor")
      exampleSchedulerActor ! Scheduled
      expectMsg(NotScheduled)
    }
  }
}

class ExampleSchedulerActor extends Actor with Scheduler {
  def schedule: Option[Cancellable] = Some(context.system.scheduler.schedule(initialDelay = 1.seconds, interval = 5.seconds, receiver = self, message = Wakeup))

  def receive = LoggingReceive {
    case Wakeup => println("Hello World!")
  }
}

class ExampleNotSchedulerActor extends Actor with Scheduler {
  def schedule: Option[Cancellable] = None

  def receive = LoggingReceive {
    case Wakeup => println("Hello World!")
  }
}