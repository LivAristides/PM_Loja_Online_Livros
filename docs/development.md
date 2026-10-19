# Desenvolvimento local

## Pré-requisitos

Docker Desktop com Compose é o caminho suportado para esta fase. Copie `.env.example` para `.env` e altere somente valores locais.

## Subir o ambiente

```bash
docker compose up --build
```

Serviços previstos: frontend em `http://localhost:5173`, backend em `http://localhost:8080/actuator/health`, PostgreSQL em `localhost:5432` e Mailpit em `http://localhost:8025`.

## Execução fora do Docker

O backend requer JDK 21 e Maven 3.9+. O frontend requer Node.js 22 e npm funcional. Variáveis de ambiente usam os valores exemplificados em `.env.example`; nunca adicione credenciais reais ao repositório.

## Persistência e migrações

O Flyway executa as migrações versionadas em `backend/src/main/resources/db/migration/` durante a inicialização do backend. A migração inicial cria apenas a tabela técnica `application_metadata` e dois marcadores de demonstração sem dados pessoais, credenciais ou entidades de negócio.

Para executar a verificação de conexão e migração contra o PostgreSQL do Compose, use um ambiente com JDK 21/Maven ou o contêiner Maven na rede do Compose:

```bash
mvn -Pintegration verify
```

As entidades de catálogo, clientes, pedidos e demais dados de domínio serão criadas somente nas TASKs correspondentes.

## Estrutura

- `backend/`: Spring Boot e futuras migrações Flyway.
- `frontend/`: React/TypeScript e Vite.
- `compose.yaml`: serviços locais reproduzíveis.
- `.github/workflows/ci.yml`: build e verificações básicas a cada push/PR.
