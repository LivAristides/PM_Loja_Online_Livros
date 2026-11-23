# Release e demonstração reproduzível

## Pré-requisitos

- Docker Desktop com Docker Compose ativo;
- Node.js 22 e npm, somente para as verificações do frontend fora do Docker;
- JDK 21 e Maven 3.9+, ou a imagem Maven documentada em `docs/development.md`, para a suíte de integração do backend.

## Subida local limpa

1. Copie `.env.example` para `.env` sem versioná-lo.
2. Defina um valor aleatório local para `PAYMENT_WEBHOOK_SECRET`. Não use segredo real.
3. Caso a porta padrão esteja ocupada, altere apenas a porta publicada, por exemplo `POSTGRES_PORT=5433`.
4. Execute `docker compose up -d --build`.
5. Confirme o estado dos serviços com `docker compose ps`. O PostgreSQL deve ficar `healthy`; backend, frontend, Mailpit e backup devem estar em execução.

Com os valores padrão, a demonstração fica disponível em:

- Loja: `http://localhost:5173`;
- Health do backend: `http://localhost:8080/actuator/health`;
- OpenAPI: `http://localhost:8080/v3/api-docs`;
- Mailpit: `http://localhost:8025`.

O banco inicia apenas com migrações Flyway e dados técnicos de demonstração sem dados pessoais. Para a demonstração, crie os dados fictícios no fluxo normal da aplicação; não use dados reais, dumps ou credenciais de terceiros.

## Gates locais

```sh
cd frontend
npm run lint
npm run build
```

```sh
cd backend
mvn -Pintegration verify
```

O workflow `.github/workflows/ci.yml` repete o `mvn verify` do backend e o lint/build do frontend em cada push e pull request. A suíte de integração local requer o PostgreSQL do Compose conforme `docs/development.md`.

## Backup e restauração

Gere um backup e valide uma restauração não destrutiva:

```sh
docker compose run --rm backup /usr/local/bin/backup-once.sh
docker compose run --rm backup sh -c 'latest=$(ls -1t /backups/livraria-*.dump | head -n 1); /usr/local/bin/verify-restore.sh "$latest"'
```

O segundo comando restaura em um banco temporário e o remove após conferir o histórico do Flyway. O procedimento completo de recuperação e retenção está em `docs/operations/backup-and-restore.md`.

## Encerramento

Ao terminar, execute `docker compose down`. Os volumes permanecem para a próxima execução; use um projeto/volume novo quando precisar de uma demonstração integralmente limpa.
