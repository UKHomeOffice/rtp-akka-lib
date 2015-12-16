package akka

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor._
import uk.gov.homeoffice.configuration.ConfigFactorySupport

trait Scheduler extends ActorLogging with ConfigFactorySupport {
  this: Actor =>

  private var cancellable: Cancellable = _

  val schedule: Cancellable

  def schedule(initialDelay: Duration = 0 seconds, interval: Duration, receiver: ActorRef = self, message: Any = Wakeup) =
    context.system.scheduler.schedule(initialDelay, interval, receiver, message)

  override def preStart(): Unit = cancellable = schedule

  override def postStop(): Unit = if (cancellable != null) cancellable.cancel()

  override protected[akka] def aroundReceive(receive: Actor.Receive, msg: Any): Unit = msg match {
    case Scheduled | Scheduled(_) =>
      log.info(s"${sender()} asked if I am scheduled!")
      sender() ! (if (cancellable == null) NotScheduled else if (cancellable.isCancelled) NotScheduled else Scheduled)

    case _ =>
      receive.applyOrElse(msg, unhandled)
  }
}

case object Wakeup

case object Scheduled

case class Scheduled(actorPath: ActorPath)

case object NotScheduled