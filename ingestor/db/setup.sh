#!/bin/bash

USER=liberator
PASS=liberator
DB=liberator
HOST=127.0.0.1

# Create user
sudo -u postgres bash -c "psql -c \"CREATE USER ${USER} WITH PASSWORD '${PASS}' SUPERUSER;\""

# Create database
PGPASSWORD=${PASS} createdb -h ${HOST} -U ${USER} -O ${USER} ${DB}
