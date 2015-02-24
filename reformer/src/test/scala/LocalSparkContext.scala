package com.liberator

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._

import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.Suite

// Manages a SparkContext variable, correctly stopping it after each test.
trait LocalSparkContext extends BeforeAndAfterEach with BeforeAndAfterAll { self: Suite =>

  @transient var sc: SparkContext = _

  override def beforeAll() {
    super.beforeAll()
  }

  override def afterEach() {
    resetSparkContext()
    super.afterEach()
  }

  def resetSparkContext() = {
    LocalSparkContext.stop(sc)
    sc = null
  }

}

object LocalSparkContext {
  def stop(sc: SparkContext) {
    if (sc != null) {
      sc.stop()
    }
    // To avoid Akka rebinding to the same port, since it doesn't unbind immediately on shutdown
    System.clearProperty("spark.driver.port")
  }

  /** Runs `f` by passing in `sc` and ensures that `sc` is stopped. */
  def withSpark[T](sc: SparkContext)(f: SparkContext => T) = {
    try {
      f(sc)
    } finally {
      stop(sc)
    }
  }

}
