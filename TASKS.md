# Backlog executável

## Regras do backlog

Cada tarefa deve obedecer a `AGENTS.md`. Dependências são obrigatórias; IDs não são renumerados. `Allowed` delimita mudanças primárias. `Incidental` significa apenas correção mínima necessária para compilar/testar a própria tarefa, sem contrato, regra ou arquitetura nova. `Forbidden` veda explicitamente mudanças fora do objetivo. Todas as TASKs incluem atualização localizada de documentação/testes quando necessária.

## Índice de execução

| Ordem | TASK | Fase | Prioridade | Dependências | Estado |
|---:|---|---|---|---|---|
| 1 | TASK-001 | Fundação | P0 | - | DONE |
| 2 | TASK-002 | Fundação | P0 | 001 | DONE |
| 3 | TASK-003 | Fundação | P0 | 002 | DONE |
| 4 | TASK-004 | Identidade | P0 | 003 | DONE |
| 5 | TASK-005 | Identidade | P0 | 004 | DONE |
| 6 | TASK-006 | Privacidade | P0 | 014 | BLOCKED |
| 7 | TASK-007 | Checkpoint | P0 | 005 | DONE |
| 8 | TASK-008 | Catálogo | P0 | 007 | DONE |
| 9 | TASK-009 | Catálogo | P0 | 008 | BLOCKED |
| 10 | TASK-010 | Catálogo | P1 | 009 | BLOCKED |
| 11 | TASK-011 | Catálogo | P0 | 009 | BLOCKED |
| 12 | TASK-012 | Checkpoint | P0 | 010,011 | BLOCKED |
| 13 | TASK-013 | Compra | P0 | 012 | BLOCKED |
| 14 | TASK-014 | Compra | P0 | 013 | BLOCKED |
| 15 | TASK-015 | Compra | P0 | 014 | BLOCKED |
| 16 | TASK-016 | Compra | P0 | 015 | BLOCKED |
| 17 | TASK-017 | Compra | P0 | 016 | BLOCKED |
| 18 | TASK-018 | Compra | P0 | 017 | BLOCKED |
| 19 | TASK-019 | Compra | P0 | 018 | BLOCKED |
| 20 | TASK-020 | Checkpoint | P0 | 019 | BLOCKED |
| 21 | TASK-021 | Operação | P0 | 020 | BLOCKED |
| 22 | TASK-022 | Operação | P0 | 021 | BLOCKED |
| 23 | TASK-023 | Operação | P1 | 022 | BLOCKED |
| 24 | TASK-024 | Operação | P0 | 023 | BLOCKED |
| 25 | TASK-025 | Operação | P1 | 024 | BLOCKED |
| 26 | TASK-026 | Operação | P0 | 025 | BLOCKED |
| 27 | TASK-027 | Checkpoint | P0 | 026 | BLOCKED |
| 28 | TASK-028 | Qualidade | P0 | 027 | BLOCKED |
| 29 | TASK-029 | Qualidade | P0 | 028 | BLOCKED |
| 30 | TASK-030 | Qualidade | P0 | 029 | BLOCKED |

## TASK-001 - Inicializar monorepo e execução local

**Objetivo:** Criar estrutura `backend/` e `frontend/`, Compose, convenções e configuração sem segredos. **Rastreia:** RNF-005/008, ADR-001. **Aceite:** uma instrução sobe os serviços vazios e documentação explica variáveis. **Testes/gates:** build base e Compose. **Allowed:** raiz, Docker, backend/frontend de scaffold, CI inicial. **Forbidden:** funcionalidades de domínio, credenciais reais. **DoD:** build/Compose/documentação passam.

**Status note:** DONE em 19/10/2026: imagens criadas e serviços validados via Docker Compose; backend health, frontend e Mailpit responderam com sucesso.

## TASK-002 - Persistência, Flyway e dados de demonstração

**Objetivo:** Configurar PostgreSQL, Flyway, perfis e carga segura de demonstração. **Depende:** 001. **Rastreia:** RNF-004/005. **Aceite:** migração vazia/versionada e banco sobe; sem dados reais. **Testes:** integração de conexão/migração. **Allowed:** backend de infraestrutura, Compose, docs. **Forbidden:** entidades de domínio final, seeds com senhas conhecidas sem troca obrigatória. **DoD:** migração e teste passam.

## TASK-003 - Infraestrutura transversal

