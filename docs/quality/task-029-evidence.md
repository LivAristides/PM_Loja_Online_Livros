# Evidências de qualidade — TASK-029

Data: 22/11/2026

## Acessibilidade e teclado

- O foco visível é aplicado a botões, campos e links com contorno de 3 px e afastamento de 3 px.
- Controles usam elementos HTML nativos; campos possuem rótulos associados e ações sem texto usam `aria-label`.
- O conteúdo dinâmico de busca usa `aria-live="polite"`; mensagens de erro usam `role="alert"` e confirmações administrativas usam `role="status"`.
- Diálogos usam `role="dialog"`, `aria-modal`, título associado e botão de fechamento rotulado. Nesta task, `Esc` fecha os diálogos de detalhes e pedido.
- Cores de disponibilidade, indisponibilidade e foco foram mantidas com contraste visual prioritário sobre os fundos claros.

## Responsividade

- A partir de 720 px ou menos, cabeçalho, catálogo e diálogos passam para uma coluna e o conteúdo preserva espaçamento de 16 px.
- A partir de 380 px ou menos, filtros passam para uma coluna e cartões diminuem a coluna de capa, atendendo à largura mínima de 360 px.
- Grades de catálogo e formulários administrativos usam `auto-fill`/`auto-fit`, evitando largura fixa de conteúdo.

## Desempenho e decisão de validação

O build de produção anterior confirmou um bundle principal de aproximadamente 248 kB sem compressão. Para esta task foram feitas três tentativas de auditoria Lighthouse local:

1. As imagens Lighthouse CI esperadas não estavam disponíveis no registro do ambiente.
2. O navegador local não está acessível pela automação desta conversa.
3. A alternativa com Chromium/Lighthouse em container não gerou relatório no runtime de automação, mesmo após a recuperação do Docker; portanto, não há métrica confiável a registrar.

**Decisão registrada:** não há métrica Lighthouse fabricada. `npm run lint` e `npm run build` passaram após a instalação das dependências locais. A auditoria Lighthouse em Chrome e a navegação manual por teclado devem ser repetidas em CI ou em máquina com Docker/browsers funcionais antes da release final (TASK-030). Não houve alteração de regras de negócio nem necessidade de novo índice de banco nesta task.
