package uk.co.sprily
package dh
package modbus

import com.github.nscala_time.time.Imports._

import scodec.bits.ByteVector

import dh.harvester._

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
