package uk.co.sprily
package dh
package harvester

import com.github.nscala_time.time.Imports._

import scalaz.concurrent.Task

trait RequestLike {
  type Device <: DeviceLike
  type Selection = Device#AddressSpace

  val device: Device
  val selection: Selection
}

trait ResponseLike {
  type Device <: DeviceLike
  type Measurement = Device#Measurement

  val timestamp: DateTime
  val device: Device
  val measurement: Measurement
}

trait RequestHandler {
  type Request <: RequestLike
  type Response <: ResponseLike

  def apply(request: Request): Task[Response]
}

// Mixed in to package object
trait HandlerTypes {
  type Dispatch = PartialFunction[RequestLike, Task[ResponseLike]]
}
