Ingestor
=
Ingests raw JSON files representing repositories, processes this as a graph and
then outputs the number of dependencies for each package.

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
It is presently assumed that all input files are located in folder test: "test/*.json".

To run:

`spark-submit --class "Ingestor" --master local[2] target/scala-2.10/ingestor_2.10-1.0.jar`

The output will be in output/part-*:
`cat output/part-*`
