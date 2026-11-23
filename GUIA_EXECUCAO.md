# Guia completo de instalação e execução — Livraria Online

Este guia permite instalar, configurar, executar e validar a aplicação localmente. O caminho recomendado é o Docker Compose: ele inicia frontend, backend, PostgreSQL, Mailpit e rotina de backup em um único comando.

> Os comandos da seção principal são para o **Prompt de Comando do Windows (cmd)**, identificado por um prompt como `C:\\...>`. Não copie as barras `\` que alguns aplicativos exibem no fim de linhas.

## 1. Pré-requisitos

### Obrigatório para executar a aplicação

Instale o [Docker Desktop](https://www.docker.com/products/docker-desktop/) e confirme que ele está aberto e em execução. O Docker Desktop já inclui o Docker Compose.

Valide no cmd:

```bat
docker --version
docker compose version
```

### Opcional: executar testes fora do Docker

Para os testes e builds diretamente na máquina, instale:

| Ferramenta | Versão esperada | Verificação |
|---|---:|---|
| Node.js | 22 | `node --version` |
| npm | incluído com Node.js | `npm --version` |
| JDK | 21 | `java --version` |
| Maven | 3.9 ou superior | `mvn --version` |

Não é necessário instalar PostgreSQL, Java, Maven ou Node.js para apenas subir o sistema via Docker.

## 2. Abrir o projeto

No Prompt de Comando, acesse a pasta do projeto:

```bat
cd "C:\Users\livia\Documents\ChatGPT\PM_Loja_Livros"
```

## 3. Criar a configuração local

Crie o arquivo `.env` a partir do modelo. Ele contém configurações locais e **não deve ser enviado ao Git**.

```bat
copy .env.example .env
notepad .env
```

No arquivo aberto, preencha pelo menos esta variável com um valor local aleatório:

```dotenv
PAYMENT_WEBHOOK_SECRET=troque-por-um-segredo-local-aleatorio
```

Mantenha os demais valores padrão, salvo se houver conflito de portas. Caso a porta PostgreSQL `5432` esteja ocupada, altere somente esta linha:

```dotenv
POSTGRES_PORT=5433
```

Configuração padrão completa:

```dotenv
POSTGRES_DB=livraria
POSTGRES_USER=livraria_app
POSTGRES_PASSWORD=change-me-local-only
POSTGRES_PORT=5432
MAILPIT_UI_PORT=8025
FRONTEND_PORT=5173
BACKEND_PORT=8080
PAYMENT_WEBHOOK_SECRET=troque-por-um-segredo-local-aleatorio
BACKUP_RETENTION_DAYS=7
BACKUP_INTERVAL_SECONDS=86400
```

Os valores são próprios para desenvolvimento. Não use credenciais reais, dados pessoais ou chaves de serviços externos.

## 4. Iniciar a aplicação

Com o Docker Desktop aberto e a configuração concluída, execute:

```bat
docker compose up -d --build
```

Na primeira execução o Docker baixa imagens e cria os containers, o que pode levar alguns minutos. Verifique o resultado:

```bat
docker compose ps
```

O resultado esperado é:

- `livraria-online-postgres-1` com estado `healthy`;
- `livraria-online-backend-1`, `livraria-online-frontend-1`, `livraria-online-mailpit-1` e `livraria-online-backup-1` em execução;
- portas publicadas para frontend (`5173`) e backend (`8080`).

## 5. Acessar e validar os serviços

Abra no navegador:

| Serviço | Endereço |
|---|---|
| Loja | `http://localhost:5173` |
| Health check do backend | `http://localhost:8080/actuator/health` |
| Documento OpenAPI | `http://localhost:8080/v3/api-docs` |
| Interface Swagger | `http://localhost:8080/swagger-ui.html` |
| Caixa de e-mails de teste (Mailpit) | `http://localhost:8025` |

Ou valide pelo cmd:

```bat
curl.exe -i http://localhost:8080/actuator/health
curl.exe -I http://localhost:5173/
curl.exe -I http://localhost:8080/v3/api-docs
```

O primeiro comando deve retornar `HTTP/1.1 200` e `{"status":"UP"}`. Os demais devem retornar status `200`.

## 6. Dados de demonstração

O banco começa com o esquema Flyway e dados técnicos sem informações pessoais. Para demonstrar o sistema, crie usuários, catálogo e pedidos fictícios pela própria interface ou API. Não use informações reais de clientes, cartões, endereços ou senhas.

As migrações Flyway são aplicadas automaticamente quando o backend inicia. Elas ficam em:

```text
backend\src\main\resources\db\migration\
```

## 7. Executar testes e builds locais

Esses comandos exigem Node.js, JDK e Maven instalados conforme a seção 1.

### Frontend

```bat
cd frontend
npm install
npm run lint
npm run build
cd ..
```

### Backend

Com o PostgreSQL do Compose em execução:

```bat
cd backend
set DB_PORT=5432
mvn verify
mvn -Pintegration verify
cd ..
```

O perfil `integration` executa os testes de integração (`*IT.java`) contra o banco configurado. Se você alterou `POSTGRES_PORT` para `5433`, use `set DB_PORT=5433` antes dos comandos Maven.

## 8. Backup e teste de restauração

O serviço `backup` cria um dump ao iniciar e repete o processo diariamente. A retenção padrão é de sete dias.

Crie um backup sob demanda:

```bat
docker compose run --rm backup /usr/local/bin/backup-once.sh
```

Liste os arquivos de backup:

```bat
docker compose run --rm backup sh -c "ls -1t /backups/livraria-*.dump"
```

Copie o nome exibido e valide a restauração não destrutiva. Exemplo:

```bat
docker compose run --rm backup /usr/local/bin/verify-restore.sh /backups/livraria-AAAAMMDDTHHMMSSZ.dump
```

O comando restaura em um banco temporário, verifica o histórico do Flyway e remove apenas esse banco temporário. Ele não altera o banco principal `livraria`.

## 9. Parar e reiniciar

Para parar os containers, preservando banco, capas e backups:

```bat
docker compose down
```

Para iniciar novamente:

```bat
docker compose up -d
```

Para acompanhar logs durante um problema:

```bat
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f postgres
```

## 10. Reinicialização completa dos dados de desenvolvimento

Use este procedimento apenas se desejar apagar **todos os dados locais** da aplicação, incluindo banco, capas e backups. Feche a aplicação antes de executar:

```bat
docker compose down -v
docker compose up -d --build
```

Isso é irreversível para os volumes locais. Faça e valide um backup antes, se quiser preservar dados de demonstração.

## 11. Problemas frequentes

| Situação | Solução |
|---|---|
| `PAYMENT_WEBHOOK_SECRET is missing` | Crie/edite o `.env` e preencha `PAYMENT_WEBHOOK_SECRET`. |
| Porta `5432`, `5173`, `8080` ou `8025` ocupada | Altere a porta correspondente no `.env` e execute `docker compose up -d`. |
| `docker` não é reconhecido | Instale/inicie o Docker Desktop e abra um novo terminal. |
| PostgreSQL não fica `healthy` | Execute `docker compose logs postgres` e confira se a porta configurada não está ocupada. |
| Backend não inicia | Execute `docker compose logs backend`; confirme que o segredo do webhook está preenchido no `.env`. |
| `Invoke-WebRequest` não é reconhecido | Você está no cmd; use `curl.exe` conforme este guia, ou abra o PowerShell. |

## Referências internas

- [Visão do projeto](README.md)
- [Roteiro de release](docs/release.md)
- [Desenvolvimento local](docs/development.md)
- [Backup, restauração e retenção](docs/operations/backup-and-restore.md)
