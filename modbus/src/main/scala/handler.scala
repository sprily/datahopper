package uk.co.sprily
package dh
package modbus

import java.util.concurrent.ExecutorService
import java.net.InetAddress
import scala.concurrent.duration._
import scala.collection.mutable.{Map => MMap}

import com.github.nscala_time.time.Imports.DateTime
import com.github.nscala_time.time.Imports.DateTimeZone

import com.typesafe.scalalogging.LazyLogging

import scalaz.concurrent.Task
import scalaz.concurrent.Strategy

import scodec.bits.ByteVector

import dh.harvester._
import dh.util.ResourcePool

case class ModbusRequest(
    device: ModbusDevice,
    selection: RegRange) extends RequestLike {

  type Device = ModbusDevice
}

case class ModbusResponse(
    device: ModbusDevice,
    timestamp: DateTime,
    measurement: ByteVector) extends ResponseLike {

  type Device = ModbusDevice
}

class ModbusRequestHandler(
    ioPool: ExecutorService,   // where to perform the blocking IO
    maxConnections: Int,
    closeUnusedConnectionAfter: FiniteDuration) extends RequestHandler
                                                   with LazyLogging {

  import com.ghgande.j2mod.modbus.msg.{ModbusRequest => _, ModbusResponse => _, _}
  import com.ghgande.j2mod.modbus.io._
  import com.ghgande.j2mod.modbus.net._

  type Request  = ModbusRequest
  type Response = ModbusResponse

  type Host = InetAddress
  type Port = Int
  type Gateway = (Host, Port)

  // a pool for each gateway
  private[this] val pools = MMap.empty[Gateway, ResourcePool[TCPMasterConnection]]

  override def shutdown(): Unit = {
    pools.synchronized {
      pools.values.foreach(_.close())
      pools.clear()
    }
  }

  override def apply(request: ModbusRequest): Task[ModbusResponse] = {
    single(request).map { bytes =>
      ModbusResponse(request.device, DateTime.now(DateTimeZone.UTC), bytes)
    }
  }

  private def single(request: ModbusRequest) = {
    val unit = request.device.unit

    withConnection(request.device) { conn =>
      logger.debug(s"Handling modbus request: $request")
      val req = new ReadMultipleRegistersRequest(
        request.selection.start,
        request.selection.numRegisters)
      req.setUnitID(unit)
      val tx = new ModbusTCPTransaction(conn)
      tx.setRequest(req)
      tx.execute() // blocking
      //Thread.sleep(2000)
      val response = tx.getResponse()//.asInstanceOf[ReadMultipleRegistersResponse]
      val result = response.asInstanceOf[ReadMultipleRegistersResponse]
      result.getRegisters.map(r => ByteVector(r.toBytes))
                         .reduce(_ ++ _)
    }
  }

  private def withConnection[T](device: ModbusDevice)
                               (body: TCPMasterConnection => T): Task[T] = {

    Task {
      val gateway = (device.address.host, device.address.port)

      val pool = pools.synchronized {
        if (!pools.contains(gateway)) {
          pools.put(gateway, mkPool(gateway))
        }
        pools.get(gateway).get
      }

      pool.withResource(3.seconds)(body)

    }(ioPool)
  }

  private def mkPool(gw: Gateway) = {

    def destroyConn(c: TCPMasterConnection) = {
      logger.info(s"Closing connection to $gw")
      c.close()
    }

    logger.info(s"Creating new pool for $gw")
    ResourcePool.apply(
      create = mkConnection(gw),
      isValid = { c: TCPMasterConnection => c.isConnected },
      destroy = destroyConn _,
      maxResources = maxConnections,
      timeout = closeUnusedConnectionAfter)
  }

  private def mkConnection(gw: Gateway) = {
    logger.info(s"New connection to $gw")
    val conn = new TCPMasterConnection(gw._1)
    conn.setPort(gw._2)
    conn.connect()    // blocking
    conn
  }

  private def unit[T]: T => Unit = { t: T => {} }

}
