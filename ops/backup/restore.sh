#!/bin/sh
set -eu

backup="${1:?Usage: restore.sh /backups/livraria-YYYYMMDDTHHMMSSZ.dump}"
case "$backup" in /backups/livraria-*.dump) ;; *) echo "Backup must be a managed file under /backups" >&2; exit 2 ;; esac
[ -f "$backup" ] || { echo "Backup not found: $backup" >&2; exit 2; }

# O banco alvo precisa estar vazio e ser selecionado explicitamente pelo operador.
: "${RESTORE_DATABASE:?Set RESTORE_DATABASE to the empty target database name}"
case "$RESTORE_DATABASE" in *[!A-Za-z0-9_]*|'') echo "Invalid RESTORE_DATABASE" >&2; exit 2 ;; esac

pg_restore --exit-on-error --clean --if-exists --no-owner --no-privileges --dbname="$RESTORE_DATABASE" "$backup"
echo "Restauração concluída em $RESTORE_DATABASE"
