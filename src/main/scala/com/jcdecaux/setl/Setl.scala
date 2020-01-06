package com.jcdecaux.setl

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import com.jcdecaux.setl.annotation.InterfaceStability
import com.jcdecaux.setl.config.ConfigLoader
import com.jcdecaux.setl.storage.connector.Connector
import com.jcdecaux.setl.storage.repository.SparkRepository
import com.jcdecaux.setl.storage.{ConnectorBuilder, SparkRepositoryBuilder}
import com.jcdecaux.setl.transformation.{Deliverable, Factory}
import com.jcdecaux.setl.util.TypesafeConfigUtils
import com.jcdecaux.setl.workflow.Pipeline
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

import scala.reflect.runtime.{universe => ru}
import scala.util.Random

@InterfaceStability.Evolving
abstract class Setl(val configLoader: ConfigLoader) {

  val spark: SparkSession

  private[setl] val inputRegister: ConcurrentHashMap[String, Deliverable[_]] = new ConcurrentHashMap()
  private[this] val pipelineRegister: ConcurrentHashMap[UUID, Pipeline] = new ConcurrentHashMap()

  private[this] val repositoryIdOf: String => String = config => s"@rpstry.$config"
  private[this] val connectorIdOf: String => String = config => s"@cnnctr.$config"

  /**
   * Get a SparkRepository[DT]. If the given config path hasn't been registered, then the repository will
   * firstly be registered and then be returned.
   *
   * @param repositoryId path to spark repository configuration
   * @tparam DT type of spark repository
   * @return
   */
  def getSparkRepository[DT: ru.TypeTag](repositoryId: String): SparkRepository[DT] = {
    setSparkRepository[DT](repositoryId)
    inputRegister.get(repositoryIdOf(repositoryId)).getPayload.asInstanceOf[SparkRepository[DT]]
  }

  def resetSparkRepository[DT: ru.TypeTag](repository: SparkRepository[DT],
                                           consumer: Seq[Class[_ <: Factory[_]]],
                                           deliveryId: String,
                                           repositoryId: String): this.type = {
    val deliverable = new Deliverable(repository).setConsumers(consumer).setDeliveryId(deliveryId)
    inputRegister.put(repositoryIdOf(repositoryId), deliverable)
    this
  }

  /**
   * Force register a spark repository for the given config path. If there this config path has been registered,
   * it will be updated
   *
   * @param config     path to spark repository configuration
   * @param consumer   Seq of consumer
   * @param deliveryId id of this delivery that will be used during the delivery matching
   * @tparam DT type of spark repository
   * @return
   */
  def resetSparkRepository[DT: ru.TypeTag](config: String,
                                           consumer: Seq[Class[_ <: Factory[_]]] = Seq.empty,
                                           deliveryId: String = Deliverable.DEFAULT_ID,
                                           readCache: Boolean = false): this.type = {
    val repo = new SparkRepositoryBuilder[DT](configLoader.getConfig(config)).getOrCreate().persistReadData(readCache)
    resetSparkRepository(repo, consumer, deliveryId, config)
    this
  }

  /**
   * Register a spark repository for the given config path. If there this config path has been registered,
   * it will NOT be updated
   *
   * @param config     path to spark repository configuration
   * @param consumer   Seq of consumer
   * @param deliveryId id of this delivery that will be used during the delivery matching
   * @tparam DT type of spark repository
   * @return
   */
  def setSparkRepository[DT: ru.TypeTag](config: String,
                                         consumer: Seq[Class[_ <: Factory[_]]] = Seq.empty,
                                         deliveryId: String = Deliverable.DEFAULT_ID,
                                         readCache: Boolean = false): this.type = {
    if (!inputRegister.contains(repositoryIdOf(config))) {
      resetSparkRepository[DT](config, consumer, deliveryId, readCache)
    }
    this
  }

  def setSparkRepository[DT: ru.TypeTag](repository: SparkRepository[DT],
                                         consumer: Seq[Class[_ <: Factory[_]]],
                                         deliveryId: String,
                                         repositoryId: String): this.type = {
    if (!inputRegister.contains(repositoryIdOf(repositoryId))) {
      resetSparkRepository[DT](repository, consumer, deliveryId, repositoryId)
    }
    this
  }

