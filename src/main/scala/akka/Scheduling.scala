package akka

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try
import akka.actor.{ActorLogging, Actor}

trait Scheduling[R] extends ActorLogging {
  this: Actor =>

  def schedule: Schedule

  def scheduled: R

  override def preStart(): Unit = doSchedule(schedule.initialDelay)

  override final def receive: Receive = {
    case Wakeup =>
      log.debug("Woken up")
      context.system.eventStream.publish(Scheduled(self.path))

      Try {
        scheduled match {
          case r: Future[_] if schedule.awaitOnFutures && schedule.scheduleAfterSuccess => r onComplete { _ => doSchedule(schedule.delay) }
          case _ if schedule.scheduleAfterSuccess => doSchedule(schedule.delay) // TODO Scheduled result of say Seq[Future], then await each Future if configured
        }
      } recover {
        case t: Throwable if schedule.scheduleAfterError =>
          log.error(s"Scheduling caused an exception which is being IGNORED and so this actor will not bubble up the error to its supervisor: $t")
          doSchedule(schedule.delay)
      }
  }

  private def doSchedule(delay: FiniteDuration) = context.system.scheduler.scheduleOnce(delay, self, Wakeup)
}

case class Schedule(initialDelay: FiniteDuration = 0 seconds,
                    delay: FiniteDuration = 0 seconds,
                    scheduleAfterSuccess: Boolean = true,
                    scheduleAfterError: Boolean = false,
                    awaitOnFutures: Boolean = true)