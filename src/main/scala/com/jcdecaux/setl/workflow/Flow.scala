package com.jcdecaux.setl.workflow

import com.jcdecaux.setl.internal.HasDescription
import com.jcdecaux.setl.transformation.Deliverable

import scala.reflect.runtime

/**
 * Flow is a representation of the data transfer in a Pipeline.
 *
 * @param from    origin node of the transfer
 * @param to      destination node of the transfer
 */
private[workflow] case class Flow(from: Node, to: Node) extends HasDescription {

  def payload: runtime.universe.Type = from.output.runtimeType

  def stage: Int = from.stage

  def deliveryId: String = from.output.deliveryId

  override def describe(): this.type = {
    if (deliveryId != Deliverable.DEFAULT_ID) {
      println(s"Delivery id : $deliveryId")
    }
    println(s"Stage       : $stage")
    println(s"Direction   : ${from.getPrettyName} ==> ${to.getPrettyName}")
    println(s"PayLoad     : ${getPrettyName(payload)}")
    println("----------------------------------------------------------")
    this
  }
}
