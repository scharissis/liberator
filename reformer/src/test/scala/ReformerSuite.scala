package com.liberator

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite

import com.liberator._
import com.liberator.LocalSparkContext._

class ReformerSuite extends FunSuite with LocalSparkContext {

  private def newSparkContext(): SparkContext = {
    val conf = new SparkConf()
      .setMaster("local")
      .setAppName("test")
    val sc = new SparkContext(conf)
    sc
  }

  val test_source = "src/test/resources/"

  test("instantiates a SparkContext") {
    withSpark(newSparkContext()) { sc =>
      assert(sc != null)
    }
    assert(sc == null)
  }

  test("reforms") {
    withSpark(newSparkContext()) { sc =>
      assert(Reformer.output == null)
      Reformer.run(sc, source = "../crawler/output/repos/raw/github", file_regex = "/f*/*/package_*.json")
      assert(Reformer.output.count != 0)
    }
  }
}
