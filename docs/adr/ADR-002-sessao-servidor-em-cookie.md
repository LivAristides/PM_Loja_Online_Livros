# ADR-002 - Sessão de servidor em cookie seguro

**Status:** Aceito

## Decisão

Autenticação web usa sessão mantida pelo Spring e cookie `HttpOnly`, `Secure` em produção e `SameSite=Lax` (ou `Strict` após validação do fluxo). React não armazena tokens. Sessões podem ser revogadas e expiram por inatividade; área administrativa expira após 30 minutos.

## Alternativas

- JWT no browser: descartado no MVP por aumentar superfície de exposição e tornar revogação mais complexa.
- Login sem sessão persistente: inadequado para carrinho, área administrativa e invalidação de acesso.

## Consequências

É necessário configurar CORS/CSRF e cookies adequadamente. Desenvolvimento HTTP local terá configuração estritamente separada da produção HTTPS.

**Rastreabilidade:** RF-001..003, RN-015..016, RNF-006; TASK-004..007.
