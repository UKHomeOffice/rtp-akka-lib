package akka

import akka.actor.{Actor, ActorLogging, Cancellable}

case object Scheduled

case object NotScheduled

case object Wakeup

trait Scheduler extends ActorLogging {
  this: Actor =>

  private var cancellable: Cancellable = _

  def schedule: Cancellable

  override def preStart(): Unit = cancellable = schedule

  override def postStop(): Unit = if (cancellable != null) cancellable.cancel()

  override protected[akka] def aroundReceive(receive: Actor.Receive, msg: Any): Unit = msg match {
    case Scheduled =>
      log.info(s"${sender()} asked if I am scheduled!")
      sender() ! (if (cancellable == null) NotScheduled else if (cancellable.isCancelled) NotScheduled else Scheduled)

    case _ =>
      receive.applyOrElse(msg, unhandled)
  }
}

trait NoSchedule {
  this: Scheduler =>

  override val schedule: Cancellable = new Cancellable {
    def isCancelled = true

    def cancel() = true
  }
}