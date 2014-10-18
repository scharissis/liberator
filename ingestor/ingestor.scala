import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD

// Reads a file 'README.md' and outputs the number of lines containing 'b' & 's'.
object Ingestor {
  def main(args: Array[String]) {
    val logFile = "README.md"
    val conf = new SparkConf().setAppName("Liberator Ingestor")
    val sc = new SparkContext(conf)
    val logData = sc.textFile(logFile, 2).cache()
    val numBs = logData.filter(line => line.contains("b")).count()
    val numSs = logData.filter(line => line.contains("s")).count()
    println("Lines with b: %s, Lines with s: %s".format(numBs, numSs))
  }
}
