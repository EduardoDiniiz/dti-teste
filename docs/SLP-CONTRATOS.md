# SLP — contratos antes da implementação

Status: proposta v1 para revisão da equipe. Os arquivos definem comportamento futuro; não criam endpoints, serviços ou tópicos. O repositório ainda é o scaffold `savings`. Não há garantia de produção.

## Requisitos do enunciado

Três serviços: Buyer, Seller e Coordinator. Ofertas sempre de 1 Piggy, sem parcial, cancelamento ou expiração. Compra >= venda gera match; execução pela média. Debitar Piggy do vendedor, creditar comprador e publicar taxa. Contratos antes do código, eventos com schema, borda assíncrona, testes unitários/integração e documentação de IA comitada.

## Arquivos e endpoints

| Serviço | Contrato | Operações |
|---|---|---|
| PiggiesBuyerService | ../contracts/buyer.openapi.json | POST /buy-offers, GET /buy-offers/{offerId} |
| PiggiesSellerService | ../contracts/seller.openapi.json | POST /sell-offers, GET /sell-offers/{offerId} |
| PiggiesTransactionCoordinator | ../contracts/coordinator.schema.json | Consome BuyOfferCreated/SellOfferCreated; publica conclusão, rejeição e taxa; sem HTTP público |

OpenAPI está em JSON, formato aceito pela especificação; não precisa convertê-lo em YAML. Cada arquivo HTTP é independente, com schemas internos, para facilitar importação no Postman. Coordinator referencia os schemas em events/. `kafka-topology.json` é descritivo, não um arquivo de deployment.

### POST de compra/venda

Recebe offerId (UUID), buyerId/sellerId (UUID) e priceUsd (string decimal positiva). quantity não é aceito: sempre 1. Rejeitar campos extras, JSON inválido e valores fora do schema com 400. Preços até 12 dígitos inteiros e 2 casas, sem expoente; usar BigDecimal. Preço 30, 30.0 e 30.00 são iguais para idempotência após conversão numérica.

Primeira aceitação: transação local grava oferta PENDING e evento em outbox; depois retorna 202 com offerId/status/statusUrl e Location. Não aguarda Kafka nem match. A transação JPA bloqueante roda fora do event loop; compor resposta assíncrona, sem get/join bloqueante. A outbox é publicada depois.

Repetição do mesmo offerId/participante/preço: 200 com a representação atual, sem outra oferta ou evento lógico. Mesmo ID com dados diferentes: 409. IDs têm escopo por serviço; no Coordinator usar chave (side, offerId). Falha de aceitação: 503; cliente repete os mesmos dados e ID, pois a resposta pode ser perdida mesmo após commit.

### GET de compra/venda

200 retorna ID, participante, preço ofertado, createdAt e estado. PENDING: sem campos de resultado. EXECUTED: transactionId e executionPriceUsd obrigatórios. REJECTED: rejectionCode e rejectionReason obrigatórios. 400 para UUID inválido; 404 para ausência; 503 para indisponibilidade. Sem body na requisição. Sem SLA de match: oferta pode permanecer PENDING indefinidamente.

Erros usam application/problem+json com type, title, status, detail e code; instance opcional. Não expor stack traces. Exemplos completos estão nos OpenAPI.

## Decisões propostas, não especificadas pelo enunciado

1. Portas locais Buyer 8081/Seller 8082; autenticação fora do MVP local. Não usar identificação declarada pelo cliente como autorização em produção.
2. Carteiras com IDs UUID previamente cadastradas no Coordinator; saldo inteiro de Piggies. Dados de demonstração: vendedor bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb com 1 Piggy, comprador aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa com 0. São fixtures futuras, não migrações já existentes.
3. Não movimentar USD: somente Piggies, conforme escopo descrito. Confirmar com avaliador se houver exigência adicional.
4. Até 2 casas nas ofertas; até 3 no preço executado, para média exata. Exemplo 20.00 e 20.01 resulta 20.005. Limite de 12 dígitos inteiros é uma escolha técnica a validar.
5. Ofertas chegam a slp.offers.v1, mesma chave PIGGY-USD e uma partição. Coordinator processa sequencialmente no MVP. Prioridade da contraparte compatível pelo offset desse tópico, não por relógio do cliente. Não alterar partições sem rever ordenação/matching.
6. Não permitir auto-negociação: ignorar contraparte do mesmo participante e manter ofertas pendentes. Não rejeitar só por não haver contraparte compatível.
7. Participante inexistente: rejeitar sua oferta com PARTICIPANT_NOT_FOUND. Saldo insuficiente na execução: rejeitar venda com INSUFFICIENT_PIGGY_BALANCE; compra continua disponível. Após rejeição, procurar outra contraparte compatível. Sem reserva antecipada no MVP; proteger saldo no momento do débito.
8. Estados: PENDING → EXECUTED ou REJECTED. Estados terminais não regridem. Não inventar FAILED para indisponibilidade temporária: retentar internamente. Transições contraditórias devem ser registradas como erro, sem sobrescrever resultado.

