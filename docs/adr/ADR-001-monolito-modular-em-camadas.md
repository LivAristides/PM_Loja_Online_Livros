# ADR-001 - Monólito modular em camadas

**Status:** Aceito  
**Contexto:** MVP acadêmico com frontend separado, regras transacionais de estoque/pagamento e necessidade de execução local simples.

## Decisão

Adotar monólito modular Java 21/Spring Boot, organizado em apresentação, negócio e acesso a dados. Módulos internos não acessam repositórios de outros módulos diretamente; integrações externas são portas da camada de negócio.

## Alternativas

- Microserviços: descartado pela complexidade operacional, contratos distribuídos e necessidade de mensageria.
- Monólito sem módulos: descartado por enfraquecer limites de domínio e testabilidade.

## Consequências

Transações locais preservam consistência da reserva. Evolução futura para serviços é possível por módulos e interfaces, mas não é objetivo do MVP.

**Rastreabilidade:** RF-007..018, RN-003..012, RNF-004; TASK-001..003.
