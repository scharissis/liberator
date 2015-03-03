package com.liberator

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite

import com.liberator.LocalSparkContext._
import com.liberator.PackageJson._


class ReformerSuite extends FunSuite with LocalSparkContext {

  private def newSparkContext(): SparkContext = {
    val conf = new SparkConf()
      .setMaster("local")
      .setAppName("test")
    val sc = new SparkContext(conf)
    sc
  }

  private def getPackages(sc: SparkContext, source: String, regex: String)
    : org.apache.spark.rdd.RDD[PackageJson] = {
      return Reformer.run(sc, source, regex).flatMap(identity)
  }

  private def getDependencies(packages : org.apache.spark.rdd.RDD[PackageJson])
    : Map[String,List[Dependency]] = {
      return packages
        .map( p => (p.name, p.dependencies))
        .groupByKey
        .map( pair => (pair._1, pair._2.flatMap(identity).toList))
        .collect
        .toMap
  }

  val test_source = "src/test/resources/"

  test("start/stop SparkContext") {
    withSpark(newSparkContext()) { sc =>
      assert(sc != null)
    }
    assert(sc == null)
  }

  test("single package") {
    withSpark(newSparkContext()) { sc =>
      val packages = getPackages(sc, test_source, "single/package*.json")
      val dependencies = getDependencies(packages)

      assert(packages.count === 1)
      assert(dependencies.contains("d3"))
      assert(dependencies.keys.size === 1)
      assert(dependencies("d3").length === 4)

      val timestamp = "1337187438"
      val commit = "dd2a424f2bdb8fae1dab5ac27168f5bba186a0c4"
      val expectedResult : Map[String, List[Dependency]] =
        Map(
          "d3" -> List(
            Dependency("jsdom", List(Event("0.2.14", "new", timestamp, commit))),
            Dependency("sizzle", List(Event("1.1.x", "new", timestamp, commit))),
            Dependency("uglify-js", List(Event("1.2.3", "new", timestamp, commit))),
            Dependency("vows", List(Event("0.6.x", "new", timestamp, commit)))
          )
        )
      expectedResult("d3").foreach { expectedDep =>
        assert(dependencies("d3").contains(expectedDep) === true)
      }
    }
  }

  test("pair of identical packages") {
    withSpark(newSparkContext()) { sc =>
      val packages = getPackages(sc, test_source, "simple/identical/package*.json")
      val dependencies = getDependencies(packages)

      val timestamp = "1337187438"  // seconds
      val commit = "dd2a424f2bdb8fae1dab5ac27168f5bba186a0c4"
      val expectedResult : Map[String, List[Dependency]] =
        Map(
          "d3" -> List(
            Dependency("jsdom", List(Event("0.2.14", "new", timestamp, commit))),
            Dependency("sizzle", List(Event("1.1.x", "new", timestamp, commit))),
            Dependency("uglify-js", List(Event("1.2.3", "new", timestamp, commit))),
            Dependency("vows", List(Event("0.6.x", "new", timestamp, commit)))
          )
        )

      assert(packages.count === 2)
      assert(dependencies.contains("d3") === true)
      assert(dependencies("d3").size === 4)

      expectedResult("d3").foreach { expectedDep =>
        assert(dependencies("d3").contains(expectedDep) === true)
      }
    }
  }

  test("one new dependency") {
    withSpark(newSparkContext()) { sc =>
      val packages = getPackages(sc, test_source, "simple/new_one/package*.json")
      val dependencies = getDependencies(packages)

      val timestamp = "1337187438"  // seconds
      val timestamp2 = "1337287438"  // seconds
      val commit = "dd2a424f2bdb8fae1dab5ac27168f5bba186a0c4"
      val commit2 = "ed2a424f2bdb8fae1dab5ac27168f5bba186a0c4"
      val expectedResult : Map[String, List[Dependency]] =
        Map(
          "d3" -> List(
            Dependency("jsdom", List(Event("0.2.14", "new", timestamp, commit))),
            Dependency("sizzle", List(Event("1.1.x", "new", timestamp, commit))),
            Dependency("uglify-js", List(Event("1.2.3", "new", timestamp, commit))),
            Dependency("vows", List(Event("0.6.x", "new", timestamp, commit))),
            Dependency("new_one", List(Event("0.0.0", "new", timestamp2, commit2)))
          )
        )

      assert(packages.count === 2)
      assert(dependencies.contains("d3") === true)
      assert(dependencies("d3").size === 5)

      expectedResult("d3").foreach { expectedDep =>
        assert(dependencies("d3").contains(expectedDep) === true)
      }
    }
  }

}
