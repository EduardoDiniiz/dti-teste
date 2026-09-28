# Registro de uso de IA — contratos SLP

Ferramenta: assistente Codex. Data: 2026-09-28. Escopo: criação de contratos e documentação, sem implementação Java.

## Solicitação do usuário (texto)

“Com base nessas definições, crie os arquivos de configuração com os contratos e detalhamento dos endpoints a serem criados na Api.”

## Contexto fornecido (resumo)

SLP, três serviços Buyer/Seller/Coordinator, ofertas de 1 Piggy, compra >= venda, execução pela média, débito/crédito e publicação de taxa. Sem match parcial/cancelamento/expiração. Contract-first, borda assíncrona, testes obrigatórios e registro do uso de IA. Propostas anteriores: UUID, preço decimal string, consulta de status, idempotência e outbox. O resumo evita reproduzir integralmente documentos externos.

## Resultado e revisão necessária

Gerados dois OpenAPI, contrato de entrada/saída do Coordinator, cinco schemas de eventos, exemplos, topologia descritiva, detalhamento de decisões e script de validação. Nenhum código de referência de terceiros foi copiado. Aprovar políticas não especificadas: prioridade, saldo inicial, precisão, limite monetário, auto-negociação e política de rejeição. A equipe deve explicar e testar toda implementação derivada.

## Fontes consultadas

- OpenAPI 3.1.0: https://spec.openapis.org/oas/v3.1.0.html — estrutura de documentos, schemas e referências.
- JSON Schema Draft 2020-12: https://json-schema.org/draft/2020-12/json-schema-core — schemas e referências.
- Arquivos locais pom.xml, application.yml, SavingsTest e scaffold: confirmaram ausência dos endpoints.

Não se copiou texto longo das especificações. As dependências de validação são instaladas separadamente e não vendorizadas. Registrar em novos commits futuros prompts, pesquisas e decisões. Rodar scripts/validate-contracts.py e registrar seu resultado na entrega; isso verifica contratos, não execução da API.

## Validação executada em 2026-09-28

Com jsonschema 4.26.0 e openapi-spec-validator 0.7.2, o comando de validação concluiu:

```text
OK: 2 OpenAPI, 6 JSON Schemas, referências, topologia e 75 casos de payload.
```

Verificados exemplos HTTP, eventos, rejeição de preços inválidos/campos extras/versão incorreta, estados condicionais e referências do Coordinator. `git diff --check` também passou. Não foram executados testes Java: esta entrega contém contratos/documentação, sem implementar os serviços. A validação estática não comprova saldo, média, entrega Kafka ou concorrência; esses cenários permanecem no plano de testes da implementação.
