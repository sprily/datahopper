package uk.co.sprily.dh
package util

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._

import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

class ResourcePoolSpec extends Specification with ScalaCheck
                                             with NoTimeConversions {

  "Resource Pool" should {

    "provide mutual exclusion" in {
      withES { es =>
        val singleResource = new Resource()
        val pool = ResourcePool(es)(
          create = singleResource,
          isValid = const(true),
          destroy = unit,
          maxResources = 1,
          timeout = 5.seconds)

        val ws = for (i <- 0 until 3) yield new Worker(pool, iters=1000)
        ws.foreach(_.start())
        ws.foreach(_.join())

        singleResource.numAcqs must === (0)
      }
    }

    "create no more than the max number of resources" in {
      withES { es =>
        val numCreated = new AtomicInteger()
        def create() = {
          numCreated.incrementAndGet()
          new Resource()
        }

        val pool = ResourcePool(es)(
          create = create,
          isValid = const(true),
          destroy = unit,
          maxResources = 4,
          timeout = 5.seconds)

        val ws = for (i <- 0 until 10) yield new Worker(pool, iters=1000)
        ws.foreach(_.start())
        ws.foreach(_.join())

        numCreated.get() must be_<=(4)
      }
    }

    "not lease out a destroyed Resource" in {
      withES { es =>
        val numCreated = new AtomicInteger()
        def create() = {
          numCreated.incrementAndGet()
          new Resource()
        }

        val pool = ResourcePool(es)(
          create = create,
          isValid = const(false),
          destroy = unit,
          maxResources = 4,
          timeout = 5.seconds)

        val ws = for (i <- 0 until 10) yield new Worker(pool, iters=2)
        ws.foreach(_.start())
        ws.foreach(_.join())

        numCreated.get() must === (2 * 10)
      }
    }

  }

  private def withES[T](body: ScheduledExecutorService => T) = {
    val es = new ScheduledThreadPoolExecutor(1)
    try { body(es) } finally { es.shutdownNow() }
  }

  private def const[S,T](t: T): S => T = { s: S => t }
  private def unit[T]: T => Unit = const[T,Unit]({})

  private class Worker(pool: ResourcePool[Resource],
                       iters: Int = 100) extends Thread {
    override def run() = {
      for (i <- 0 until iters) {
        pool.withResource { r =>
          r.workerAcquired()
          r.workerReleased()
        }
      }
    }
  }

  private class Resource {

    // deliberately no mutex for accessing this
    @volatile var numAcqs = 0L
    @volatile var destroyed = false

    def workerAcquired() = { numAcqs = numAcqs + 1 }
    def workerReleased() = { numAcqs = numAcqs - 1 }
  }

}
