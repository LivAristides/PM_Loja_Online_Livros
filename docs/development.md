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

## Infraestrutura transversal

O backend propaga `X-Correlation-Id` em cada resposta HTTP; valores inválidos são substituídos por UUID novo. Erros inesperados retornam uma mensagem segura e o identificador de correlação, sem detalhes internos.

As tabelas `audit_log` e `outbox_message` são infraestrutura técnica. Mensagens da outbox são persistidas antes do processamento e possuem estados `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED`. O agendador tenta mensagens disponíveis em lotes, usa bloqueio de linha para evitar processamento simultâneo e registra falhas para nova tentativa. Nenhum fluxo de pedidos, pagamentos ou notificações é implementado nesta etapa.

## Estrutura

- `backend/`: Spring Boot e futuras migrações Flyway.
- `frontend/`: React/TypeScript e Vite.
- `compose.yaml`: serviços locais reproduzíveis.
- `.github/workflows/ci.yml`: build e verificações básicas a cada push/PR.
# Autenticação local

A API utiliza sessão no servidor, persistida no PostgreSQL. O navegador recebe apenas o cookie de sessão `LIVRARIA_SESSION` (HttpOnly e SameSite=Lax); nenhum token é armazenado pelo React. Em produção, defina `SESSION_COOKIE_SECURE=true` para enviar o cookie exclusivamente por HTTPS.

Para ações que alteram estado, a interface busca `GET /api/v1/auth/csrf` e envia o token retornado no cabeçalho `X-XSRF-TOKEN`. O CORS local aceita apenas `http://localhost:5173` com credenciais.

## Conta e recuperação

O cadastro requer aceite dos termos na versão configurada por `TERMS_VERSION`; marketing é opcional e pode ser revogado em `/api/v1/me/consents/marketing`. Tokens de recuperação e convite ficam apenas como hash no PostgreSQL, expiram em 30 minutos e são inutilizados após uso. A redefinição de senha encerra todas as sessões ativas do usuário.

## Capas de livros

As capas são armazenadas no volume Docker `cover-data`; o PostgreSQL guarda apenas seus metadados técnicos. O upload administrativo aceita JPG, PNG ou WebP de até 2 MB e valida assinatura, tamanho e dimensões antes da gravação. Fora do Docker, `COVER_STORAGE_PATH` define o diretório local; o padrão é `storage/covers`.
