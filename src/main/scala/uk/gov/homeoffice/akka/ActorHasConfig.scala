package uk.gov.homeoffice.akka

import akka.actor.Actor
import uk.gov.homeoffice.HasConfig

trait ActorHasConfig extends HasConfig {
  this: Actor =>

  override val config = context.system.settings.config
}