  /**
   * Get a Connector. If the given config path hasn't been registered, then the connector will
   * firstly be registered and then be returned.
   *
   * @param connectorId id of connector (could be the config path)
   * @tparam CN type of the connector
   * @return
   */
  def getConnector[CN <: Connector](connectorId: String): CN = {
    setConnector(connectorId)
    inputRegister.get(connectorIdOf(connectorId)).getPayload.asInstanceOf[CN]
  }

  /**
   * Register a connector. As each connector must have an delivery ID, by default the config path will be used.
   *
   * <p>If there this config path has been registered, it will NOT be updated.</p>
   *
   * @param config path to connector configuration
   * @return
   */
  def setConnector(config: String): this.type = this.setConnector(config, config)

  /**
   * Register a connector.
   *
   * <p>If there this config path has been registered, it will NOT be updated.</p>
   *
   * @param config     path to connector configuration
   * @param deliveryId delivery ID
   * @return
   */
  def setConnector(config: String, deliveryId: String): this.type = {
    if (!inputRegister.contains(connectorIdOf(config))) resetConnector[Connector](config, deliveryId, classOf[Connector])
    this
  }

  /**
   * Register a connector. As each connector must have an delivery ID, by default the config path will be used.
   *
   * <p>If there this config path has been registered, it will NOT be updated.</p>
   *
   * @param config path to connector configuration
   * @tparam CN type of connector
   * @return
   */
  def setConnector[CN <: Connector : ru.TypeTag](config: String, cls: Class[CN]): this.type =
    this.setConnector[CN](config, config, cls)


  /**
   * Register a connector.
   *
   * <p>If there this config path has been registered, it will NOT be updated.</p>
   *
   * @param config     path to connector configuration
   * @param deliveryId delivery ID
   * @param cls        class of the Connector
   * @tparam CN type of spark connector
   * @return
   */
  def setConnector[CN <: Connector : ru.TypeTag](config: String, deliveryId: String, cls: Class[CN]): this.type = {
    if (!inputRegister.contains(connectorIdOf(config))) resetConnector[CN](config, deliveryId, cls)
    this
  }

  /**
   * Register a connector.
   *
   * <p>If there this config path has been registered, it will NOT be updated.</p>
   *
   * @param connector   a connector
   * @param deliveryId  delivery ID
   * @param connectorId id of the Connector
   * @tparam CN type of spark connector
   * @return
   */
  def setConnector[CN <: Connector : ru.TypeTag](connector: CN, deliveryId: String, connectorId: String): this.type = {
    if (!inputRegister.contains(connectorIdOf(connectorId))) {
      resetConnector[CN](connector, deliveryId, connectorId)
    }
    this
  }

  /**
   * Register a connector.
   *
   * <p>If there this config path has been registered, it will be updated.</p>
   *
   * @param configPath path to connector configuration
   * @param deliveryId delivery ID
   * @param cls        class of the Connector
   * @tparam CN type of spark connector
   * @return
   */
  def resetConnector[CN <: Connector : ru.TypeTag](configPath: String, deliveryId: String, cls: Class[CN]): this.type = {
    val payload = new ConnectorBuilder(configLoader.getConfig(configPath)).getOrCreate().asInstanceOf[CN]
    resetConnector[CN](payload, deliveryId, configPath)
  }

  /**
   * Register a connector.
   *
   * <p>If there this config path has been registered, it will be updated.</p>
   *
   * @param connector  a connector
   * @param deliveryId delivery ID
   * @tparam CN type of spark connector
   * @return
   */
  def resetConnector[CN <: Connector : ru.TypeTag](connector: CN, deliveryId: String, connectorId: String): this.type = {
    val deliverable = new Deliverable(connector).setDeliveryId(deliveryId)
    inputRegister.put(connectorIdOf(connectorId), deliverable)
    this
  }

  def sparkSession: SparkSession = this.spark

