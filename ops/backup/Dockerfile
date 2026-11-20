FROM postgres:17-alpine

COPY backup-once.sh backup-scheduler.sh restore.sh verify-restore.sh /usr/local/bin/
RUN chmod 0555 /usr/local/bin/backup-once.sh /usr/local/bin/backup-scheduler.sh /usr/local/bin/restore.sh /usr/local/bin/verify-restore.sh

CMD ["/usr/local/bin/backup-scheduler.sh"]
