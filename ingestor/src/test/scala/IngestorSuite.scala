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
    : org.apache.spark.rdd.RDD[(String, Int)] = {
      return Ingestor.run(sc, source, regex)
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
      deps foreach println
    }
  }

}
