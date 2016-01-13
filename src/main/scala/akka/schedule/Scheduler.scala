package akka.schedule

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.actor._
import akka.schedule.Protocol._
import akka.serialization.Serialization.serializedActorPath
import uk.gov.homeoffice.configuration.ConfigFactorySupport

trait Scheduler extends ActorLogging with ConfigFactorySupport {
  this: Actor =>

  log.info(s"Scheduling configured for ${serializedActorPath(self)}")

  private var cancellable: Cancellable = _

  val schedule: Cancellable

  def schedule(initialDelay: Duration = 0 seconds, interval: Duration, receiver: ActorRef = self, message: Any = Schedule) =
    context.system.scheduler.schedule(initialDelay, interval, receiver, message)

  override def preStart(): Unit = cancellable = schedule

  override def postStop(): Unit = if (cancellable != null) cancellable.cancel()

  override protected[akka] def aroundReceive(receive: Actor.Receive, msg: Any): Unit = msg match {
    case IsScheduled =>
      log.info(s"${sender()} asked if I am scheduled!")
      sender() ! (if (cancellable == null) NotScheduled else if (cancellable.isCancelled) NotScheduled else Scheduled(self.path))

    case _ =>
      receive.applyOrElse(msg, unhandled)
  }
}