package com.jcdecaux.datacorp.spark.transformation

import java.util.UUID

import com.jcdecaux.datacorp.spark.annotation.{Delivery, InterfaceStability}
import org.apache.spark.sql.Dataset

import scala.reflect.runtime

/**
  * DeliverySetterMetadata contains information of the @Delivery annotated method, including the name,
  * argument types, the producer and optional
  *
  * @param factoryUUID UUID of factory
  * @param name        name of the method
  * @param argTypes    type of each argument
  * @param producer    the producer class for the given data
  * @param optional    true if optional
  */
@InterfaceStability.Evolving
private[spark] case class FactoryDeliveryMetadata(factoryUUID: UUID,
                                                  name: String,
                                                  argTypes: List[runtime.universe.Type],
                                                  producer: Class[_ <: Factory[_]],
                                                  optional: Boolean,
                                                  autoLoad: Boolean = false,
                                                  condition: String = "",
                                                  id: String = "") {

  /**
    * As a setter method may have multiple arguments (even though it's rare), this method will return a list of
    * [[FactoryInput]] for each of argument of setter method
    *
    * @return
    */
  def getFactoryInputs: List[FactoryInput] = argTypes.map(tp => FactoryInput(tp, producer))

  def isDataset: List[Boolean] = argTypes.map {
    tp => tp.toString.startsWith(runtime.universe.typeOf[Dataset[_]].toString.dropRight(2))
  }
}

private[spark] object FactoryDeliveryMetadata {

  /**
    * Build a DeliverySetterMetadata from a given class
    */
  class Builder extends com.jcdecaux.datacorp.spark.Builder[Iterable[FactoryDeliveryMetadata]] {

    var cls: Class[_ <: Factory[_]] = _
    var factoryUUID: UUID = _
    var metadata: Iterable[FactoryDeliveryMetadata] = _

    def setFactory(factory: Factory[_]): this.type = {
      this.cls = factory.getClass
      this.factoryUUID = factory.getUUID
      this
    }

    override def build(): this.type = {

      log.debug(s"Search Deliveries of ${cls.getSimpleName}")

      // Black magic XD
      val classSymbol = runtime.universe.runtimeMirror(getClass.getClassLoader).classSymbol(cls)
      val methodsWithDeliveryAnnotation = classSymbol.info.decls.filter {
        x => x.annotations.exists(y => y.tree.tpe =:= runtime.universe.typeOf[Delivery])
      }

      if (methodsWithDeliveryAnnotation.isEmpty) log.info("No method having @Delivery annotation")

      metadata = methodsWithDeliveryAnnotation.map {
        mth =>
          val annotation = if (mth.isMethod) {
            cls
              .getDeclaredMethods
              .find(_.getName == mth.name.toString).get
              .getAnnotation(classOf[Delivery])
          } else {
            cls
              .getDeclaredField(mth.name.toString.trim)
              .getAnnotation(classOf[Delivery])
          }

          val producerMethod = annotation.annotationType().getDeclaredMethod("producer")
          val optionalMethod = annotation.annotationType().getDeclaredMethod("optional")
          val autoLoadMethod = annotation.annotationType().getDeclaredMethod("autoLoad")
          val conditionMethod = annotation.annotationType().getDeclaredMethod("condition")
          val idMethod = annotation.annotationType().getDeclaredMethod("id")

          val name = if (mth.isMethod) {
            log.debug(s"Find annotated method `${mth.name}` in ${cls.getSimpleName}")
            mth.name.toString
          } else {
            // If an annotated value was found, then return the default setter created by compiler, which is {valueName}_$eq.
            log.debug(s"Find annotated variable `${mth.name.toString.trim}` in ${cls.getSimpleName}")
            mth.name.toString.trim + "_$eq"
          }

          val argTypes = if (mth.isMethod) {
            mth.typeSignature.paramLists.head.map(_.typeSignature)
          } else {
            List(mth.typeSignature)
          }

          FactoryDeliveryMetadata(
            factoryUUID = factoryUUID,
            name = name,
            argTypes = argTypes,
            producer = producerMethod.invoke(annotation).asInstanceOf[Class[_ <: Factory[_]]],
            optional = optionalMethod.invoke(annotation).asInstanceOf[Boolean],
            autoLoad = autoLoadMethod.invoke(annotation).asInstanceOf[Boolean],
            condition = conditionMethod.invoke(annotation).asInstanceOf[String],
            id = idMethod.invoke(annotation).asInstanceOf[String]
          )
      }

      this
    }

    override def get(): Iterable[FactoryDeliveryMetadata] = metadata
  }


  /**
    * DeliverySetterMetadata Builder will create a [[com.jcdecaux.datacorp.spark.transformation.FactoryDeliveryMetadata]]
    * for each setter method (user defined or auto-generated by compiler)
    */
  def builder(): Builder = new Builder()
}