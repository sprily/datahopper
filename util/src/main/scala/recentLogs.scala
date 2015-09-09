package uk.co.sprily
package dh
package util

import scala.beans.BeanProperty
import scala.collection.immutable.Queue

import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.classic.spi.ILoggingEvent

import scalaz.concurrent.Task
import scalaz.stream._

object RecentLogs {

  def latest: Process[Task,Seq[String]] = sig.discrete
  //def latest: Process[Task,String] = sig.discrete.fold1[Queue[String]] { case (_, q) =>
  //  q.dequeue._2
  //}.pipe(process1.unchunk)

  private val sig = async.signalOf[Queue[String]](Queue.empty[String])

  private[util] def append(msg: String): Unit = {
    sig.compareAndSet(_.map(q => appendQ(40)(msg, q))).run
  }

  private def appendQ[A](limit: Int)(a: A, q: Queue[A]): Queue[A] = {
    (if (q.length >= limit) q.dequeue._2 else q).enqueue(a)
  }

}

/** A logback handler which forwards log entries to the Logging object
  * defined above.
  */
class RecentLogsAppender extends AppenderBase[ILoggingEvent] {

  /**
    * Configured by Bean-setting, for compatibility with logback config file
    */
  @BeanProperty
  var layout: PatternLayout = _

  override def start(): Unit = {
    if (layout == null) {
      addError(s"No layout set for the appender named [$name]")
      return
    }
    addInfo("Created RecentLogsAppender")
    super.start()
  }

  override def append(event: ILoggingEvent): Unit = {
    val msg = layout.doLayout(event)
    RecentLogs.append(msg)
  }
}
