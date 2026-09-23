# Arquitetura detalhada

## Limites de módulos

`identity`, `catalog`, `cart`, `checkout`, `orders`, `payments`, `inventory`, `shipping`, `returns`, `notifications`, `administration`, `reports`, `audit` e `shared` são módulos internos. Cada módulo expõe serviços de aplicação/contratos e protege entidades e repositórios próprios.

Controladores REST somente fazem autenticação/autorização, validação de entrada e mapeamento de DTO. Serviços de negócio aplicam regras e publicam mensagens outbox na mesma transação. Repositórios persistem agregados com JPA; Flyway versiona o esquema.

## Consistência e concorrência

Criar pedido bloqueia/atualiza estoque em transação e só sucede se quantidade disponível cobrir cada linha. Cancelamento e expiração geram movimento compensatório. Webhooks são validados, deduplicados por chave de evento e não podem criar duas cobranças/estornos.

## Segurança operacional

Erros retornam mensagens seguras e `correlationId`. Logs estruturados omitem senha, CPF completo, endereço e dados de cartão. Auditoria é obrigatória para permissões, exclusões lógicas, preços, estoque, status e intervenções financeiras.

## Execução

Docker Compose fornece backend, frontend, PostgreSQL e Mailpit/Mailtrap configurável. Volumes persistem banco e imagens. Backup/exportação e restauração devem ser documentados e testados antes do checkpoint final.
