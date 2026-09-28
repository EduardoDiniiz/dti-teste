# Estratégia — SLP

## Decisão de arquitetura

- **Um único projeto** Micronaut, com **3 módulos hexagonais** (`buyer`, `seller`, `coordinator`), um por dev.
- Os módulos **só se comunicam por eventos Kafka**. Nenhum módulo importa classe de outro; o que é
  compartilhado é o contrato (`co.inter.piggies.contracts`) e a infra (`co.inter.piggies.shared`).
- Por quê: com 1h30, um projeto só evita triplicar build, config e infra, e continua desacoplado.
  Como a fronteira entre os módulos é o evento, cada módulo pode virar um microsserviço separado
  depois, sem mudar o contrato.
- **Contract-first:** `contracts/openapi` (Buyer e Seller) e `contracts/events` (JSON Schema dos 3 eventos),
  commitados antes do código.
- **Borda assíncrona:** o POST grava a oferta e responde **202 + Location**; o match acontece depois,
  e o cliente consulta o status no `GET`.
- **Confiabilidade:** eventos saem pelo **Transactional Outbox** (mesma transação da gravação). Consumidores
  deduplicam por `(consumer, eventId)` em `processed_events`.
- **Ordem:** `slp.offers.created` usa a chave fixa `PIGGY-USD` → uma partição → o Coordinator processa
  uma oferta por vez, sem corrida no livro de ofertas.

## Fluxo de eventos

```
POST /buy-offers ─▶ buyer: grava OPEN + outbox ─▶ 202 + Location
POST /sell-offers ─▶ seller: grava OPEN + outbox ─▶ 202 + Location
                         │
                         ▼  slp.offers.created (chave PIGGY-USD)
                    coordinator: registra no livro → match? (compra >= venda)
                         ├─ não: fica aberta
                         └─ sim: preço = média (HALF_EVEN) · debita vendedor / credita comprador
                                 ├─▶ slp.trades.executed ─▶ buyer e seller: marcam EXECUTED
                                 └─▶ slp.exchange-rate.updated (nova taxa USD/Piggy)
```

## Divisão do time

| Dev | Módulo | Entrega |
|---|---|---|
| Dev 1 | `buyer` | `BuyOffer` + teste unitário · `POST /buy-offers` (202) · `GET /buy-offers/{id}` · publica `OfferCreated(BUY)` · consome `TradeExecuted` → `EXECUTED` |
| Dev 2 | `seller` | igual ao Buyer para `/sell-offers` e `OfferCreated(SELL)` |
| Dev 3 | `coordinator` | `MatchingEngine` + testes (20/10/30 → 25) · consome `OfferCreated` · cria o trade · lança na carteira · publica `TradeExecuted` e `ExchangeRateUpdated` |

Compartilhado (combinar antes de mudar): `contracts/`, `shared/`, `V1__create_schema.sql`.
Migrations novas com número combinado: `V2__buyer_…`, `V3__seller_…`, `V4__coordinator_…`.

## Plano de tempo (1h30)

| Tempo | Entrega | Commit |
|---|---|---|
| 0–15 | Requisitos, contratos e estrutura base (feito) | `docs + contracts + base` |
| 15–40 | Cada dev: domínio + teste unitário do seu módulo | por módulo |
| 40–60 | Buyer/Seller: REST 202 + outbox · Coordinator: listener + match + trade | por módulo |
| 60–75 | Fechar o ciclo: Buyer/Seller consomem `TradeExecuted`; teste de integração do fluxo 20/10/30 | integração |
| 75–90 | Revisar, atualizar `docs/ia`, ensaiar a explicação, commit final | final |

Regra: **commit a cada entrega que roda**. Entrega parcial funcionando vale mais que código completo quebrado.

## Plano de 1 hora — fluxo completo com 3 devs

**Princípio: primeiro o "esqueleto andando", depois a regra.** Até o minuto 25 os eventos já precisam
atravessar os 3 módulos, mesmo com lógica mínima. Depois disso, é só enriquecer. Integrar no final é o
que mais derruba time em live code.

### Linha do tempo

