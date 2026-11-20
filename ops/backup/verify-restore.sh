#!/bin/sh
set -eu

backup="${1:?Usage: verify-restore.sh /backups/livraria-YYYYMMDDTHHMMSSZ.dump}"
case "$backup" in /backups/livraria-*.dump) ;; *) echo "Backup must be a managed file under /backups" >&2; exit 2 ;; esac
[ -f "$backup" ] || { echo "Backup not found: $backup" >&2; exit 2; }

temporary_database="livraria_restore_verify_$(date +%s)"
cleanup() { dropdb --if-exists "$temporary_database" >/dev/null 2>&1 || true; }
trap cleanup EXIT INT TERM

createdb "$temporary_database"
pg_restore --exit-on-error --no-owner --no-privileges --dbname="$temporary_database" "$backup"
schema_version="$(psql --dbname="$temporary_database" --tuples-only --no-align --command "SELECT max(version) FROM flyway_schema_history WHERE success = TRUE")"
[ -n "$schema_version" ] || { echo "Restore verification failed: Flyway history was not restored" >&2; exit 1; }
echo "Restauração verificada: Flyway versão $schema_version em banco temporário"