**Objetivo:** Implementar tratamento de erro, correlação, auditoria básica, outbox e agendamento seguro. **Depende:** 002. **Rastreia:** RF-018, RNF-007, ADR-003. **Aceite:** mensagem sobrevive reinício e falha é registrável. **Testes:** unitário/integrado de outbox. **Allowed:** shared/outbox/audit/config. **Forbidden:** regras de pedidos/pagamento. **DoD:** gates aplicáveis passam.

**Status note:** DONE em 19/10/2026: outbox persistente, auditoria básica, correlação e tratamento seguro de erro implementados; testes unitários e de integração passaram contra o PostgreSQL do Compose.

## TASK-004 - Identidade, sessão e papéis

**Objetivo:** Implementar usuários, RBAC, sessão server-side, login/logout/bloqueio. **Depende:** 003. **Rastreia:** RF-001/013, RN-015, ADR-002. **Aceite:** cinco perfis respeitam menor privilégio; 5 falhas bloqueiam 15 min. **Testes:** auth e autorização API. **Allowed:** identity/security/frontend de acesso. **Forbidden:** catálogo, checkout, JWT. **DoD:** testes e revisão de segurança passam.

**Status note:** DONE em 22/10/2026: autenticação por sessão persistida, cookie seguro por perfil, CSRF, RBAC e bloqueio temporário validados por testes de integração.


## TASK-005 - Conta, recuperação e consentimentos

**Objetivo:** Perfil/endereço, troca de senha, convite administrativo, recuperação e aceite versionado. **Depende:** 004. **Rastreia:** RF-002/003, RN-016. **Aceite:** token único expira e invalida sessões; consentimento de marketing é opcional/revogável. **Testes:** fluxo de recuperação e autorização. **Allowed:** identity/customers/notificações correlatas. **Forbidden:** exportação/anonimização ainda não autorizadas. **DoD:** gates passam.

**Status note:** DONE em 25/10/2026: perfil/endereço, consentimentos versionados, recuperação com token de uso único e expiração, revogação de sessões e convite administrativo autorizado foram validados por testes de integração.


## TASK-006 - Direitos LGPD e retenção configurável

**Objetivo:** Exportação, solicitação de anonimização, bloqueio por pedido ativo e políticas de retenção. **Depende:** 014. **Rastreia:** RF-003, RN-016, ADR-006. **Aceite:** exporta somente dados do titular; anonimização preserva evidência sem PII. **Testes:** autorização e dados anonimizados. **Allowed:** customers/privacy/audit/config. **Forbidden:** anonimizar pedidos ativos ou apagar auditoria. **DoD:** testes e critérios passam.

**Status note:** Reordenada em 25/10/2026 por decisão explícita: aguarda TASK-014, que fornece o contrato de pedidos necessário para bloquear anonimização durante pedidos ativos.

## TASK-007 - Checkpoint PHASE-01

**Objetivo:** Validar fundação e identidade disponível antes do catálogo. **Depende:** 005. **Aceite:** build, migrações, auth, auditoria e docs coerentes. **Testes:** todos da fase. **Allowed:** correções mínimas da fase e TASKS. **Forbidden:** funcionalidades de catálogo. **DoD:** gates globais P0 passam.

**Status note:** Dependência ajustada em 25/10/2026, com autorização explícita, porque a privacidade completa (TASK-006) requer o domínio de pedidos da TASK-014.

## TASK-008 - Domínio de catálogo

**Objetivo:** Modelar livro, autor, editora, categoria, relações e exclusão lógica. **Depende:** 007. **Rastreia:** RF-014, RN-001/002/013. **Aceite:** ISBN-13 único/validado; N:N e editora única persistem. **Testes:** serviços/restrições. **Allowed:** catalog/migrações/frontend admin. **Forbidden:** carrinho. **DoD:** gates passam.

## TASK-009 - Administração de catálogo, preço e estoque

**Objetivo:** CRUD controlado, upload de capa, preço, movimentos e desativação. **Depende:** 008. **Rastreia:** RF-005/014, ADR-004/006. **Aceite:** capa validada; movimentos auditados; vínculo bloqueia exclusão física. **Testes:** upload, autorização, estoque. **Allowed:** catalog/inventory/storage e UI administrativa. **Forbidden:** reservar no checkout. **DoD:** gates passam.

## TASK-010 - Catálogo público e busca

**Objetivo:** Listagem paginada, busca/filtros e detalhe responsivo. **Depende:** 009. **Rastreia:** RF-004/005, RNF-001..004. **Aceite:** filtros funcionam e inativos não aparecem. **Testes:** API, UI e acessibilidade. **Allowed:** catalog público/frontend. **Forbidden:** avaliações/cupons/lista de desejos. **DoD:** p95/qualidade verificadas quando aplicável.

