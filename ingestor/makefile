default: build db
	spark-submit \
		--class "Ingestor" --master local[2] \
		target/scala-2.10/Ingestor-assembly-1.0.jar \
		--jar lib/postgresql-9.3-1102-jdbc41.jar
			
build: clean
	sbt assembly 

db:
	PGPASSWORD=liberator psql -h 127.0.0.1 -a -f create_tables.sql liberator_test liberator

report: default
	PGPASSWORD=liberator psql -h 127.0.0.1 -a -f get_data.sql liberator_test liberator

clean:
	rm -rf output