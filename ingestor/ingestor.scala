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

// Input: Files containing Lists of RepDep files.
// Output: Files containing number of dependencies per package.
// TODO: Add timestamp to graph edges and output accordingly.
object Ingestor {
  Class.forName("org.postgresql.Driver")
  val db_name="liberator_test"
  val db_url = "localhost:5432"
  val db_jdbc = "jdbc:postgresql://"+ db_url + "/" + db_name
  val db_username = "liberator"
  val db_password = "liberator"

  // Transform strings for consistent hashing.
  def sanitiseString(s: String): String = {
    s.toLowerCase.replace(" ", "").replace("-", "")
  }

  // Generate a pseudo-Unique VertexId of type Long.
  def hash(s: String): Long = { sanitiseString(s).hashCode.toLong }

  // Define a Package format.
  implicit val formats = org.json4s.DefaultFormats
  case class Event(version: String, event: String, time: String, commit: String)
  case class Dependency(name: String, usage: List[Event])
  case class Package(name: String, source: String, dependencies: List[Dependency])

  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("Liberator Ingestor")
    val sc = new SparkContext(conf)
    val input_files = "../reformer/output/part-*"
    val output_file = "output"
    val targetDate = DateTime.yesterday.withTimeAtStartOfDay()

    // Read & Parse JSON files into Packages.
    // TODO: Ignore empty files needed?
    val packages: org.apache.spark.rdd.RDD[Package] = sc.wholeTextFiles(input_files)
      .filter{ case (filename,filecontent) => filecontent != "" } // Skip empty files.
      .map(json  =>  { parse(json._2) })                          // Discard filename.
      .map(json  =>  json.extract[ List[Package] ])
      .flatMap(identity)
      .cache

    // Generate Vertex RDD.
    val vertices: VertexRDD[String] = VertexRDD(
      packages.flatMap(p => { p.dependencies.map( d => { (hash(d.name), d.name)}) })
        .union(packages.map(p => { (hash(p.name), p.name) }))
        .distinct
    )

    // Generate Edge RDD.
    // Define: connection(a,b) => 'b is a dependency of a'.
    val edges: org.apache.spark.rdd.RDD[Edge[String]] = for {
        pac <- packages
        dep <- pac.dependencies
        event <- dep.usage
    } yield { Edge(hash(pac.name), hash(dep.name), event.time ) }

    // Build a directed multi-Graph.
    val graph: Graph[String, String] = Graph(vertices, edges)

    // Neo4J Debug Graph
    // TODO: Enable with a '--debug' flag of some sort.
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

    // Obtain dependency subgraph.
    // TODO: Rounding errors in time conversion from milliseconds to seconds.
    val subgraph = graph.subgraph(
      //vpred = (verexId,vd) => vd == "grunt",
      epred = e =>
        (e.attr.toLong >= targetDate.getMillis()/1000 && e.attr.toLong <= (targetDate + 1.days).getMillis()/1000)
    ).cache()

    // Get the inDegree RDD, including the zeroes,
    val inDegreeGraph = graph.vertices.leftJoin(graph.inDegrees) {
      (vid, attr, inDegOpt) => inDegOpt.getOrElse(0)
    }

    // Translate vertexId's back to String's.
    val result = subgraph.vertices.innerJoin(inDegreeGraph){
      (id, name, indegree) => (name, indegree)
    }.map{ case (id, (name, indegree)) => (name, indegree) }.filter{ x => x._1 != "null"}

    // Write result to file.
    result.saveAsTextFile(output_file)

    // Write result to DB.
    result.foreachPartition { (partition) =>
      partition.foreach { case (name, count) =>
        using (DB (DriverManager .getConnection (db_jdbc, db_username, db_password))) {db =>
          db.localTx { implicit session =>
            sql"""
             insert into liberator_nodejs (package_id, usage_date, usage_count)
             values (${name}, current_timestamp, ${count})
             """
             .update.apply()
          }
        }
      }
    }
    println("Completed ingestion for date: " + DateTimeFormat.forPattern("yyyy-MM-dd").print(targetDate))
  }
}
