Ingestor
=
Ingests normalised Liberator Package files (as generated by the Normaliser),
which represent repositories, processes this as a graph and then outputs the
number of dependencies for each package.

Dependencies
==
* Scala (Tested with 2.10.5)<br/>
  `sudo apt-get install scala`

* Scala SBT <br/>
  http://www.scala-sbt.org/download.html

* SBT 'Assembly Plugin'
  https://github.com/sbt/sbt-assembly

* Java 7 (Tested with OpenJDK 7)

* Apache Spark (Tested with 1.5)<br/>

* Neo4J (v2.2.0+) (optional; for development debugging only)

Build
==

First time setup script:

```
bash db/setup.sh
```

Regular workflow:

```
make
```

This will build a fat JAR (one with all dependencies in it), re-create the database tables and run the Ingestor.

```
make debug
```
Same as `make`, but will then also run a Neo4J server and import the graph into it for visualisation. Open your browser at http://localhost:7474 to view.


Run
==
It is presently assumed that all input files are located in folder test: "test/*.json".

To run:

`spark-submit --class "Ingestor" --master local[2] target/scala-2.10/ingestor_2.10-1.0.jar`

The output will be in output/part-*:
`cat output/part-*`
