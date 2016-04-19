package uk.gov.homeoffice.akka.schedule

import akka.actor.ActorPath

object Protocol {
  case object Schedule

  case object IsScheduled

  case class Scheduled(actorPath: ActorPath)

  case object NotScheduled

  case object Wakeup
}