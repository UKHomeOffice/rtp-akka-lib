package uk.gov.homeoffice.akka

import akka.actor.Actor
import uk.gov.homeoffice.configuration.HasConfig

trait ActorHasConfig extends HasConfig {
  this: Actor =>

  override implicit val config = context.system.settings.config
}