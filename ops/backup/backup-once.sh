#!/bin/sh
set -eu

: "${PGHOST:?PGHOST is required}"
: "${PGDATABASE:?PGDATABASE is required}"
: "${PGUSER:?PGUSER is required}"
retention_days="${BACKUP_RETENTION_DAYS:-7}"

case "$retention_days" in
  ''|*[!0-9]*|0) echo "BACKUP_RETENTION_DAYS must be a positive integer" >&2; exit 2 ;;
esac

mkdir -p /backups
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
temporary="/backups/.livraria-${timestamp}.dump.tmp"
backup="/backups/livraria-${timestamp}.dump"

umask 077
pg_dump --format=custom --no-owner --no-privileges --file="$temporary"
mv "$temporary" "$backup"

# A limpeza só acontece depois que o dump atual foi concluído com sucesso.
find /backups -type f -name 'livraria-*.dump' -mtime "+$((retention_days - 1))" -delete
echo "Backup concluído: $(basename "$backup")"
