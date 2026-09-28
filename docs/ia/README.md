# Uso de IA e referências

Exigência do desafio: tudo o que foi usado (prompts, pesquisas, código de referência) fica anexado e commitado aqui.

## Ferramentas
- Claude Code (assistente no terminal)

## Prompts e para que foram usados
| Quando | Quem | Prompt (resumo) | O que foi aproveitado |
|---|---|---|---|
| | | Quebrar o enunciado em 3 serviços e requisitos básicos | `REQUISITOS.md`, divisão do time |
| | | Gerar contratos contract-first | `contracts/` (OpenAPI + JSON Schema) |
| | | Gerar a base comum dos 3 serviços | config, migrations, outbox, testes base |

## Código de referência
- PROJETO-DTI (projeto de treino próprio, arquitetura hexagonal): padrões de outbox, idempotência (`processed_events`), lock otimista e Testcontainers.

## Pesquisas
- 
