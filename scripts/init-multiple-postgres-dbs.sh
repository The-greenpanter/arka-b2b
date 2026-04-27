#!/bin/bash
# Crea múltiples DBs dentro de un mismo contenedor PostgreSQL.
# Lee la env var POSTGRES_MULTIPLE_DATABASES y las crea todas.
set -e
set -u

function create_database() {
  local db=$1
  echo "  -> Creando base de datos '$db'"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE $db;
    GRANT ALL PRIVILEGES ON DATABASE $db TO $POSTGRES_USER;
EOSQL
}

if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
  echo "Creando múltiples DBs: $POSTGRES_MULTIPLE_DATABASES"
  for db in $(echo "$POSTGRES_MULTIPLE_DATABASES" | tr ',' ' '); do
    create_database "$db"
  done
  echo "Listo."
fi