## TASK-011 - Carrinho

**Objetivo:** Criar carrinho persistente por cliente e operações de linha sem reserva. **Depende:** 009. **Rastreia:** RF-006, RN-003. **Aceite:** alteração de estoque gera aviso e não bloqueia unidade. **Testes:** serviço/API/UI. **Allowed:** cart/frontend. **Forbidden:** criação de pedido. **DoD:** gates passam.

## TASK-012 - Checkpoint PHASE-02

**Objetivo:** Validar catálogo e carrinho. **Depende:** 010,011. **Aceite:** regras ISBN/inativos/estoque e experiência responsiva passam. **Testes:** regressão da fase. **Allowed:** correções mínimas fase 02. **Forbidden:** checkout/pagamento. **DoD:** gates globais P0 passam.

## TASK-013 - Frete configurável e snapshots de checkout

**Objetivo:** Configurações da loja, zonas CEP e cálculo isolado. **Depende:** 012. **Rastreia:** RF-007/017, RN-008. **Aceite:** zonas e prazos configuráveis; cálculo é substituível. **Testes:** limites de CEP/snapshot. **Allowed:** shipping/settings/checkout DTO. **Forbidden:** pagamento/reserva. **DoD:** gates passam.

## TASK-014 - Pedido e reserva transacional

**Objetivo:** Criar pedido, itens snapshot e reserva atômica de estoque. **Depende:** 013. **Rastreia:** RF-007/009, RN-003/008. **Aceite:** disputa última unidade cria um pedido somente. **Testes:** concorrência dedicada. **Allowed:** orders/checkout/inventory. **Forbidden:** gateway e status logísticos. **DoD:** integração concorrente passa.

## TASK-015 - Pagamento simulado e webhook

**Objetivo:** Tentar PIX/cartão, validar assinatura e idempotência. **Depende:** 014. **Rastreia:** RF-008, RN-004/006/014, ADR-004/005. **Aceite:** máximo 3 cartão; um processamento; webhook repetido não duplica efeitos. **Testes:** API/webhook. **Allowed:** payments/adaptador simulado. **Forbidden:** provedor real. **DoD:** gates passam.

## TASK-016 - Expiração e compensação

**Objetivo:** Expirar reservas, liberar estoque e processar pagamento tardio/estorno idempotente. **Depende:** 015. **Rastreia:** RF-009/018, RN-004/007. **Aceite:** expiração reiniciável libera uma vez; tardio não reativa pedido. **Testes:** jobs/reinício. **Allowed:** payments/orders/inventory/outbox. **Forbidden:** alterar prazos sem configuração. **DoD:** gates passam.

## TASK-017 - Pedidos do cliente e cancelamento

**Objetivo:** Histórico, transições, cancelamento e correção restrita de endereço. **Depende:** 016. **Rastreia:** RF-010, RN-005/008..010. **Aceite:** transição inválida é rejeitada e histórico registra origem/motivo. **Testes:** máquina de estados/API/UI. **Allowed:** orders/customers frontend. **Forbidden:** retorno/devolução. **DoD:** gates passam.

## TASK-018 - Logística e rastreio simulado

**Objetivo:** Separação, envio com código obrigatório e entrega por atendimento. **Depende:** 017. **Rastreia:** RF-010, RN-009. **Aceite:** somente papel autorizado altera status; rastreio visível ao cliente. **Testes:** RBAC/transições. **Allowed:** orders/shipping/admin UI. **Forbidden:** integração transportadora real. **DoD:** gates passam.

## TASK-019 - Notificações do ciclo de compra

**Objetivo:** E-mails de teste assíncronos para eventos definidos. **Depende:** 018. **Rastreia:** RF-012, ADR-003/004. **Aceite:** falha não desfaz pedido e é reenviada. **Testes:** outbox/adaptador. **Allowed:** notifications/templates/outbox. **Forbidden:** e-mail real/campanhas. **DoD:** gates passam.

## TASK-020 - Checkpoint PHASE-03

**Objetivo:** Validar compra ponta a ponta. **Depende:** 019. **Aceite:** checkout concorrente, pagamento, expiração, cancelamento, rastreio e e-mail passam. **Testes:** E2E/regressão. **Allowed:** correções mínimas fase 03. **Forbidden:** devolução/admin não relacionados. **DoD:** gates globais P0 passam.

