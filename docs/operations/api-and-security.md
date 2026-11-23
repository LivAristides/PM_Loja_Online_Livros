# Contrato e segurança da API

## OpenAPI

Com o backend em execução, o contrato gerado automaticamente está disponível em:

- `http://localhost:8080/v3/api-docs` — documento OpenAPI JSON;
- `http://localhost:8080/swagger-ui.html` — interface Swagger UI.

O contrato descreve os controladores REST ativos e é atualizado a partir do código. Os endpoints da aplicação usam o prefixo `/api/v1`.

## Erros e correlação

Qualquer erro de API retorna o mesmo envelope, sem informações internas:

```json
{
  "timestamp": "2026-11-20T00:00:00Z",
  "status": 400,
  "code": "validation_failed",
  "message": "Há campos inválidos.",
  "correlationId": "uuid",
  "details": [{ "field": "email", "message": "deve ser um endereço de e-mail bem formado" }]
}
```

Envie opcionalmente um UUID em `X-Correlation-Id`; a API o devolve em todas as respostas e o inclui nos logs. Valores inválidos são substituídos por um UUID gerado pelo servidor.

## Segredos e logs

`PAYMENT_WEBHOOK_SECRET` é obrigatório para iniciar o backend fora do perfil de teste. Gere um valor aleatório no `.env` local e nunca o versione. Senhas, tokens de recuperação, dados de cartão, corpo de webhook e segredos não são registrados nos logs. Erros inesperados registram apenas tipo da exceção e o `correlationId`.
