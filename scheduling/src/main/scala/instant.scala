package uk.co.sprily.dh

package scheduling

import scala.concurrent.duration.Deadline

/** An Instant is really just a Deadline, but there are places where calling
  * it a Deadline is a little mis-leading
  *
  * This trait is mixed into the scheduling package object.
  */
trait InstantTypes {
  type Instant = Deadline
}

object Instant {
  def now(): Instant = Deadline.now
}

