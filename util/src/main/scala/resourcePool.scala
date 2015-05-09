package uk.co.sprily.dh
package util

import java.util.concurrent._
import scala.concurrent.duration._

import com.typesafe.scalalogging.LazyLogging

trait ResourcePool[A] {
  def withResource[B](action: A => B): B
}

object ResourcePool {

  def apply[A](es: ScheduledExecutorService)
              (create: => A,
               isValid: A => Boolean,
               destroy: A => Unit,
               maxResources: Int,
               timeout: FiniteDuration): ResourcePool[A] = {
    new ResourcePoolImpl(es, create, isValid, destroy, maxResources, timeout)
  }

  private class ResourcePoolImpl[A](es: ScheduledExecutorService,
                                    create: => A,
                                    isValid: A => Boolean,
                                    destroy: A => Unit,
                                    maxResources: Int,
                                    timeout: FiniteDuration) extends ResourcePool[A]
                                                                with LazyLogging {

    val taskF = es.scheduleAtFixedRate(new Runnable { def run() = sweep() },
                                       (timeout / 2).toMillis,
                                       (timeout / 2).toMillis,
                                       TimeUnit.MILLISECONDS)

    private[this] val numAvailable = new Semaphore(maxResources, true)

    // sorted by Deadline (oldest first)
    @volatile private[this] var available = Vector.empty[(Deadline,A)]

    def withResource[B](action: A => B): B = {
      val a = acquire()
      try {
        logger.debug(s"Performing action on resource")
        action(a)
      } catch {
        case (e: Exception) =>
          logger.warn(s"Caught exception performing action on $a: $e")
          tryDestroy(a)
          throw e
      } finally {
        release(a)
      }
    }

    private[this] def release(a: A): Unit = {
      logger.debug(s"Attempting to release Resource $a")
      if (isValid(a)) {
        synchronized { available = available :+ ((Deadline.now, a)) }
      }
      numAvailable.release()
    }

    private[this] def acquire(): A = {
      try {
        logger.debug("Attempting to acquire Resource")
        numAvailable.acquire()  // blocking
        logger.debug("Resource acquisition allowed, picking available...")
        pickAvailable()
      } catch {
        case e: Exception =>
          logger.warn(s"Exception caught acquiring Resource: $e")
          numAvailable.release()
          throw e
      }
    }

    private[this] def pickAvailable(): A = synchronized {
      available match {
        case as :+ ((_,a)) if  isValid(a) => available = as ; a
        case as :+ ((_,a)) if !isValid(a) => available = as ; tryDestroy(a) ; pickAvailable
        case _                            => create
      }
    }

    private[this] def sweep() = synchronized {
      logger.info(s"Sweeping for stale Resources")
      val now = Deadline.now
      available.takeWhile(_._1 + timeout < now)
               .foreach { case(_,a) => tryDestroy(a) }
      available = available.dropWhile(_._1 + timeout < now)
    }

    private[this] def tryDestroy(a: A) = swallowExceptionsIn {
      logger.info(s"Destroying Resource: $a")
      destroy(a)
    }

    private[this] def swallowExceptionsIn[T](body: => T) = {
      try { body } catch {
        case e: Exception =>
          logger.warn(s"Swallowed exception: $e")
      }
    }

  }
}
