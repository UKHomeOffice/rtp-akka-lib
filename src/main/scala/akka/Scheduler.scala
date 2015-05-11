package akka

import akka.actor.{Actor, ActorLogging, Cancellable}

case object Scheduled

case object NotScheduled

case object Wakeup

trait Scheduler extends ActorLogging {
  this: Actor =>
  
  private var cancellable: Option[Cancellable] = _

  def schedule: Option[Cancellable]

  override def preStart(): Unit = cancellable = schedule

  override def postStop(): Unit = cancellable.foreach { _.cancel() }

  override protected[akka] def aroundReceive(receive: Actor.Receive, msg: Any): Unit = msg match {
    case Scheduled =>
      log.info(s"${sender()} asked if I am scheduled!")
      sender() ! cancellable.map(_ => Scheduled).getOrElse(NotScheduled)

    case _ =>
      receive.applyOrElse(msg, unhandled)
  }
}