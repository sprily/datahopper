package uk.co.sprily
package dh
package modbus

import java.net.InetAddress

import scodec.bits.ByteVector

import harvester._

case class ModbusDevice(
    id: DeviceId,
    host: InetAddress,
    port: Int,
    unit: Int) extends DeviceLike {

  type NetLoc = ModbusNetLoc
  type AddressSpace = RegRange
  type Measurement = ByteVector

  def address = ModbusNetLoc(host, port, unit)

}

case class ModbusNetLoc(
    host: InetAddress,
    port: Int,
    unit: Int)

case class RegRange(
    start: Int,
    end: Int)

