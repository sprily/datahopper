package uk.co.sprily
package dh
package util

import scala.concurrent.duration._

import com.typesafe.scalalogging.LazyLogging

import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.PooledObjectFactory
import org.apache.commons.pool2.impl.DefaultPooledObject
import org.apache.commons.pool2.impl.GenericObjectPool
import org.apache.commons.pool2.impl.GenericObjectPoolConfig

trait ResourcePool[A] {
  def withResource[B](timeout: FiniteDuration)(action: A => B): B
  def close(): Unit
}

object ResourcePool {

  def apply[A](create: => A,
               isValid: A => Boolean,
               destroy: A => Unit,
               maxResources: Int,
               timeout: FiniteDuration): ResourcePool[A] = {
    new ApacheResourcePoolImpl[A](create, isValid, destroy, maxResources, timeout)
  }

  private class ApacheResourcePoolImpl[A](
      create: => A,
      isValid: A => Boolean,
      destroy: A => Unit,
      maxResources: Int,
      timeout: FiniteDuration) extends ResourcePool[A] with LazyLogging { self =>

  
    private[this] val factory: PooledObjectFactory[A] = new BasePooledObjectFactory[A] {
      override def create: A = self.create
      override def wrap(a: A) = new DefaultPooledObject[A](a)
      override def destroyObject(pA: PooledObject[A]) = self.destroy(pA.getObject)
      override def validateObject(pA: PooledObject[A]) = self.isValid(pA.getObject)
    }

    private[this] val config = {
      val cfg = new GenericObjectPoolConfig()
      cfg.setMaxIdle(maxResources)
      cfg.setMinIdle(0)
      cfg.setMaxTotal(maxResources)
      cfg
    }

    private[this] val underlying = {
      val pool = new GenericObjectPool[A](factory, config)
      pool.setMinEvictableIdleTimeMillis(timeout.toMillis)
      pool.setTimeBetweenEvictionRunsMillis((timeout / 2).toMillis)
      pool.setLifo(true)
      pool.setTestOnReturn(true)
      pool
    }

    override def withResource[B](timeout: FiniteDuration)(action: A => B): B = {

      var a: Option[A] = None
      try {
        a = Some(underlying.borrowObject(timeout.toMillis))
      } catch {
        case (e: Exception) =>
          throw new RuntimeException(s"Unable to acquire resource: $e")
      }

      var invalidated = false
      try {
        action(a.get)
      } catch {
        case (e: Exception) =>
          logger.warn(s"Caught exception performing action on $a: $e")
          invalidated = true
          underlying.invalidateObject(a.get)
          throw e
      } finally {
        if (!invalidated) { underlying.returnObject(a.get) }
      }
    }

    override def close(): Unit = {
      underlying.close()
    }

  }

}
