package uk.gov.homeoffice.akka.schedule

import akka.actor.Cancellable

/**
 * Why provide a "no schedule" such that a scheduled Actor will not periodically "wake up" and perform its duties?
 * One way that this trait can be very useful, is when testing a scheduled Actor's functionality - essentially its API (which is only exposed via a message protocol).
 * When testing a scheduled actor's functionality, messages sent to it by a specification can be processed and assertions made on the outcome of said processing.
 * During a test, you would usually not want the actor to also be "woken up" by the scheduling mechanism, as this would probably interfere with the running test.
 */
trait NoSchedule {
  this: Scheduler =>

  override lazy val schedule: Cancellable = new Cancellable {
    def isCancelled = true

    def cancel() = true
  }
}