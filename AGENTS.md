**Este documento define as regras obrigatórias para qualquer agente de programação que opere neste repositório.**

# Contrato operacional do agente

## Fontes de verdade e ordem de leitura

Antes de qualquer TASK, leia: (1) este arquivo, (2) `README.md`, (3) `TASKS.md`, (4) ADRs aplicáveis, (5) código e (6) testes. `README.md` define requisitos/escopo; `TASKS.md`, backlog/estado/Scope Guard; `AGENTS.md`, processo; e `docs/adr/`, decisões. Conflito relevante entre fontes exige parada e decisão humana.

## Seleção e estado

Selecione a primeira TASK em ordem com `STATUS: READY`. Antes de iniciar, confirme dependências `DONE`, artefatos e testes das dependências, RF/RN/RNF/UC/ADR relacionados, critérios de aceitação, Scope Guard e DoR. Então altere somente aquela TASK para `IN_PROGRESS`. Estados: `BLOCKED -> READY -> IN_PROGRESS -> IN_REVIEW -> DONE`; falhas usam `FAILED`. Concluir uma TASK não autoriza a seguinte.

## Definition of Ready

Uma TASK é READY somente com objetivo, critérios de aceitação, testes, dependências resolvidas, contratos/ADRs necessários e Scope Guard definidos. Caso contrário, marque BLOCKED e explique o motivo.

## Scope Guard

Cada TASK define `Allowed Changes`, política de alterações incidentais e `Forbidden Changes`.

- Alteração incidental só é permitida se for pequena, necessária para compilar/testar a TASK, não mudar contrato/arquitetura/regra e não afetar módulo não relacionado.
- Mudança em múltiplos módulos, contrato, regra, arquitetura ou refatoração ampla não é incidental. Pare e emita `SCOPE ESCALATION`.
- Todo arquivo alterado é classificado como PRIMARY ou INCIDENTAL no relatório final.

Use este formato quando houver ampliação necessária:

```text
## SCOPE ESCALATION
TASK: TASK-xxx
Necessidade identificada: ...
Motivo e impacto: ...
Arquivos/módulos afetados: ...
Allowed/Forbidden relacionados: ...
Por que não é incidental: ...
Alternativas e recomendação: ...
Decisão necessária: ...
```

## Implementação e qualidade

Não implemente TASK futura, não invente requisito, não altere arquitetura sem ADR, não remova testes/validações para passar gates e não exponha dados sensíveis. Execute gates aplicáveis: build, lint, unitários, integração, API, segurança, acessibilidade/E2E, critérios de aceitação e regressão. Nunca afirme que um gate passou sem executá-lo.

## Definition of Done

Uma TASK só é DONE se implementação e documentação necessárias estiverem concluídas; gates passarem; critérios de aceitação, segurança e contratos forem atendidos; `TASKS.md` estiver atualizado; todas as mudanças obedecerem ao Scope Guard; e não houver regressão conhecida. Caso contrário, use `FAILED` ou `BLOCKED`.

## Branches e commits

Não desenvolva em `main`. Use preferencialmente uma branch por TASK: `feature/TASK-xxx-descricao`, `fix/TASK-xxx-descricao` ou `chore/TASK-xxx-descricao`. Commits seguem Conventional Commits: `feat(escopo): descrição [TASK-xxx]`. Mantenha-os pequenos, coesos e rastreáveis.

## Segurança, privacidade e dados

Nunca registre senha, token, cartão, CPF completo, endereço ou segredo. Não versionar `.env` real, dumps de produção ou imagens com dados pessoais. Valide autorização server-side; respeite exclusão lógica, snapshots de pedido, retenção e anonimização definidos no README/ADRs.

## Parada e blockers

Pare diante de requisito ambíguo, contradição, dependência ausente, decisão arquitetural não registrada, risco de segurança/perda de dados, quebra de contrato, conflito de Scope Guard ou falha externa pré-existente.

```text
## BLOCKER
TASK: TASK-xxx
Problema e impacto: ...
RF/RN/RNF/UC/ADR afetados: ...
Alternativas e recomendação: ...
Decisão necessária: ...
```

## Relatório final obrigatório

```text
## TASK Execution Report
TASK: TASK-xxx | Status: DONE | Branch: ... | Commits: ...
Primary Changes: ...
Incidental Changes: NONE | tabela de arquivo, alteração e justificativa
Tests Executed: ...
Quality Gates: Build/Lint/Unit/Integration/API/Security/Acceptance/Regression = PASS|FAIL|N/A
Scope Validation: Allowed PASS; Incidental PASS; Forbidden PASS; Scope Creep NONE
Definition of Done: PASS|FAIL
Next Eligible TASK: TASK-xxx | NONE
```
