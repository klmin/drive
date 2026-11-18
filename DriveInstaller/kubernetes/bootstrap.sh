#!/bin/sh
set -eu
: "${PGHOST:?PGHOST is required}"
: "${PGPORT:?PGPORT is required}"
: "${PGDATABASE:?PGDATABASE is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"
export PGCONNECT_TIMEOUT=10
export PGAPPNAME=drive-db-bootstrap
cd /bootstrap
echo '[drive-db-init] Connecting and checking initialization.'
# Same connection owns the lock across the original SQL's BEGIN/COMMIT.
exec psql -X -w -q -v ON_ERROR_STOP=1 -v VERBOSITY=terse -f bootstrap.sql
