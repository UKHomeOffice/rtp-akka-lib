package uk.gov.homeoffice.akka

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka._
import akka.actor.{Actor, Cancellable, Props}
import akka.event.LoggingReceive
import org.specs2.mutable.Specification

class SchedulerSpec extends Specification {
  "Actor" should {
    "be scheduled to act as a poller" in new ActorSystemContext {
      val exampleSchedulerActor = system.actorOf(Props(new ExampleSchedulerActor), "exampleSchedulerActor")
      exampleSchedulerActor ! Scheduled
      expectMsg(Scheduled)
    }

    "not be scheduled to act as a poller" in new ActorSystemContext {
      val exampleSchedulerActor = system.actorOf(Props(new ExampleSchedulerActor with NoSchedule), "exampleNoSchedulerActor")
      exampleSchedulerActor ! Scheduled
      expectMsg(NotScheduled)
    }
  }
}

class ExampleSchedulerActor extends Actor with Scheduler {
  val schedule: Cancellable = context.system.scheduler.schedule(initialDelay = 1 second, interval = 5 seconds, receiver = self, message = Wakeup)

  def receive = LoggingReceive {
    case Wakeup => println("Hello World!")
  }
}