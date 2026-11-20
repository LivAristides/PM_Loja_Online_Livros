#!/bin/sh
set -eu

interval="${BACKUP_INTERVAL_SECONDS:-86400}"
case "$interval" in
  ''|*[!0-9]*|0) echo "BACKUP_INTERVAL_SECONDS must be a positive integer" >&2; exit 2 ;;
esac

while true; do
  /usr/local/bin/backup-once.sh
  sleep "$interval"
done
