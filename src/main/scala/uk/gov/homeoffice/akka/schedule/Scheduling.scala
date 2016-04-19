package uk.gov.homeoffice.akka.schedule

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging}
import uk.gov.homeoffice.akka.schedule.Protocol.{Scheduled, Wakeup}

trait Scheduling[R] extends ActorLogging with ActorInitialisationLog {
  this: Actor =>

  val schedule: Schedule

  def scheduled: R

  override def preStart(): Unit = doSchedule(schedule.initialDelay)

  override final def receive: Receive = {
    case Wakeup =>
      log.info("Woken up")
      val client = sender()
      client ! scheduled

    case Schedule =>
      log.debug("Triggered schedule")
      context.system.eventStream.publish(Scheduled(self.path))

      val client = sender()

      try {
        scheduled match {
          case result: Future[_] if schedule.awaitOnFutures && schedule.scheduleAfterSuccess =>
            result onComplete { r =>
              client ! r
              doSchedule(schedule.delay)
            }

          case result if schedule.scheduleAfterSuccess =>
            client ! result
            doSchedule(schedule.delay) // TODO Scheduled result of say Seq[Future], then await each Future if configured

          case result =>
            client ! result
        }
      } catch {
        case t: Throwable if schedule.scheduleAfterError =>
          log.error(s"Scheduling caused an exception which is being IGNORED and so this actor will not bubble up the error to its supervisor: $t")
          client ! t
          doSchedule(schedule.delay)

        case t: Throwable =>
          log.error(s"Exception in scheduling")
          client ! t
          throw t
      }
  }

  private def doSchedule(delay: FiniteDuration) = context.system.scheduler.scheduleOnce(delay, self, Schedule)
}

case class Schedule(initialDelay: FiniteDuration = 0 seconds,
                    delay: FiniteDuration = 0 seconds,
                    scheduleAfterSuccess: Boolean = true,
                    scheduleAfterError: Boolean = false,
                    awaitOnFutures: Boolean = true)