package uk.co.sprily
package dh
package modbus

import java.net.InetAddress

import scodec.bits.ByteVector

import harvester._

case class ModbusDevice(
    id: DeviceId,
    address: ModbusNetLoc) extends DeviceLike {

  type NetLoc = ModbusNetLoc
  type AddressSpace = RegRange
  type Measurement = ByteVector

}

case class ModbusNetLoc(
    host: InetAddress,
    port: Int,
    unit: Int)

case class RegRange(
    start: Int,
    end: Int)

