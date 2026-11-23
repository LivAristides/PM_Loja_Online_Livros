# Backup, restauração e retenção local

## Política do MVP

O serviço `backup` do Docker Compose cria um dump PostgreSQL no formato customizado assim que inicia e depois a cada 24 horas. Os arquivos ficam no volume nomeado `backup-data`; o banco de produção local e as capas não são apagados por essa rotina.

`BACKUP_RETENTION_DAYS` controla somente o expurgo de dumps concluídos. O valor padrão é `7`; a limpeza ocorre **após** um backup novo ser gerado com sucesso. Dados de pedidos, auditoria, devoluções, estoque ou usuários não são removidos por esta automação.

| Indicador | Meta do MVP | Evidência |
|---|---:|---|
| RPO | até 24 horas | backup inicial + agendamento diário |
| RTO | até 4 horas | procedimento de restauração abaixo, com teste em banco temporário |
| Retenção de dumps | 7 dias | `BACKUP_RETENTION_DAYS=7` |

## Operação local

1. Copie `.env.example` para `.env` e mantenha segredos reais fora do Git. Se a porta `5432` já estiver em uso na máquina, altere somente `POSTGRES_PORT` (por exemplo, para `5433`).
2. Inicie a infraestrutura: `docker compose up -d`.
3. Para gerar um dump sob demanda, sem esperar o agendamento: `docker compose run --rm backup /usr/local/bin/backup-once.sh`.
4. Liste os dumps: `docker compose run --rm backup sh -c 'ls -lh /backups'`.

Os nomes seguem `livraria-AAAAMMDDTHHMMSSZ.dump`. Não copie dumps com dados reais para repositórios, e-mail ou armazenamento público.

## Teste de restauração não destrutivo

Depois de gerar um dump, execute o teste em um banco temporário, que é removido ao final:

```sh
docker compose run --rm backup sh -c 'latest=$(ls -1t /backups/livraria-*.dump | head -n 1); /usr/local/bin/verify-restore.sh "$latest"'
```

O comando restaura no banco `livraria_restore_verify_<timestamp>`, confirma que o histórico do Flyway existe e remove apenas esse banco temporário. Ele não modifica o banco `livraria` em uso.

## Restauração operacional

Restaure somente em um banco vazio e selecionado explicitamente. Antes, pare os consumidores da aplicação e preserve o volume atual para investigação.

```sh
docker compose exec -e RESTORE_DATABASE=livraria_recovered backup \
  /usr/local/bin/restore.sh /backups/livraria-AAAAMMDDTHHMMSSZ.dump
```

Crie `livraria_recovered` antes do comando e faça as verificações funcionais necessárias. A substituição do banco principal exige uma janela operacional e um backup adicional validado; não é automatizada para evitar perda de dados.
