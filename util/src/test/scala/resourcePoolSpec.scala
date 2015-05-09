package uk.co.sprily.dh
package util

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._

import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

class ResourcePoolSpec extends Specification with ScalaCheck {

  "Resource Pool" should {

    "provide mutual exclusion" in {
      val singleResource = new Resource()
      val p = ResourcePool.apply(
        create = singleResource,
        isValid = const(true),
        destroy = unit,
        maxResources = 1,
        timeout = 5.seconds)

      withPool(p) { pool =>
        val ws = for (i <- 0 until 3) yield new Worker(pool, iters=1000)
        ws.foreach(_.start())
        ws.foreach(_.join())

        singleResource.numAcqs must === (0)
      }
    }

    "create no more than the max number of resources" in {
      val numCreated = new AtomicInteger()
      def create() = {
        numCreated.incrementAndGet()
        new Resource()
      }

      val p = ResourcePool.apply(
        create = create,
        isValid = const(true),
        destroy = unit,
        maxResources = 4,
        timeout = 5.seconds)

      withPool(p) { pool =>
        val ws = for (i <- 0 until 10) yield new Worker(pool, iters=1000)
        ws.foreach(_.start())
        ws.foreach(_.join())

        numCreated.get() must be_<=(4)
      }
    }

    "not lease out a destroyed Resource" in {
      val numCreated = new AtomicInteger()
      def create() = {
        numCreated.incrementAndGet()
        new Resource()
      }

      val p = ResourcePool.apply(
        create = create,
      isValid = (r: Resource) => !r.beenAcquired,
        destroy = unit,
        maxResources = 4,
        timeout = 5.seconds)

      withPool(p) { pool =>
        val ws = for (i <- 0 until 3) yield new Worker(pool, iters=2)
        ws.foreach(_.start())
        ws.foreach(_.join())

      }
      numCreated.get() must === (2 * 3)
    }

  }

  private def withPool[S,T](pool: ResourcePool[S])(body: ResourcePool[S] => T) = {
    try { body(pool) } finally { pool.close() }
  }

  private def const[S,T](t: T): S => T = { s: S => t }
  private def unit[T]: T => Unit = const[T,Unit]({})

  private class Worker(pool: ResourcePool[Resource],
                       iters: Int = 100) extends Thread {
    override def run() = {
      for (i <- 0 until iters) {
        pool.withResource(2.seconds) { r =>
          r.workerAcquired()
          r.workerReleased()
        }
      }
    }
  }

  private class Resource {

    // deliberately no mutex for accessing these, as that's the job of the pool!
    @volatile var numAcqs = 0L
    @volatile var destroyed = false
    @volatile var beenAcquired = false

    def workerAcquired() = { numAcqs = numAcqs + 1 ; beenAcquired = true }
    def workerReleased() = { numAcqs = numAcqs - 1 }
  }

}
