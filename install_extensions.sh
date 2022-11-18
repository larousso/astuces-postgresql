#!/bin/bash
set -e

   psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
EOSQL

   psql -d movies -c "CREATE EXTENSION IF NOT EXISTS pg_trgm"
   psql -d movies -c "CREATE EXTENSION IF NOT EXISTS unaccent;"



