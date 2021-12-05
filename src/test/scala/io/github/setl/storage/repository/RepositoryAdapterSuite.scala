package io.github.setl.storage.repository

import io.github.setl.SparkSessionBuilder
import io.github.setl.storage.Condition
import io.github.setl.storage.connector.CSVConnector
import org.apache.spark.sql.catalyst.encoders.ExpressionEncoder
import org.apache.spark.sql.{Dataset, SparkSession}
import org.scalatest.funsuite.AnyFunSuite

class RepositoryAdapterSuite extends AnyFunSuite {

  val path: String = "src/test/resources/test_repository_adapter"

  val data: Seq[RepoAdapterTesterA] = Seq(
    RepoAdapterTesterA("a", "A"),
    RepoAdapterTesterA("b", "B")
  )

  test("RepositoryAdapter should implicitly convert two dataset") {
    val spark: SparkSession = new SparkSessionBuilder().setEnv("local").build().get()
    val ds: Dataset[RepoAdapterTesterA] = spark.createDataset(data)(ExpressionEncoder[RepoAdapterTesterA])

    import io.github.setl.storage.repository.ImplicitConverter.a2b
    import io.github.setl.storage.repository.ImplicitRepositoryAdapter._

    val options: Map[String, String] = Map[String, String](
      "path" -> path,
      "inferSchema" -> "true",
      "delimiter" -> ",",
      "header" -> "true",
      "saveMode" -> "Overwrite"
    )

    val csvConnector = new CSVConnector(options)

    val repo: SparkRepository[RepoAdapterTesterA] =
      new SparkRepository[RepoAdapterTesterA]().setConnector(csvConnector)

    repo.convertAndSave(ds)
    val ds2 = repo.findAllAndConvert()
    val df = csvConnector.read()

    assert(ds2.columns === ds.columns)
    assert(df.columns === Array("column1", "col2", "col3"))
    csvConnector.delete()
  }

  test("RepositoryAdapter should be able to handle filter") {
    val spark: SparkSession = new SparkSessionBuilder().setEnv("local").build().get()
    val ds: Dataset[RepoAdapterTesterA] = spark.createDataset(data)(ExpressionEncoder[RepoAdapterTesterA])

    import io.github.setl.storage.repository.ImplicitConverter.a2b
    import io.github.setl.storage.repository.ImplicitRepositoryAdapter._

    val options: Map[String, String] = Map[String, String](
      "path" -> (path + "_filter"),
      "inferSchema" -> "true",
      "delimiter" -> ",",
      "header" -> "true",
      "saveMode" -> "Overwrite"
    )

    val csvConnector = new CSVConnector(options)

    val repo: SparkRepository[RepoAdapterTesterA] =
      new SparkRepository[RepoAdapterTesterA]().setConnector(csvConnector)

    repo.convertAndSave(ds)

    val conditions = Condition("column1", "=", "a")

    assert(repo.findByAndConvert(conditions).count() === 1)
    csvConnector.delete()
  }

}