  /**
   * Create a new pipeline. All the registered repositories and connectors will be passed into the delivery pool
   * of the pipeline.
   *
   * @return
   */
  def newPipeline(): Pipeline = {
    val _pipe = new Pipeline
    pipelineRegister.put(_pipe.getUUID, _pipe)
    import scala.collection.JavaConverters._
    inputRegister.asScala.foreach { case (_, del) => _pipe.setInput(del) }
    _pipe
  }

  /**
   * Find a pipeline by its UUID
   *
   * @param uuid UUID of the target pipeline
   * @return
   */
  def getPipeline(uuid: UUID): Pipeline = this.pipelineRegister.get(uuid)

  /**
   * Stop the spark session
   */
  def stop(): Unit = {
    this.spark.stop()
  }
}

object Setl {

  class Builder extends com.jcdecaux.setl.Builder[Setl] {

    private[this] var setl: Setl = _
    private[this] var contextConfiguration: Option[String] = None
    private[this] var configLoader: ConfigLoader = _
    private[this] var sparkConf: Option[SparkConf] = None
    private[this] var parallelism: Option[Int] = None
    private[this] var sparkMasterUrl: Option[String] = None

    private[this] val fallbackContextConfiguration: String = "setl.config"

    def setSetlConfigPath(config: String): this.type = {
      contextConfiguration = Option(config)
      this
    }

    def setSparkConf(sparkConf: SparkConf): this.type = {
      this.sparkConf = Option(sparkConf)
      this
    }

    def setParallelism(par: Int): this.type = {
      this.parallelism = Some(par)
      this
    }

    def setConfigLoader(configLoader: ConfigLoader): this.type = {
      this.configLoader = configLoader
      this
    }

    def setSparkMaster(url: String): this.type = {
      this.sparkMasterUrl = Option(url)
      this
    }

    def withDefaultConfigLoader(configFile: String): this.type = {
      this.configLoader = ConfigLoader.builder()
        .setAppName(sparkAppName)
        .setConfigPath(configFile)
        .getOrCreate()
      this
    }

    def withDefaultConfigLoader(): this.type = {
      this.configLoader = ConfigLoader.builder()
        .setAppName(sparkAppName)
        .getOrCreate()
      this
    }

    private[this] val sparkAppName: String = s"spark_app_${Random.alphanumeric.take(10).mkString("")}"

    private[this] def buildSparkSession(): SparkSession = {
      val pathOf: String => String = (s: String) => s"${contextConfiguration.getOrElse(fallbackContextConfiguration)}.$s"

      val usages: Array[String] = if (configLoader.has(pathOf("usages"))) {
        configLoader.getArray(pathOf("usages"))
      } else {
        Array()
      }

      val sparkConfigurations: Map[String, String] = try {
        TypesafeConfigUtils.getMap(configLoader.getConfig(pathOf("spark")))
      } catch {
        case _: com.typesafe.config.ConfigException.Missing =>
          log.warn(s"Config path ${pathOf("spark")} doesn't exist")
          Map.empty
      }

      val cassandraHost = configLoader.getOption(pathOf("cassandraHost"))

      val sparkSessionBuilder = new SparkSessionBuilder(usages: _*)
        .setAppName(configLoader.appName) // Set the default app name
        .setEnv(configLoader.appEnv) // Retrieve app env

      // SparkConf has the highest priority
      configureSpark(sparkConf, sparkSessionBuilder.withSparkConf)

      // Configure Spark with properties defined in the configuration file
      sparkSessionBuilder.set(sparkConfigurations)

      // Overwrite configuration file's properties with those defined in the application
      configureSpark(cassandraHost, sparkSessionBuilder.setCassandraHost)
      configureSpark(sparkMasterUrl, sparkSessionBuilder.setSparkMaster)

      sparkSessionBuilder.getOrCreate()
    }

    private[this] def configureSpark[T](opt: Option[T], setter: T => SparkSessionBuilder): Unit = {
      opt match {
        case Some(thing) => setter(thing)
        case _ =>
      }
    }

    /**
     * Build an object
     *
     * @return
     */
    override def build(): Builder.this.type = {
      setl = new Setl(configLoader) {
        override val spark: SparkSession = buildSparkSession()
      }
      this
    }

    override def get(): Setl = setl
  }

  def builder(): Setl.Builder = new Setl.Builder()

}
