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
      .map( p => (p.name, p.dependencies)).groupByKey
      .map( pair => (pair._1, pair._2.flatMap(identity).toList))
      .collect
      .toMap
  }

  val test_source = "src/test/resources/"

  test("SparkContext - starts/stops") {
    withSpark(newSparkContext()) { sc =>
      assert(sc != null)
    }
    assert(sc == null)
  }

  test("simple - runs okay") {
    withSpark(newSparkContext()) { sc =>
      val output = Reformer.run(sc, source = test_source, file_regex = "simple/identical/package*.json")
      assert(output.count != 0)
    }
  }

  test("simple - identical inputs") {
    withSpark(newSparkContext()) { sc =>
      val packages = getPackages(sc, test_source, "simple/identical/package*.json")
      val dependencies = getDependencies(packages)

      assert(packages.count === 2)  // 2 input package.json's
      assert(dependencies.size === 1) // 1 resultant Package (they were identical)
      assert(dependencies("d3").size === 4) // 4 discrete Dependencies
    }
  }
}
