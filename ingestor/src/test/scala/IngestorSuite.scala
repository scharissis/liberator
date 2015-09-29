package com.liberator

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import com.github.nscala_time.time.Imports._

import com.liberator.LocalSparkContext._
import com.liberator.PackageJson._


class IngestorSuite extends FunSuite with LocalSparkContext {

  val test_source = "src/test/resources/"

  private def newSparkContext(master:String = "local[2]"): SparkContext = {
    val conf = new SparkConf()
      .setMaster(master)
      .setAppName("test")
    val sc = new SparkContext(conf)
    sc
  }

  private def getIngestedDeps(sc: SparkContext, source: String, regex: String, time: org.joda.time.DateTime)
    : Map[String, Int] = {
      return Ingestor.run(
        sc = sc, source = source, file_regex = regex, startDate = time,
        output_dir = "", save_to_db = false, debug = false
      ).collect.toMap
  }

  test("start/stop SparkContext") {
    withSpark(newSparkContext()) { sc =>
      assert(sc != null)
    }
    assert(sc == null)
  }

  test("one package - within date") {
    // Range: 1440806400 (Sat, 29 Aug 2015 00:00:00 GMT) --> infinity
    withSpark(newSparkContext()) { sc =>  // TODO: Make time/day suit test data.
      val deps = getIngestedDeps(sc, test_source, "single/part-*", DateTime.yesterday.withTimeAtStartOfDay())

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

  test("one package - not within date") {
    // Range: 1440806400 (Sat, 29 Aug 2015 00:00:00 GMT) --> infinity
    withSpark(newSparkContext()) { sc =>  // TODO: Make time/day suit test data.
      val deps = getIngestedDeps(sc, test_source, "single/part-*", DateTime.now - 20.years)

      val expectedMap : Map[String,Int] = Map(
        ("test-repo" -> 0),
        ("test-dep-1" -> 0),
        ("test-dep-2" -> 0)
      )

      assert(deps.size != 0)
      expectedMap foreach { case (depName,usageCount) =>
        assert(deps.contains(depName) === true)
        assert(deps(depName) === usageCount)
      }
    }
  }


  test("two packages") {
    withSpark(newSparkContext()) { sc =>  // TODO: Make time/day suit test data. (I think this should fail)
      val deps = getIngestedDeps(sc, test_source, "two/part-*", DateTime.yesterday.withTimeAtStartOfDay())

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

  // Dates: Wed, 29 May 2013 19:54:02 GMT (1369857242).
  test("two packages - outside of date range") {
    withSpark(newSparkContext()) { sc =>  // TODO: Make time/day suit test data. (I think this should fail)
      val deps = getIngestedDeps(sc, test_source, "two/part-*", DateTime.now - 20.years)

      val expectedMap : Map[String,Int] = Map(
        ("test-repo" -> 0),
        ("test-dep-1" -> 0),
        ("test-dep-2" -> 0),
        ("test-dep-3" -> 0),
        ("test-dep-4" -> 0)
      )

      assert(deps.size != 0)
      expectedMap foreach { case (depName,usageCount) =>
        assert(deps.contains(depName) === true)
        assert(deps(depName) === usageCount)
      }
    }
  }

  // test-dep-1 and test-dep-2 are used by both test-repo-1 and test-repo-2.
  // Dates: Wed, 29 May 2013 19:54:02 GMT (1369857242).
  test("multiple packages") {
    withSpark(newSparkContext()) { sc =>
      val deps = getIngestedDeps(sc, test_source, "multiple/part-*", DateTime.yesterday.withTimeAtStartOfDay())

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

  // test-dep-1 and test-dep-2 are used by both test-repo-1 and test-repo-2.
  // Dates: Wed, 29 May 2013 19:54:02 GMT (1369857242).
  test("multiple packages - outside of date range") {
    withSpark(newSparkContext()) { sc =>
      val deps = getIngestedDeps(sc, test_source, "multiple/part-*", DateTime.now - 20.years)

      val expectedMap : Map[String,Int] = Map(
        ("test-repo-1" -> 0),
        ("test-repo-2" -> 0),
        ("test-dep-1" -> 0),
        ("test-dep-2" -> 0)
      )

      assert(deps.size != 0)
      expectedMap foreach { case (depName,usageCount) =>
        assert(deps.contains(depName) === true)
        assert(deps(depName) === usageCount)
      }
    }
  }

}