| Min | Dev 1 — buyer | Dev 2 — seller | Dev 3 — coordinator |
|---|---|---|---|
| 0–10 | `BuyOffer` (create/markExecuted) + `OfferStatus` + teste unitário | `SellOffer` + `OfferStatus` + teste unitário (copiar o do buyer e trocar o lado) | `MatchingEngine` + teste unitário 20/10/30 → 25 |
| 10–25 | Entidade JPA + repositório · `POST /buy-offers` → grava OPEN + `outboxWriter.write(OFFERS_CREATED, PAIR_KEY, OfferCreatedMessage(BUY))` → **202 + Location** · `GET /buy-offers/{id}` | Igual para `/sell-offers` (SELL) | `OfferCreatedListener` (group `slp-coordinator`) → só grava em `offers` e loga |
| **25** | 🔁 **Checkpoint 1:** POST no buyer e no seller → linha em `offers` no coordinator. Merge na `master`. | | |
| 25–40 | `TradeExecutedListener` (group `slp-buyer`): se `buyOfferId` for meu → `EXECUTED` + `executedPrice` (dedup `"buyer"`) | `TradeExecutedListener` (group `slp-seller`): se `sellOfferId` for meu → `EXECUTED` (dedup `"seller"`) | `MatchingService`: numa transação: dedup `"coordinator"` → grava oferta → busca contraparte → se houver, marca as duas `MATCHED`, grava `trades`, 2 linhas em `wallet_entries` (−1 vendedor, +1 comprador) → outbox `TradeExecuted` + `ExchangeRateUpdated` |
| **40** | 🔁 **Checkpoint 2:** fluxo 20/10/30 à mão (curl) → A e C `EXECUTED` a 25.00, B `OPEN`. Merge. | | |
| 40–55 | **Teste de integração ponta a ponta** (`FullFlowTest`): POST 20 (venda), 10 e 30 (compra) → Awaitility até GET mostrar A e C `EXECUTED`, B `OPEN` | Problem+json (400/404) + testes de integração do seller (POST 202, GET 404) | Teste de integração: `KafkaTestClient` lê `slp.exchange-rate.updated` com `rate` 25.00 + teste de idempotência (mesmo `OfferCreated` 2x → 1 oferta) |
| 55–60 | Todos: `./mvnw test` verde · atualizar `docs/ia` · commit final · combinar quem explica cada parte | | |

### Consultas prontas do coordinator (livro processado em ordem, uma oferta por vez)

```sql
-- nova COMPRA com preço :p → melhor VENDA aberta (menor preço, mais antiga)
SELECT * FROM offers WHERE side = 'SELL' AND status = 'OPEN' AND price <= :p
ORDER BY price ASC, created_at ASC LIMIT 1 FOR UPDATE;

-- nova VENDA com preço :p → melhor COMPRA aberta (maior preço, mais antiga)
SELECT * FROM offers WHERE side = 'BUY' AND status = 'OPEN' AND price >= :p
ORDER BY price DESC, created_at ASC LIMIT 1 FOR UPDATE;
```
Preço do trade: `buy.add(sell).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_EVEN)`.

### Regras para não travar

- Cada dev só mexe no **próprio pacote**. `contracts/`, `shared/` e a V1 **não mudam** sem avisar os três.
- Precisou de tabela nova ou coluna: migration própria (`V2__buyer_…`, `V3__seller_…`, `V4__coordinator_…`).
- Travou por mais de 5 minutos? Fala em voz alta. Trabalho em equipe é critério de avaliação.
- **Merge nos checkpoints** (min 25 e 40), não no final. Commits pequenos: `feat(buyer): POST 202`.
- Ninguém commita código que não sabe explicar ("a IA fez" elimina).

### Se atrasar, cortar nesta ordem (o fluxo principal continua de pé)

1. Header `Idempotency-Key` no POST (deixar documentado como próximo passo)
2. Teste de idempotência do coordinator
3. `wallet_entries` → manter só o débito/crédito no trade e explicar o ledger
4. Tratamento problem+json além do 404

**Nunca cortar:** POST 202 → evento → match → `TradeExecuted` → status `EXECUTED` + taxa publicada, e
pelo menos 1 teste unitário (match) e 1 de integração (fluxo). Isso é o mínimo que o enunciado cobra.

### Divisão da explicação na apresentação

- Dev 1: contract-first + borda assíncrona (202, Location, GET de status)
- Dev 2: outbox + idempotência por consumidor + por que um projeto com módulos via Kafka
- Dev 3: regra de match, prioridade preço-tempo, preço médio HALF_EVEN, ordenação por chave `PIGGY-USD`

## Como cada módulo publica e consome

- Publicar (dentro da transação do caso de uso): o adapter da porta `EventPublisher` do módulo chama
  `outboxWriter.write(eventId, Topics.X, chave, mensagem)`. O `OutboxRelay` publica no Kafka depois.
- Consumir: `@KafkaListener(groupId = "slp-<módulo>")` + `@Topic(Topics.X)`; deduplicar com
  `processedEvents.registerIfAbsent("<módulo>", eventId)` na mesma transação.

## Simplificações assumidas (documentadas)

- Ofertas sempre de 1 Piggy, sem match parcial, sem cancelamento ou expiração (enunciado).
- Carteira como ledger (`wallet_entries`), sem validação de saldo do vendedor nesta versão.
- Preço executado com 2 casas, `RoundingMode.HALF_EVEN`.

## Critério de pronto (demo)

1. Venda A a $20 → 202, `OPEN`.
2. Compra B a $10 → 202, `OPEN`, sem trade.
3. Compra C a $30 → trade A×C a **$25.00**; A e C `EXECUTED`; B continua `OPEN`; taxa 25.00 publicada.
