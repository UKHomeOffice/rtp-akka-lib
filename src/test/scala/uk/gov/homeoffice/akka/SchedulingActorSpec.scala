package uk.gov.homeoffice.akka

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.Props
import akka.{Schedule, Scheduled, SchedulingActor}
import org.specs2.mutable.Specification

class SchedulingActorSpec extends Specification {
  "Actor" should {
    "tell itself to do something more than once" in new ActorSystemContext {
      val actor = system actorOf Props {
        new SchedulingActor[Unit] {
          val schedule = Schedule()
          val scheduled = {}
        }
      }

      system.eventStream.subscribe(self, classOf[Scheduled])

      expectMsg(Scheduled(actor.path))
      expectMsg(Scheduled(actor.path))
    }

    "tell itself to do something more than once, waiting for future results before rescheduling is kicked off" in new ActorSystemContext {
      val actor = system actorOf Props {
        new SchedulingActor[Any] {
          var futureScheduled = false

          val schedule = Schedule()

          def scheduled = if (futureScheduled) {} else Future {
            futureScheduled = true
            TimeUnit.SECONDS.sleep(3)
          }
        }
      }

      system.eventStream.subscribe(self, classOf[Scheduled])

      expectMsg(Scheduled(actor.path))
      expectNoMsg(2 seconds)
      expectMsg(Scheduled(actor.path))
    }

    "tell itself to do something only once" in new ActorSystemContext {
      val actor = system actorOf Props {
        new SchedulingActor[Unit] {
          override val schedule = Schedule(scheduleAfterSuccess = false)

          val scheduled = {}
        }
      }

      system.eventStream.subscribe(self, classOf[Scheduled])

      expectMsg(Scheduled(actor.path))
      expectNoMsg()
    }

    "tell itself to do something only once, but only after a non-default delay" in new ActorSystemContext {
      val actor = system actorOf Props {
        new SchedulingActor[Unit] {
          override val schedule = Schedule(initialDelay = 3 seconds, scheduleAfterSuccess = false)

          val scheduled = {}
        }
      }

      system.eventStream.subscribe(self, classOf[Scheduled])

      expectNoMsg(2 seconds)
      expectMsg(Scheduled(actor.path))
      expectNoMsg()
    }
  }
}