## Kafka e responsabilidades

Envelope obrigatório: eventId UUID, eventType fixo por schema, schemaVersion=1, occurredAt UTC (RFC3339) e payload. Publish/retry de um mesmo evento preserva ID, instante e payload. Os exemplos reutilizam IDs ilustrativos entre arquivos: na implementação cada ocorrência possui eventId único. Schemas rejeitam campos desconhecidos; mudanças aditivas também precisam de coordenação/versionamento para consumidores estritos.

Buyer publica BuyOfferCreated; Seller publica SellOfferCreated. Coordinator valida e deduplica entradas, persiste livro, encontra contraparte disponível e verifica compra >= venda. Média calculada com BigDecimal, nunca double. Schema valida formato, mas não igualdade da média, saldo, unicidade, identidade da chave Kafka ou relação entre os IDs: essas regras precisam de código/testes.

Na mesma transação do Coordinator: verificar disponibilidade das duas ofertas e saldo, debitar 1, creditar 1, registrar transação, marcar ambas executadas e gravar TransactionCompleted/ExchangeRateUpdated na outbox. Constraints impedem usar a mesma oferta duas vezes; locks/atualizações condicionais impedem saldo negativo. Deduplicação do evento de entrada e efeito de negócio precisam ser atômicos. Confirmar offset somente após commit. Rejeição também deve ser persistida com outbox, sem movimentação parcial.

Buyer e Seller consomem TransactionCompleted em grupos DISTINTOS (slp-buyer-v1 e slp-seller-v1) para ambos receberem. Cada um atualiza somente sua oferta. OfferRejected é filtrado por side. Atualizar status/deduplicar em transação e não regredir estado em replay. Coordinator usa slp-coordinator-v1. Outbox entrega ao menos uma vez; consumidores devem aceitar reentrega sem novo efeito.

TransactionCompleted e ExchangeRateUpdated estão em tópicos diferentes: não prometer ordem global nem atomicidade entre observações externas. Ambos carregam transactionId. Indisponibilidade Kafka deixa outbox pendente; crash após publicar pode duplicar evento. Retry precisa preservar identidade. Política operacional de mensagem inválida/DLT deve ser acordada; nunca descartar silenciosamente.

## Exemplo de aceite

1. Registrar venda de 20.00 (vendedor com 1 Piggy).
2. Registrar compra de 10.00: ambas pendentes, nenhuma transferência/taxa.
3. Registrar nova compra de 30.00 com OUTRO offerId: match com venda; execução 25.000.
4. Vendedor fica com 0, comprador executado com 1. Compra de 10.00 permanece pendente.
5. GETs da compra de 30 e venda retornam EXECUTED com mesmo transactionId; observar evento de taxa 25.000.

## Testes a implementar

Unitários: compra < venda sem match; igualdade com match; média 30/20=25 e 20.01/20=20.005; saldo insuficiente; não auto-negociação; escolha por sequência.
Integração: POST 202/outbox persistida; replay 200 sem duplicar; conflito 409; validação 400; GET 404; Kafka real até estado final com timeout; débito/crédito atômicos; replay de eventos sem nova transferência; duas compras disputam uma venda, apenas uma execução; recuperação de outbox após falha.
Contratos: validar exemplos HTTP/eventos e casos inválidos, refs locais e OpenAPI. O script em scripts/validate-contracts.py faz verificações estáticas; não substitui testes de aplicação.

## Como visualizar e validar

Importe contracts/buyer.openapi.json e contracts/seller.openapi.json no Postman. As coleções descrevem endpoints FUTUROS; só funcionarão após implementação. Os exemplos de eventos ficam em contracts/examples/.

Com Python 3 disponível, na raiz do repositório:

```powershell
python -m venv .venv-contracts
.\.venv-contracts\Scripts\python.exe -m pip install -r scripts/requirements-contracts.txt
.\.venv-contracts\Scripts\python.exe scripts/validate-contracts.py
```

O ambiente de validação é opcional e separado do Java. Não comitar .venv-contracts. A validação não inicia Docker nem altera banco.

## Próximas entregas

Revisar decisões em equipe e comitar contratos ANTES da implementação. Dividir Buyer, Seller e Coordinator usando estes formatos. Implementar o exemplo completo, testes unitários/integração e instruções de execução. Não há compose/configuração Micronaut nova nesta entrega: portas, tópicos, serviços e persistência ainda devem ser implementados.
