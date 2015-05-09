package uk.co.sprily
package dh
package harvester

case class DeviceId(value: Long) extends AnyVal

trait DeviceLike {

  type NetLoc
  type AddressSpace
  type Measurement

  def id: DeviceId
  def address: NetLoc

}
