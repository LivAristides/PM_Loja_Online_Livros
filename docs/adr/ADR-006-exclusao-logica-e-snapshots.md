# ADR-006 - Exclusão lógica e snapshots históricos

**Status:** Aceito

## Decisão

Entidades de catálogo vinculadas são desativadas; itens de pedido guardam snapshots de dados e preço. Exclusão física é permitida somente sem referências. Anonimização remove identificadores pessoais preservando totais e evidências requeridas.

## Consequências

Histórico e relatórios não mudam após alterações no catálogo ou na conta. Consultas devem filtrar registros inativos onde aplicável.

**Rastreabilidade:** RF-003, RF-014, RN-008, RN-013, RN-016; TASK-006, 009, 017, 026.