## TASK-021 - Solicitação e tratamento de devolução

**Objetivo:** Implementar devolução integral, prazos, aprovação/recusa e postagem simulada. **Depende:** 020. **Rastreia:** RF-011, RN-011. **Aceite:** motivos/prazos aplicados e recusa exige justificativa. **Testes:** regras e API. **Allowed:** returns/orders/shipping. **Forbidden:** devolução parcial. **DoD:** gates passam.

## TASK-022 - Recebimento, reembolso e estoque de retorno

**Objetivo:** Conferir retorno, estornar e decidir reentrada/baixa por avaria. **Depende:** 021. **Rastreia:** RF-011, RN-012. **Aceite:** estorno integral automático e movimento de estoque auditado. **Testes:** integração pagamento/estoque. **Allowed:** returns/payments/inventory/outbox. **Forbidden:** alterar fluxo de pedido não relacionado. **DoD:** gates passam.

## TASK-023 - Administração operacional e financeiro

**Objetivo:** Painéis para pedidos, pendências de estorno e intervenção manual justificada. **Depende:** 022. **Rastreia:** RF-013/015. **Aceite:** financeiro/admin resolvem somente ações permitidas e há auditoria. **Testes:** RBAC/API/UI. **Allowed:** administration/payments/orders/audit frontend. **Forbidden:** permissões implícitas. **DoD:** gates passam.

## TASK-024 - Administração de usuários e configurações

**Objetivo:** Criar/desativar administradores e manter dados da loja/faixas de frete. **Depende:** 023. **Rastreia:** RF-013/017, RN-015. **Aceite:** convite/troca inicial e menor privilégio funcionam. **Testes:** RBAC/configuração. **Allowed:** administration/identity/settings. **Forbidden:** cadastro público de admin. **DoD:** gates passam.

## TASK-025 - Relatórios básicos

**Objetivo:** Vendas, mais vendidos e estoque baixo com acesso apropriado. **Depende:** 024. **Rastreia:** RF-016. **Aceite:** totais usam snapshots e respeitam período/perfil. **Testes:** consultas/API/UI. **Allowed:** reports/administration. **Forbidden:** BI/exportações não especificadas. **DoD:** gates passam.

## TASK-026 - Backups, restauração e retenção

**Objetivo:** Documentar/automatizar backup local, teste de restore e expurgo parametrizado. **Depende:** 025. **Rastreia:** RNF-005/007. **Aceite:** backup diário/retenção 7 dias e restore testado. **Testes:** procedimento de restauração. **Allowed:** ops/Compose/docs/jobs. **Forbidden:** destruir dados sem backup validado. **DoD:** RPO/RTO documentados e teste passa.

## TASK-027 - Checkpoint PHASE-04

**Objetivo:** Validar operação administrativa, devolução e recuperação. **Depende:** 026. **Aceite:** auditoria, RBAC, devolução, backup e relatórios coerentes. **Testes:** regressão fase. **Allowed:** correções mínimas fase 04. **Forbidden:** novos recursos fora do MVP. **DoD:** gates globais P0 passam.

## TASK-028 - OpenAPI, hardening e observabilidade básica

**Objetivo:** Publicar contrato, revisar validações, segredos, correlação e logs seguros. **Depende:** 027. **Rastreia:** RNF-006..008. **Aceite:** OpenAPI atual, erros consistentes e logs sem dados sensíveis. **Testes:** API/segurança. **Allowed:** api/config/docs/audit. **Forbidden:** trocar arquitetura/auth. **DoD:** gates passam.

## TASK-029 - Acessibilidade, responsividade e desempenho

**Objetivo:** Validar WCAG AA prioritário, navegadores/telas e metas de volume. **Depende:** 028. **Rastreia:** RNF-001..004. **Aceite:** fluxos por teclado, Lighthouse e carga simples atendem metas ou têm decisão registrada. **Testes:** E2E/a11y/k6. **Allowed:** frontend/testes/config de índices. **Forbidden:** alterar regras de negócio. **DoD:** evidências salvas.

## TASK-030 - Checkpoint final de release

**Objetivo:** Executar todos os gates, restaurar backup, revisar rastreabilidade e preparar demonstração. **Depende:** 029. **Aceite:** build/testes/docs/Compose/CI e dados de demonstração reproduzíveis. **Testes:** suíte completa. **Allowed:** correções mínimas e documentação de release. **Forbidden:** novas funcionalidades. **DoD:** todos os gates obrigatórios passam e backlog é atualizado.
