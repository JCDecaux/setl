package io.github.setl.storage

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

import io.github.setl.annotation.InterfaceStability
import io.github.setl.enums.ValueType
import io.github.setl.util.DateUtils
import org.apache.spark.sql.Column

/**
 * Condition is used by [[io.github.setl.storage.repository.Repository]] to find data
 *
 * @param key       key of the field
 * @param operator  e.g. ">", "<", ">=", "<=", "="
 * @param value     value to compare
 * @param valueType type of the value
 */
@InterfaceStability.Evolving
case class Condition(key: String, operator: String, value: Option[String], valueType: ValueType) {

  private[this] val _key: String = s"`$key`"

  /**
   * Convert a [[io.github.setl.storage.Condition]] object to a spark SQL query string
   *
   * @throws IllegalArgumentException if a datetime/date filter doesn't have a value with correct format,
   *                                  an illegal argument exception will be thrown
   * @return String
   */
  @throws[IllegalArgumentException]
  def toSqlRequest: String = {
    val query: String = if (this.value.isDefined) {
      this.valueType match {
        case ValueType.DATETIME =>
          val t = DateUtils.reformatDateTimeString(this.value.get, withTime = true, end = if (this.operator.contains(">")) false else true)
          s"(${this._key} ${this.operator} cast('$t' as ${this.valueType.value}))"

        case ValueType.DATE =>
          val t = DateUtils.reformatDateTimeString(this.value.get, withTime = false, end = if (this.operator.contains(">")) false else true)
          s"(${this._key} ${this.operator} cast('$t' as ${this.valueType.value}))"

        case ValueType.STRING =>
          s"(${this._key} ${this.operator} '${this.value.get}')"

        case ValueType.COLUMN =>
          this.value.get

        case _ =>
          s"(${this._key} ${this.operator} ${this.value.get})"
      }
    } else {
      null
    }
    query
  }
}

object Condition {

  def apply(key: String, operator: String, value: String, valueType: ValueType): Condition = Condition(key, operator, Some(value), valueType)

  def apply(key: String, operator: String, value: String): Condition = Condition(key, operator, Some(value), ValueType.STRING)

  def apply(key: String, operator: String, value: Int): Condition = Condition(key, operator, Some(value.toString), ValueType.NUMBER)

  def apply(key: String, operator: String, value: Long): Condition = Condition(key, operator, Some(value.toString), ValueType.NUMBER)

  def apply(key: String, operator: String, value: Float): Condition = Condition(key, operator, Some(value.toString), ValueType.NUMBER)

  def apply(key: String, operator: String, value: Double): Condition = Condition(key, operator, Some(value.toString), ValueType.NUMBER)

  def apply(key: String, operator: String, value: LocalDate): Condition = {
    Condition(key, operator, Option(value.toString), ValueType.DATE)
  }

  def apply(key: String, operator: String, value: LocalDateTime): Condition = {
    Condition(key, operator, Option(value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))), ValueType.DATETIME)
  }

  def apply(key: String, operator: String, value: Set[_]): Condition = {
    val reformatValue: Set[String] = value.map {
      case str: String => s"'$str'"
      case num => num.toString
    }
    Condition(key, operator.toUpperCase(), Some(reformatValue.mkString("(", ", ", ")")), ValueType.SET)
  }

  def apply(column: Column): Condition = {
    Condition(null, null, Some(column.expr.sql), ValueType.COLUMN)
  }
}
