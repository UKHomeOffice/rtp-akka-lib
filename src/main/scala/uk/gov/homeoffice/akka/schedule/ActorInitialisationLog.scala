package uk.gov.homeoffice.akka.schedule

import akka.actor.{Actor, ActorLogging}
import akka.serialization.Serialization._

trait ActorInitialisationLog {
  this: Actor with ActorLogging =>

  log.info(s"Actor configured for ${serializedActorPath(self)}")
}