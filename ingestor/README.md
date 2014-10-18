Ingestor
=
Ingests raw JSON files representing repositories, processes this as a graph and
then outputs pertinent data.


Dependencies
==
* Scala <br/>
  `sudo apt-get install scala`

* Scala SBT <br/>
  http://www.scala-sbt.org/download.html

* Apache Spark <br/>


Build
==
`sbt package`


Run
==
`spark-submit --class "Ingestor" --master local[2] target/scala-2.10/ingestor_2.10-1.0.jar`
