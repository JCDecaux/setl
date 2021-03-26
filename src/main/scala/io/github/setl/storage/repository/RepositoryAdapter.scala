package io.github.setl.storage.repository

import io.github.setl.Converter
import io.github.setl.annotation.InterfaceStability
import io.github.setl.storage.Condition

/**
 * RepositoryAdapter could be used when one wants to save a `Dataset[A]` to a data store of type `B`.
 *
 * A `Repository[A]` and a `DatasetConverter[A, B]` must be provided (either explicitly or implicitly)
 *
 * @tparam A Type of the Repository
 * @tparam B Target data store type
 */
@InterfaceStability.Evolving
trait RepositoryAdapter[A, B] {

  val repository: Repository[A]

  val converter: Converter

  def findAllAndConvert(): A

  def findByAndConvert(conditions: Set[Condition]): A

  def findByAndConvert(condition: Condition): A

  def convertAndSave(data: A, suffix: Option[String]): this.type

}
