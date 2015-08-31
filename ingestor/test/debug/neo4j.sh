#!/bin/bash

set -e

# Check Neo4J is installed.
hash neo4j 2>/dev/null || \
	{ echo >&2 "Neo4J not found; is it installed?"; exit 1; }

echo -e "\n*** DEBUG - Neo4J ***"

cd test/debug

neo_dir="$(dirname $(which neo4j))/.."
db_dir="${neo_dir}/data/graph.db"
nodes="$PWD/packages_header.csv"
for f in output/packages.csv/part-*; do
	nodes="${nodes},$PWD/$f"
done
deps="$PWD/dependencies_header.csv"
for f in output/dependencies.csv/part-*; do
	deps="${deps},$PWD/${f}"
done

echo "DB           : ${neo_dir}/data/graph.db"
echo "Nodes        : ${nodes}"
echo "Relationships: ${deps}"
echo ""

rm -rf ${db_dir}

neo4j-import --into "${db_dir}" \
	--id-type STRING \
	--nodes "${nodes}" \
	--relationships "${deps}" --stacktrace

neo4j console
