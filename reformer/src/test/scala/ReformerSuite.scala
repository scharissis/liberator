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
      return Reformer.run(sc, source, regex, output_dir="")
  }

  private def getDependencies(packages : org.apache.spark.rdd.RDD[PackageJson])
    : Map[String,List[Dependency]] = {
      return packages
        .map( p => (p.name, p.dependencies) )
        .groupByKey
        .map( pair => (pair._1, pair._2.flatMap(identity).toList) )
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

  test("shmoosh - merge packages") {
    withSpark(newSparkContext()) { sc =>
      val e1: List[Event] = List(Event("0.1", "new", "1", "commit1"))
      val e2: List[Event] = List(Event("0.2", "updated", "2", "commit2"))
      val e3: List[Event] = List(Event("0.2", "removed", "3", "commit3"))
      val d1: List[Dependency] = List(Dependency("d1-a", e1),Dependency("d1-b", e2))
      val d2: List[Dependency] = List(Dependency("d2", e3))
      val d3: List[Dependency] = List(Dependency("d3", e3))
      val p1: PackageJson = PackageJson("A", "source1", d2)
      val p2: PackageJson = PackageJson("X", "source2", d1)
      val p3: PackageJson = PackageJson("A", "source1", d3)
      val packages: Iterable[PackageJson] = List(p1,p2,p3)

      val input: org.apache.spark.rdd.RDD[(String,Iterable[PackageJson])] = sc.parallelize(
        List(("repo-one", packages))
      )

      val output: List[PackageJson] = Reformer.shmoosh(input).collect.toList

      val expected: List[PackageJson] = List(
        PackageJson("A", "source1", d2++d3),
        PackageJson("X", "source2", d1)
      )

      assert(output.size == 2)
      assert(output == expected)

      output.foreach { case(pkg) =>
        assert( expected.contains(pkg) )
        assert( (expected.find{ p => p.name == pkg.name }).size == 1 )
        val expectedPackage = expected.find{ p => p.name == pkg.name }.get
        pkg.dependencies.foreach {
          d => assert(expectedPackage.dependencies.contains(d))
        }
      }

    }
  }

  test("shmoosh - merge dependencies") {
    withSpark(newSparkContext()) { sc =>
      val e1: List[Event] = List(Event("0.1", "new", "1", "commit1"))
      val e2: List[Event] = List(Event("0.2", "updated", "2", "commit2"))
      val e3: List[Event] = List(Event("0.2", "removed", "3", "commit3"))
      val d1: Dependency = Dependency("d1", e1++e2)
      val d2: Dependency = Dependency("d1", e3)
      val p1: PackageJson = PackageJson("A", "source1", List(d1))
      val p2: PackageJson = PackageJson("A", "source2", List(d2))
      val p3: PackageJson = PackageJson("B", "source3", List(d1))
      val packages: Iterable[PackageJson] = List(p1,p2,p3)

      val input: org.apache.spark.rdd.RDD[(String,Iterable[PackageJson])] = sc.parallelize(
        List(("repo-one", packages))
      )
      val output: List[PackageJson] = Reformer.shmoosh(input).collect.toList
      val expected: List[PackageJson] = List(
        PackageJson("A", "source1", List(Dependency("d1", e1++e2++e3))),
        PackageJson("B", "source3", List(d1))
      )

      assert(output.size == 2)
      assert( (output.find{ p => p.name == "A"}).size == 1 )
      assert( (output.find{ p => p.name == "B"}).size == 1 )
      assert(output == expected)

      output.foreach { case(pkg) =>
        assert( expected.contains(pkg) )
        assert( (expected.find{ p => p.name == pkg.name }).size == 1 )
        val expectedPackage = expected.find{ p => p.name == pkg.name }.get

        // Check dependencies are equal
        pkg.dependencies.foreach {
          d => assert(expectedPackage.dependencies.contains(d))
        }
        // Check events are equal
        pkg.dependencies.foreach {
          d => {
            val expectedDep = (expectedPackage.dependencies.find{ ed=> d.name==ed.name }).get
            d.usage.foreach { u =>
              assert(expectedDep.usage.contains(u))
            }
          }
        }
        assert( (output.find{ p => p.name == "A"}).get.dependencies.size == 1 )
        assert(
          (output.find{ p => p.name == "A"}).get.dependencies
            .map(_.usage.map(_.version)).flatMap(identity)
          == List("0.1", "0.2", "0.2")
        )
        assert(
          (output.find{ p => p.name == "B"}).get.dependencies
            .map(_.usage.map(_.version)).flatMap(identity)
          == List("0.1", "0.2")
        )
      }

    }
  }

  test("single package") {
    withSpark(newSparkContext()) { sc =>
      val packages = getPackages(sc, test_source, "single/package*.json")
      val dependencies = getDependencies(packages)

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

      assert(packages.count === 1)
      assert(dependencies.contains("d3"))
      assert(dependencies.keys.size === 1)
      assert(dependencies("d3").length === 4)
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

      assert(packages.count === 1)
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

      assert(packages.count === 1)
      assert(dependencies.contains("d3") === true)
      assert(dependencies("d3").size === 5)
      expectedResult("d3").foreach { expectedDep =>
        assert(dependencies("d3").contains(expectedDep) === true)
      }
    }
  }

  test("one removed dependency") {
    withSpark(newSparkContext()) { sc =>
      val packages = getPackages(sc, test_source, "simple/removed_one/package*.json")
      val dependencies = getDependencies(packages)

      val timestamp = "1337187438"  // seconds
      val timestamp2 = "1337287438"  // seconds
      val commit = "dd2a424f2bdb8fae1dab5ac27168f5bba186a0c4"
      val commit2 = "ed2a424f2bdb8fae1dab5ac27168f5bba186a0c4"
      val expectedResult : Map[String, List[Dependency]] =
        Map(
          "d3" -> List(
            Dependency("jsdom", List(Event("0.2.14", "new", timestamp, commit))),
            Dependency("sizzle", List(
              Event("1.1.x", "new", timestamp, commit),
              Event("1.1.x", "removed", timestamp2, commit2)
            )),
            Dependency("uglify-js", List(Event("1.2.3", "new", timestamp, commit))),
            Dependency("vows", List(Event("0.6.x", "new", timestamp, commit)))
          )
        )

      assert(packages.count === 1)
      assert(dependencies.contains("d3") === true)
      assert(dependencies("d3").size === 4)
      expectedResult("d3").foreach { expectedDep =>
        assert(dependencies("d3").contains(expectedDep) === true)
      }

      assert(packages.collect.toList ==
        List(
          PackageJson("d3", "unknown", List(
            Dependency("jsdom", List(Event("0.2.14", "new", timestamp, commit))),
            Dependency("sizzle", List(
              Event("1.1.x", "new", timestamp, commit),
              Event("1.1.x", "removed", timestamp2, commit2)
            )),
            Dependency("uglify-js", List(Event("1.2.3", "new", timestamp, commit))),
            Dependency("vows", List(Event("0.6.x", "new", timestamp, commit)))
          ))
        )
      )
    }
  }

  test("new one which is then updated") {
    withSpark(newSparkContext()) { sc =>
      val packages = getPackages(sc, test_source, "simple/new_then_update/package*.json")
      val dependencies = getDependencies(packages)

      val timestamp = "1337187438"  // seconds
      val timestamp2 = "1337287438"  // seconds
      val timestamp3 = "1337387438"  // seconds
      val commit = "dd2a424f2bdb8fae1dab5ac27168f5bba186a0c4"
      val commit2 = "ed2a424f2bdb8fae1dab5ac27168f5bba186a0c4"
      val commit3 = "fd2a424f2bdb8fae1dab5ac27168f5bba186a0c4"
      val expectedResult : Map[String, List[Dependency]] = Map(
        "d3" -> List(
          Dependency("jsdom", List(Event("0.2.14", "new", timestamp, commit))),
          Dependency("sizzle", List(Event("1.1.x", "new", timestamp, commit))),
          Dependency("uglify-js", List(Event("1.2.3", "new", timestamp, commit))),
          Dependency("vows", List(Event("0.6.x", "new", timestamp, commit))),
          Dependency("new_one", List(
            Event("0.0.0", "new", timestamp2, commit2),
            Event("0.0.1", "updated", timestamp3, commit3)
            )
          )
        )
      )

      assert(packages.count === 1)
      assert(dependencies.contains("d3") === true)
      assert(dependencies("d3").size === 5)
      expectedResult("d3").foreach { expectedDep =>
        assert(dependencies("d3").contains(expectedDep) === true)
      }
      val new_dep = dependencies("d3").find{ d => d.name == "new_one" }

      assert(new_dep.isEmpty == false)
      assert(new_dep.get.usage.size == 2)
      assert(new_dep.get.usage.head.version == "0.0.0")
      assert(new_dep.get.usage.head.event == "new")
      assert(new_dep.get.usage.last.version == "0.0.1")
      assert(new_dep.get.usage.last.event == "updated")
    }
  }

  test("one new dependency - with error") {
    withSpark(newSparkContext()) { sc =>
      val packages = getPackages(sc, test_source, "simple/new_with_error/package*.json")
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
            Dependency("vows", List(Event("0.6.x", "new", timestamp, commit)))
            //Dependency("new_with_error", List(Event("0.0.0", "new", timestamp2, commit2)))
          )
        )

        println("EXPECTED DEPENDENCIES 4: ")
        expectedResult("d3") foreach println

        println("DEPENDENCIES " + dependencies("d3").size.toString + ": ")
        dependencies("d3") foreach println

      assert(packages.count === 1)
      assert(dependencies.contains("d3") === true)
      assert(dependencies("d3").size === 4)
      expectedResult("d3").foreach { expectedDep =>
        println("Checking " + expectedDep.name + " --> " + dependencies("d3").contains(expectedDep).toString);
        assert(dependencies("d3").contains(expectedDep) === true)
      }
    }
  }

}
