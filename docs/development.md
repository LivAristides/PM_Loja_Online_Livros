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

## Estrutura

- `backend/`: Spring Boot e futuras migrações Flyway.
- `frontend/`: React/TypeScript e Vite.
- `compose.yaml`: serviços locais reproduzíveis.
- `.github/workflows/ci.yml`: build e verificações básicas a cada push/PR.
