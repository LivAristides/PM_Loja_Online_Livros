# Contexto

```mermaid
flowchart TB
  Customer[Cliente] --> Store[Livraria Online]
  Staff[Perfis administrativos] --> Store
  Store --> Gateway[Gateway de pagamento simulado]
  Store --> Email[E-mail de teste]
  Store --> Shipping[Frete e rastreio simulados]
```
