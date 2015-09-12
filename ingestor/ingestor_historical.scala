package com.liberator

// Runs the ingestor over the last N days.

import com.github.nscala_time.time.Imports._
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark._


object IngestorHistorical {

  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("Liberator Ingestor").setMaster("local[2]")
    val sc = new SparkContext(conf)

    println("Running a historical ingest...")
    for (d <- Iterator.range(0,30)) {
      val time = DateTime.yesterday.withTimeAtStartOfDay() - d.days
      Ingestor.run(sc = sc, targetDate = time, save_to_db = true, debug = false)
    }
  }
  
}
