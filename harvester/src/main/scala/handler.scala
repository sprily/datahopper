package uk.co.sprily
package dh
package harvester

import java.util.concurrent.ScheduledExecutorService

import scala.concurrent.duration._

import com.github.nscala_time.time.Imports.DateTime

import com.typesafe.scalalogging.LazyLogging

import scalaz.concurrent._
import scalaz.stream._

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

trait RequestHandler extends LazyLogging {
  type Request <: RequestLike
  type Response <: ResponseLike

  def apply(request: Request): Task[Response]

  final def recurring(request: Request, interval: FiniteDuration): Process[Task, Response] = {
    implicit val Strat = Strategy.DefaultStrategy
    implicit val Sched = Strategy.DefaultTimeoutScheduler

    val requestRate = time.awakeEvery(interval)
    val responses = Process.repeatEval(apply(request))
    retryEvery(5.seconds)(requestRate zip responses) map (_._2)
  }

  private def retryEvery[A](interval: FiniteDuration)
                           (p: Process[Task,A])
                           (implicit s: ScheduledExecutorService): Process[Task,A] = {

    val logErrors = channel.lift[Task,Throwable,Unit](t => Task { logger.warn(s"Request error: $t") })

    time.awakeEvery(interval).flatMap { _ =>
      p.attempt().observeW(logErrors).stripW
    }
  }
}

// Mixed in to package object
trait HandlerTypes {
  type Dispatch = PartialFunction[RequestLike, Task[ResponseLike]]
}
