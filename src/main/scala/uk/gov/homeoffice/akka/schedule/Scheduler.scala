package uk.gov.homeoffice.akka.schedule

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.actor._
import uk.gov.homeoffice.configuration.ConfigFactorySupport

trait Scheduler extends ActorLogging with ActorInitialisationLog with ConfigFactorySupport {
  this: Actor =>

  private var cancellable: Cancellable = _

  val schedule: Cancellable

  def schedule(initialDelay: Duration = 0 seconds, interval: Duration, receiver: ActorRef = self, message: Any = Protocol.Schedule) =
    context.system.scheduler.schedule(initialDelay, interval, receiver, message)

  override def preStart(): Unit = cancellable = schedule

  override def postStop(): Unit = if (cancellable != null) cancellable.cancel()

  override def aroundReceive(receive: Actor.Receive, msg: Any): Unit = msg match {
    case Protocol.IsScheduled =>
      log.info(s"${sender()} asked if I am scheduled!")
      sender() ! (if (cancellable == null) Protocol.NotScheduled else if (cancellable.isCancelled) Protocol.NotScheduled else Protocol.Scheduled(self.path))

    case _ =>
      receive.applyOrElse(msg, unhandled)
  }
}