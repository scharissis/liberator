package com.liberator

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.scalatest.Matchers

import com.liberator.LocalSparkContext._
import com.liberator.PackageJson._



class ReformerSuite extends FunSuite with LocalSparkContext with Matchers {

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

        // Sort the dependency usage events, so the matchers work (sameElementsAs isn't recursive)
        .mapValues( dep_list =>
          dep_list.map( d =>
            Dependency(d.name, d.usage.sortBy( e => e.time ))
          )
        )
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

      assert(packages.count === 1)
      assert(dependencies.contains("d3") === true)
      assert(dependencies("d3").size === 4)
      expectedResult("d3").foreach { expectedDep =>
        assert(dependencies("d3").contains(expectedDep) === true)
      }
    }
  }

  test("multiple packages - one commit each") {
    withSpark(newSparkContext()) { sc =>
      val packages = getPackages(sc, test_source, "simple/multiple/one-commit/*/*/package*.json")
      val dependencies = getDependencies(packages)

      val timestamp_react = "1369857242"  // seconds
      val commit_react = "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"

      val expectedResult : Map[String, List[Dependency]] = Map(

        "react-tools" -> List(
          Dependency("recast", List(Event("~0.3.3", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("esprima", List(Event("git://github.com/facebook/esprima#fb-harmony", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("base62", List(Event("~0.1.1", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("commoner", List(Event("~0.6.8", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("source-map", List(Event("~0.1.22", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("semver", List(Event(">= 1.1.4", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("uglify-js", List(Event("~2.3.6", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("phantomjs", List(Event(">= 1.9.0", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("grunt-contrib-jshint", List(Event("~0.5.4", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("gzip-js", List(Event("~0.3.2", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("grunt-compare-size", List(Event("~0.4.0", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("grunt-contrib-copy", List(Event("~0.4.1", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("grunt-contrib-compress", List(Event("~0.5.1", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("wrapup", List(Event("~0.12.0", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("optimist", List(Event("~0.4.0", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("grunt-contrib-clean", List(Event("~0.4.1", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("grunt", List(Event("~0.4.1", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("browserify", List(Event("~2.14.2", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4")))
        ),

        "react-native" -> List(
          Dependency("esprima-fb", List(Event("7001.0001.0000-dev-harmony-fb", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("through", List(Event("2.3.6", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("connect", List(Event("2.8.3", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("jstransform", List(Event("8.2.0", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("punycode", List(Event("1.2.4", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("module-deps", List(Event("3.5.6", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("yargs", List(Event("1.3.2", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("q", List(Event("1.0.1", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("base62", List(Event("0.1.1", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("absolute-path", List(Event("0.0.0", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("source-map", List(Event("0.1.31", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("react-tools", List(Event("0.12.2", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("path-is-inside", List(Event("1.0.1", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("rebound", List(Event("0.0.10", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("node-static", List(Event("0.7.6", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("mime", List(Event("1.2.11", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("underscore", List(Event("1.7.0", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("stacktrace-parser", List(Event("0.1.1", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("qs", List(Event("0.6.5", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("node-haste", List(Event("1.2.6", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("fs-extra", List(Event("0.15.0", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("sane", List(Event("1.0.1", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("wordwrap", List(Event("0.0.2", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("optimist", List(Event("0.6.1", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("debug", List(Event("~2.1.0", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("worker-farm", List(Event("1.1.0", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("eslint", List(Event("0.9.2", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("jest-cli", List(Event("0.2.1", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12")))
        ),

        "react-relay" -> List(
          Dependency("crc32", List(Event("^0.2.2", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("fbjs", List(Event("0.1.0-alpha.7", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("babel-runtime", List(Event("5.8.20", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("react-static-container", List(Event("^1.0.0-alpha.1", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("react", List(Event("^0.14.0-beta2", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("object-assign", List(Event("^3.0.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("gulp-derequire", List(Event("^2.1.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("gulp-util", List(Event("^3.0.6", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("gulp-header", List(Event("^1.2.2", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("webpack", List(Event("1.11.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("webpack-stream", List(Event("^2.1.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("gulp-babel", List(Event("^5.1.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("gulp-flatten", List(Event("^0.1.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("gulp", List(Event("^3.9.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("run-sequence", List(Event("^1.1.2", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("envify", List(Event("^3.4.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("del", List(Event("^1.2.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("babel-loader", List(Event("5.3.2", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("babel-core", List(Event("5.8.21", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("jest-cli", List(Event("^0.4.16", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4")))
        )
      )

      assert(packages.count === 3)

      dependencies("react-tools") should have size expectedResult("react-tools").size
      dependencies("react-tools") should contain theSameElementsAs expectedResult("react-tools")

      dependencies("react-native") should have size expectedResult("react-native").size
      dependencies("react-native") should contain theSameElementsAs expectedResult("react-native")

      dependencies("react-relay") should have size expectedResult("react-relay").size
      dependencies("react-relay") should contain theSameElementsAs expectedResult("react-relay")
    }
  }

  test("multiple packages - multiple commits each") {
    withSpark(newSparkContext()) { sc =>
      val packages = getPackages(sc, test_source, "simple/multiple/many-commits/*/*/package*.json")
      val dependencies = getDependencies(packages)

      val expectedResult : Map[String, List[Dependency]] = Map(

        "react-tools" -> List(
          // 29 May 2013 19:54:02 GMT
          Dependency("recast", List(
            Event("~0.3.3", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"),
            Event("~0.4.5", "updated", "1370978690", "15360056bdb9299b027740ecf2f96091f0a847cc")  // 11 Jun 2013 19:24:50 GMT
          )),
          Dependency("esprima", List(Event("git://github.com/facebook/esprima#fb-harmony", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("base62", List(Event("~0.1.1", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("commoner", List(
            Event("~0.6.8", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"),
            Event("~0.7.0", "updated", "1370978690", "15360056bdb9299b027740ecf2f96091f0a847cc")  // 11 Jun 2013 19:24:50 GMT
          )),
          Dependency("source-map", List(Event("~0.1.22", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("semver", List(Event(">= 1.1.4", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("uglify-js", List(Event("~2.3.6", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("phantomjs", List(Event(">= 1.9.0", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("grunt-contrib-jshint", List(Event("~0.5.4", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("gzip-js", List(Event("~0.3.2", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("grunt-compare-size", List(Event("~0.4.0", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("grunt-contrib-copy", List(Event("~0.4.1", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("grunt-contrib-compress", List(Event("~0.5.1", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("wrapup", List(Event("~0.12.0", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("optimist", List(Event("~0.4.0", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("grunt-contrib-clean", List(Event("~0.4.1", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("grunt", List(Event("~0.4.1", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("browserify", List(Event("~2.14.2", "new", "1369857242", "75897c2dcd1dd3a6ca46284dd37e13d22b4b16b4"))),
          Dependency("grunt-cli", List(Event("~0.1.9", "new", "1369869469", "12e1bb1daa256fc27eeb957b841a43751be828ab"))),  // 29 May 2013 23:17:49 GMT
          Dependency("tmp", List(
            Event("~0.0.18", "new", "1370010939", "60a6665bbdf5fe5e4c526efe38eb375c54e14aa9"),      // 31 May 2013 14:35:39 GMT
            Event("~0.0.18", "removed", "1370023060", "0c6bbf275bb14a0b37426a5caf3dfd31753d36f3"),  // 31 May 2013 17:57:40 GMT
            Event("~0.0.18", "new", "1370040119", "8d259093bf383a1fbbe69eae0fd5c5f615af252b")       // 31 May 2013 22:41:59 GMT
          ))
        ),

        "react-native" -> List(
          Dependency("esprima-fb", List(
            Event("7001.0001.0000-dev-harmony-fb", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"),
            Event("7001.0001.0000-dev-harmony-fb", "removed", "1425371406", "668c53ab9cd71c98cf43e781fa1cadd6a7332a59")
          )),
          Dependency("through", List(
            Event("2.3.6", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"),
            Event("2.3.6", "removed", "1425371406", "668c53ab9cd71c98cf43e781fa1cadd6a7332a59")
          )),
          Dependency("connect", List(Event("2.8.3", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("jstransform", List(
            Event("8.2.0", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"),
            Event("10.0.1", "updated", "1425690773", "582c05f4a04040b09b380cc74ea4b7b37bda7ec2")
          )),
          Dependency("punycode", List(
            Event("1.2.4", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"),
            Event("1.2.4", "removed", "1425371406", "668c53ab9cd71c98cf43e781fa1cadd6a7332a59")
          )),
          Dependency("module-deps", List(Event("3.5.6", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("yargs", List(Event("1.3.2", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("q", List(Event("1.0.1", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("base62", List(
            Event("0.1.1", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"),
            Event("0.1.1", "removed", "1425371406", "668c53ab9cd71c98cf43e781fa1cadd6a7332a59")
          )),
          Dependency("absolute-path", List(Event("0.0.0", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("source-map", List(Event("0.1.31", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("react-tools", List(
            Event("0.12.2", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"),
            Event("0.13.0-rc2", "updated", "1425690773", "582c05f4a04040b09b380cc74ea4b7b37bda7ec2")
          )),
          Dependency("path-is-inside", List(
            Event("1.0.1", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"),
            Event("1.0.1", "removed", "1425371406", "668c53ab9cd71c98cf43e781fa1cadd6a7332a59")
          )),
          Dependency("rebound", List(
            Event("0.0.10", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"),
            Event("0.0.10", "removed", "1425371406", "668c53ab9cd71c98cf43e781fa1cadd6a7332a59")
          )),
          Dependency("node-static", List(
            Event("0.7.6", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"),
            Event("0.7.6", "removed", "1425371406", "668c53ab9cd71c98cf43e781fa1cadd6a7332a59")
          )),
          Dependency("mime", List(
            Event("1.2.11", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"),
            Event("1.2.11", "removed", "1425371406", "668c53ab9cd71c98cf43e781fa1cadd6a7332a59")
          )),
          Dependency("underscore", List(Event("1.7.0", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("stacktrace-parser", List(Event("0.1.1", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("qs", List(
            Event("0.6.5", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"),
            Event("0.6.5", "removed", "1425371406", "668c53ab9cd71c98cf43e781fa1cadd6a7332a59")
          )),
          Dependency("node-haste", List(
            Event("1.2.6", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"),
            Event("1.2.6", "removed", "1425371406", "668c53ab9cd71c98cf43e781fa1cadd6a7332a59")
          )),
          Dependency("fs-extra", List(
            Event("0.15.0", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"),
            Event("0.15.0", "removed", "1425371406", "668c53ab9cd71c98cf43e781fa1cadd6a7332a59")
          )),
          Dependency("sane", List(Event("1.0.1", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("wordwrap", List(
            Event("0.0.2", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"),
            Event("0.0.2", "removed", "1425371406", "668c53ab9cd71c98cf43e781fa1cadd6a7332a59")
          )),
          Dependency("optimist", List(Event("0.6.1", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("debug", List(Event("~2.1.0", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("worker-farm", List(Event("1.1.0", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("eslint", List(Event("0.9.2", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("jest-cli", List(Event("0.2.1", "new", "1424409911", "efae175a8e1b05c976cc5a1cbd492da71eb3bb12"))),
          Dependency("joi", List(Event("~5.1.0", "new", "1424725895", "00553c6d060ae0c78e90bfceb0f1f971b363199a"))),
          Dependency("uglify-js", List(Event("~2.4.16", "new", "1425359401", "bb7040808e259dc782403af5debb9ab94deb44f6")))
        ),

        "react-relay" -> List(
          Dependency("crc32", List(Event("^0.2.2", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("fbjs", List(Event("0.1.0-alpha.7", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("babel-runtime", List(Event("5.8.20", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("react-static-container", List(Event("^1.0.0-alpha.1", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("react", List(
            Event("^0.14.0-beta2", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"),
            Event("^0.14.0-beta3", "updated", "1439398605", "7448d5ce0117a90a55b4632186b8e7efbff093ad"),
            Event("^0.14.0-beta2", "updated", "1439409180", "c495d68eb67e3848f203db081721681f3854cbd0"),
            Event("^0.14.0-beta3", "updated", "1439412931", "e729d8024e4ef838d7b26c9971bb00e67f29eae3")
          )),
          Dependency("object-assign", List(Event("^3.0.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("gulp-derequire", List(Event("^2.1.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("gulp-util", List(Event("^3.0.6", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("gulp-header", List(Event("^1.2.2", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("webpack", List(Event("1.11.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("webpack-stream", List(Event("^2.1.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("gulp-babel", List(Event("^5.1.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("gulp-flatten", List(Event("^0.1.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("gulp", List(Event("^3.9.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("run-sequence", List(Event("^1.1.2", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("envify", List(Event("^3.4.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("del", List(Event("^1.2.0", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("babel-loader", List(Event("5.3.2", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("babel-core", List(Event("5.8.21", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("jest-cli", List(Event("^0.4.16", "new", "1439320973", "2a86be3e71cdc6511fa994e3de539f72070da1b4"))),
          Dependency("babel-relay-plugin", List(Event("^0.1.2", "new", "1439510620", "353b1077c88df81910859fd1996bf83276563336")))
        )

      )

      assert(packages.count === 3)

      dependencies("react-tools") should have size expectedResult("react-tools").size
      dependencies("react-tools") should contain theSameElementsAs expectedResult("react-tools")

      dependencies("react-native") should have size expectedResult("react-native").size
      dependencies("react-native") should contain theSameElementsAs expectedResult("react-native")

      dependencies("react-relay") should have size expectedResult("react-relay").size
      dependencies("react-relay") should contain theSameElementsAs expectedResult("react-relay")
    }
  }

}
