package uk.co.sprily.dh

package scheduling

import scala.concurrent.duration._

case class Delay(schedule: Schedule, delay: FiniteDuration) extends Schedule {
    
  case class Target(
      val initiateAt: Deadline,
      val timeoutAt: Deadline,
      val underlying: schedule.Target) extends TargetLike

  object Target {
    def apply(underlying: schedule.Target): Target = Target(
      initiateAt = underlying.initiateAt,
      timeoutAt = underlying.timeoutAt,
      underlying = underlying)
  }

  def startAt(now: Instant) = {
    val underlying = schedule.startAt(now + delay)
    Target(underlying)
  }

  def completedAt(previous: Target, now: Instant) = {
    schedule.completedAt(previous.underlying, now)
            .map(Target.apply _)
  }

  def timedOutAt(previous: Target, now: Instant) = {
    schedule.timedOutAt(previous.underlying, now)
            .map(Target.apply _)
  }
}
