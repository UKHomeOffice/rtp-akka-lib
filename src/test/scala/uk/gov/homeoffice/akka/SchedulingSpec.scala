package uk.gov.homeoffice.akka

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.{Actor, Props}
import akka.{Schedule, Scheduled, Scheduling}
import org.specs2.mutable.Specification

class SchedulingSpec extends Specification {
  trait Context extends ActorSystemContext {
    system.eventStream.subscribe(self, classOf[Scheduled])
  }

  "Actor" should {
    "tell itself to do something more than once" in new Context {
      val actor = system actorOf Props {
        new Actor with Scheduling[Unit] {
          val schedule = Schedule()
          val scheduled = {}
        }
      }

      expectMsg(Scheduled(actor.path))
      expectMsg(Scheduled(actor.path))
    }

    "tell itself to do something more than once, waiting for future results before rescheduling is kicked off" in new Context {
      val actor = system actorOf Props {
        new Actor with Scheduling[Any] {
          var futureScheduled = false

          val schedule = Schedule()

          def scheduled = if (futureScheduled) {} else Future {
            futureScheduled = true
            TimeUnit.SECONDS.sleep(3)
          }
        }
      }

      expectMsg(Scheduled(actor.path))
      expectNoMsg(2 seconds)
      expectMsg(Scheduled(actor.path))
    }

    "tell itself to do something only once" in new Context {
      val actor = system actorOf Props {
        new Actor with Scheduling[Unit] {
          override val schedule = Schedule(scheduleAfterSuccess = false)

          val scheduled = {}
        }
      }

      expectMsg(Scheduled(actor.path))
      expectNoMsg()
    }

    "tell itself to do something only once, but only after a non-default delay" in new Context {
      val actor = system actorOf Props {
        new Actor with Scheduling[Unit] {
          override val schedule = Schedule(initialDelay = 3 seconds, scheduleAfterSuccess = false)

          val scheduled = {}
        }
      }

      expectNoMsg(2 seconds)
      expectMsg(Scheduled(actor.path))
      expectNoMsg()
    }
  }
}