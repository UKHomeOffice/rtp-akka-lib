package akka

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try
import akka.actor.Actor
import grizzled.slf4j.Logging

trait Scheduling[R] extends Logging {
  this: Actor =>

  val schedule = Schedule()

  def scheduled: R

  override def preStart(): Unit = doSchedule()

  override def receive: Receive = {
    case Wakeup =>
      trace("Woken up")
      context.system.eventStream.publish(Scheduled(self.path))

      Try {
        scheduled match {
          case r: Future[_] if schedule.awaitOnFutures && schedule.scheduleAfterSuccess => r onComplete { _ => doSchedule() }
          case _ if schedule.scheduleAfterSuccess => doSchedule() // TODO Scheduled result of say Seq[Future], then await each Future if configured
        }
      } recover {
        case t: Throwable if schedule.scheduleAfterError =>
          error(s"Scheduling caused an exception which is being IGNORED and so this actor will not bubble up the error to its supervisor: $t")
          doSchedule()
      }
  }

  private def doSchedule() = context.system.scheduler.scheduleOnce(schedule.delay, self, Wakeup)
}

case class Schedule(delay: FiniteDuration = 0 seconds, scheduleAfterSuccess: Boolean = true, scheduleAfterError: Boolean = false, awaitOnFutures: Boolean = true)