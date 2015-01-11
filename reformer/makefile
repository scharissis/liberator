default: build
	spark-submit --class "Reformer" --master local[2] target/scala-2.10/reformer_2.10-1.0.jar

build: clean
	sbt package

clean:
	rm -rf output
