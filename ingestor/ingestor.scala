package com.liberator

import org.json4s._
import org.json4s.jackson.JsonMethods._

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD

import com.github.nscala_time.time.Imports._
import java.sql.{Connection, DriverManager, ResultSet}
import scalikejdbc._

import com.liberator.PackageJson._

// Input: Files containing Lists of RepDep files.
// Output: Files containing number of dependencies per package.
object Ingestor {
  Class.forName("org.postgresql.Driver")
  val db_name="liberator_test"
  val db_url = "localhost:5432"
  val db_jdbc = "jdbc:postgresql://"+ db_url + "/" + db_name
  val db_username = "liberator"
  val db_password = "liberator"

  implicit val formats = org.json4s.DefaultFormats

  val infinity = Int.MaxValue

  // Transform strings for consistent hashing.
  def sanitiseString(s: String): String = {
    s.toLowerCase.replace(" ", "").replace("-", "")
  }

  // Generate a pseudo-Unique VertexId of type Long.
  def hash(s: String): Long = { sanitiseString(s).hashCode.toLong }

  def run(
    sc : SparkContext,
    source:String = "../reformer/output",
    file_regex:String = "/part-*",
    startDate:DateTime = DateTime.yesterday.withTimeAtStartOfDay(),
    days_back:Int = 3,
    output_dir:String = "",
    save_to_db:Boolean = true,
    debug:Boolean = false) : org.apache.spark.rdd.RDD[(String, Int)] = {

    println("Ingesting from " + startDate + " for last " + days_back.toString + " days...")

    // Read & Parse JSON files into Packages.
    // TODO: Ignore empty files needed?
    val packages: org.apache.spark.rdd.RDD[PackageJson] = sc
      .wholeTextFiles(source + file_regex)
      .filter{ case (filename,filecontent) => filecontent != "" } // Skip empty files.
      .map(json  =>  { parse(json._2) })                          // Discard filename.
      .map(json  =>  json.extract[ PackageJson ])
      .cache

    // Generate Vertex RDD.
    val vertices: VertexRDD[String] = VertexRDD(
      packages.flatMap(p => { p.dependencies.map( d => { (hash(d.name), d.name)}) })
        .union(packages.map(p => { (hash(p.name), p.name) }))
        .distinct
    )

    if (debug) {
      println("VERTICES: " + vertices.count)
      println("VERTICES: " + vertices.collect)

      println("PACKAGES: ")
      for {
          pac <- packages
      } if ( pac.dependencies.size > -1) println(" - " + pac.name + ": " + pac.dependencies.size + " dependencies.")

      println("DEPENDENCIES: ")
      for {
          pac <- packages
          dep <- pac.dependencies
      } println(pac.name + " --> " + dep.name)

      println("USAGES: ")
      for {
          pac <- packages
          dep <- pac.dependencies
      } if ( dep.usage.size > 0) println(" - " + dep.name + ": " + dep.usage.size + " usages.")

      println("PAIRS: ")
      for {
          pac <- packages
          dep <- pac.dependencies
          pair <- dep.usage.zip(dep.usage.tail)  // empty if dep.usage.count < 2
      } println("pair: " + pair) // Pair(e1,e2)
    }

    // Generate Edge RDD.
    // Define: connection(a,b) => 'b is a dependency of a'.
    val edges_tmp: org.apache.spark.rdd.RDD[Triple[PackageJson,Dependency,List[Range]]] = for {
        pac <- packages
        dep <- pac.dependencies
        Pair(e1,e2) <- dep.usage.size match {
          case 1 => List((dep.usage.head, dep.usage.head))  // hack; (new,new) won't match a case below.
          case _ => dep.usage.zip(dep.usage.tail)
        }
    } yield {
      val a: String = e1.event
      val b: String = e2.event
      val t1: Int = e1.time.toInt
      val t2: Int = e2.time.toInt
      (a,b) match {
        case ("new", "updated") => Triple(pac, dep, List(Range(t1,t2), Range(t2,infinity)))
        case ("new", "removed") => Triple(pac, dep, List(Range(t1,t2)))
        case ("updated", "updated") => Triple(pac, dep, List(Range(t1,t2), Range(t2,infinity)))
        case ("updated", "removed") => Triple(pac, dep, List(Range(t1,t2)))
        case ("removed", "removed") => Triple(pac, dep, List(Range(t2,infinity)))
        case _ => Triple(pac, dep, List(Range(t1,infinity)))
      }
    }

    val edges_tmp_stitched: org.apache.spark.rdd.RDD[Triple[PackageJson,Dependency,Range]] =
      for {
        (pkg, dep, ranges) <- edges_tmp
        (range, index) <- ranges.zipWithIndex
    } yield (pkg, dep, range.size match {
        case 0 => Range(0,0)
        case _ => range.last match {
          case `infinity` =>
            if (index+1 < ranges.size-1){  // not the last
              println("Ranges.size: " + ranges.size + " | index: " + (index+1));
              Range(range.head, ranges(index+1).head)
            } else { range }
          case _ => range
          }
        }
      )

    val edges: org.apache.spark.rdd.RDD[Edge[Range]] = edges_tmp_stitched
      .map { case (pkg, dep, range) =>
        Edge(hash(pkg.name), hash(dep.name), range )
      }

    // Build a directed multi-Graph.
    val graph: Graph[String, Range] = Graph(vertices, edges)
    graph.vertices.cache()

    if (debug) {
      // Neo4J Debug Graph
      // Vertices/Nodes
      // Format: :ID,:LABEL,name
      graph.vertices
        .map{ case (id, name) => Array(id, "package", name).mkString(",") }
        .saveAsTextFile("test/debug/output/packages.csv")
      // Edges/Relationships
      // Format: :START_ID,:END_ID,:TYPE
      graph.triplets
        .map( triplet => Array(hash(triplet.srcAttr), hash(triplet.dstAttr), "dep").mkString(",") )
        .saveAsTextFile("test/debug/output/dependencies.csv")
    }

    var target_date_result: org.apache.spark.rdd.RDD[(String, Int)] =
      sc.parallelize(List(("error",0)))

    // Create a subgraph and ingest, going back N days.
    for (days <- Iterator.range(0,days_back)) {
      val targetDate = startDate - days.days
      val targetDateEpoch = targetDate.getMillis()/1000
      println("Ingesting for date: " +
        DateTimeFormat.forPattern("yyyy-MM-dd").print(targetDate) +
        " (" + targetDateEpoch + ")"
      )

      // Obtain dependency subgraph.
      val subgraph = graph.subgraph(
        // Example vertex predicate:  vpred = (verexId,vd) => vd == "grunt",
        epred = e => e.attr.contains(targetDateEpoch)
      ).cache()

      if (debug) {
        println("The graph size: " + graph.vertices.count + " | " + graph.edges.count)
        println("Subgraph size: " + subgraph.vertices.count + " verts | " + subgraph.edges.count + " edges")
      }

      // Get the inDegree RDD, including the zeroes,
      val inDegreeGraph = graph.vertices.leftJoin(subgraph.inDegrees) {
        (vId, attr, inDegOpt) => inDegOpt.getOrElse(0)
      }

      // Translate vertexId's back to String's.
      val result: org.apache.spark.rdd.RDD[(String, Int)] = subgraph.vertices.innerJoin(inDegreeGraph){
        (id, name, indegree) => (name, indegree)
      }.map{ case (id, (name, indegree)) => (name, indegree) }.filter{ x => x._1 != "null"}

      if (days==0) target_date_result = result

      if (debug) {
        println("SUBGRAPH.vertices:")
        subgraph.vertices.foreach { println }
        println("inDegreeGraph:")
        inDegreeGraph.foreach { println }
        println("RESULT:")
        result.foreach { println }
      }

      // Write result to file.
      if (output_dir != ""){
        // TODO: Multi-day write to file.
        result.saveAsTextFile(output_dir)
      }

      // Write result to DB.
      if (save_to_db == true){
        result.foreachPartition { (partition) =>
          partition.foreach { case (name, count) =>
            using (DB (DriverManager .getConnection (db_jdbc, db_username, db_password))) {db =>
              db.localTx { implicit session =>
                sql"""
                 insert into liberator_nodejs (package_id, usage_date, usage_count)
                 values (${name}, ${targetDate}, ${count})
                 """
                 .update.apply()
              }
            }
          }
        }
      }

      if (debug) {
        println("Completed ingestion for timestamp: " +
          DateTimeFormat.forPattern("yyyy-MM-dd").print(targetDate) +
          " (" + targetDateEpoch + ")"
        )
      }
    }
    target_date_result
  }

  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("Liberator Ingestor").setMaster("local[2]")
    val sc = new SparkContext(conf)

    val _ = run(sc, output_dir = "", days_back = 21, debug = false)
  }
}
