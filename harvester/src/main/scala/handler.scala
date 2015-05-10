package uk.co.sprily
package dh
package harvester

import java.util.concurrent.ScheduledExecutorService

import scala.concurrent.duration._

import com.github.nscala_time.time.Imports.DateTime

import com.typesafe.scalalogging.LazyLogging

import scalaz.concurrent._
import scalaz.stream._

import uk.co.sprily.dh.scheduling._

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

  // Some handlers are going to require life-cycle management, eg. setting
  // up TCP connection pools.
  def startup(): Unit = { }
  def shutdown(): Unit = { }

  def apply(request: Request): Task[Response]

  final def recurring(request: Request, interval: FiniteDuration): Process[Task, Response] = {
    implicit val Strat = Strategy.DefaultStrategy
    implicit val Sched = Strategy.DefaultTimeoutScheduler

    val requestRate = rate(Schedule.each(interval))
    val responses = Process.repeatEval(apply(request))
    retryEvery(Schedule.each(5.seconds))(requestRate zip responses) map (_._2)
  }

  private def rate(s: Schedule): Process[Task, TargetLike] = {
    implicit val Strat = Strategy.DefaultStrategy
    implicit val Sched = Strategy.DefaultTimeoutScheduler

    def next(t: s.Target) = Process.await(Task.delay(s.completed(t))) { t => go(t) }

    def go(target: Option[s.Target]): Process[Task, s.Target] = {
      target match {
        case None    => Process.halt
        case Some(t) => sleep(t) ++ Process.emit(t) ++ next(t)
      }
    }

    val t0 = Task.delay(Some(s.start()))
    Process.await(t0) { t => go(t) }
  }

  private def sleep(t: TargetLike)(implicit es: ScheduledExecutorService) = {
    t.initialDelay() match {
      case Duration.Zero => Process.empty
      case d             => time.sleep(d)
    }
  }

  private def retryEvery[A](schedule: Schedule)
                           (p: Process[Task,A])
                           (implicit s: ScheduledExecutorService): Process[Task,A] = {

    val logErrors = channel.lift[Task,Throwable,Unit](t => Task { logger.warn(s"Request error: $t") })

    rate(schedule).flatMap { _ =>
      p.attempt().observeW(logErrors).stripW
    }
  }
}

// Mixed in to package object
trait HandlerTypes {
  type Dispatch = PartialFunction[RequestLike, Task[ResponseLike]]
}
