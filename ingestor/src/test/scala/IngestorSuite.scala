package com.liberator

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite

import com.liberator.LocalSparkContext._
import com.liberator.PackageJson._


class IngestorSuite extends FunSuite with LocalSparkContext {

  val test_source = "src/test/resources/"

  private def newSparkContext(): SparkContext = {
    val conf = new SparkConf()
      .setMaster("local")
      .setAppName("test")
    val sc = new SparkContext(conf)
    sc
  }

  private def getIngestedDeps(sc: SparkContext, source: String, regex: String)
    : Map[String, Int] = {
      return Ingestor.run(sc, source, regex, save_to_db = false).collect.toMap
  }

  test("start/stop SparkContext") {
    withSpark(newSparkContext()) { sc =>
      assert(sc != null)
    }
    assert(sc == null)
  }

  test("one package") {
    withSpark(newSparkContext()) { sc =>
      val deps = getIngestedDeps(sc, test_source, "single/part-*")

      val expectedMap : Map[String,Int] = Map(
        ("test-repo" -> 0),
        ("test-dep-1" -> 1),
        ("test-dep-2" -> 1)
      )

      assert(deps.size != 0)
      expectedMap foreach { case (depName,usageCount) =>
        assert(deps.contains(depName) === true)
        assert(deps(depName) === usageCount)
      }
    }
  }

  test("two packages") {
    withSpark(newSparkContext()) { sc =>
      val deps = getIngestedDeps(sc, test_source, "two/part-*")

      val expectedMap : Map[String,Int] = Map(
        ("test-repo" -> 0),
        ("test-dep-1" -> 1),
        ("test-dep-2" -> 1),
        ("test-dep-3" -> 1),
        ("test-dep-4" -> 1)
      )

      assert(deps.size != 0)
      expectedMap foreach { case (depName,usageCount) =>
        assert(deps.contains(depName) === true)
        assert(deps(depName) === usageCount)
      }
    }
  }

  // test-dep-1 and test-dep-2 are used by both test-repo-1 and test-repo-2.
  test("multiple packages") {
    withSpark(newSparkContext()) { sc =>
      val deps = getIngestedDeps(sc, test_source, "multiple/part-*")

      val expectedMap : Map[String,Int] = Map(
        ("test-repo-1" -> 0),
        ("test-repo-2" -> 0),
        ("test-dep-1" -> 2),
        ("test-dep-2" -> 2)
      )

      assert(deps.size != 0)
      expectedMap foreach { case (depName,usageCount) =>
        assert(deps.contains(depName) === true)
        assert(deps(depName) === usageCount)
      }
    }
  }

}
