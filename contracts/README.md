# SLP — Sistema de Leilão de Porquinhos: contratos (contract-first)

## Módulos e responsáveis

Um único projeto Micronaut (`co.inter.piggies`) com 3 módulos hexagonais que **só conversam por eventos Kafka**
(nenhum módulo importa classe de outro; o contrato compartilhado fica em `co.inter.piggies.contracts`).

| Módulo | Pacote | Responsável | Consumer group | Papel |
|---|---|---|---|---|
| PiggiesBuyerService | `co.inter.piggies.buyer` | Dev 1 | `slp-buyer` | `POST /buy-offers` (202), `GET /buy-offers/{id}`; marca EXECUTED ao receber `TradeExecuted` |
| PiggiesSellerService | `co.inter.piggies.seller` | Dev 2 | `slp-seller` | Igual ao Buyer, para `/sell-offers` |
| PiggiesTransactionCoordinator | `co.inter.piggies.coordinator` | Dev 3 | `slp-coordinator` | Livro de ofertas, match, débito/crédito, publica `TradeExecuted` e `ExchangeRateUpdated` |

## Fluxo

```
Cliente ──POST /buy-offers──▶ Buyer ──(outbox)──▶ slp.offers.created ──▶ Coordinator
        ◀── 202 + Location ─┘                                              │ match?
Cliente ──POST /sell-offers─▶ Seller ─(outbox)──▶ slp.offers.created ──▶──┘
                                                                            │ sim
                         Buyer ◀── slp.trades.executed ◀── Coordinator ─────┤ debita vendedor / credita comprador
                         Seller ◀─ slp.trades.executed                      │
                   outros sistemas ◀─ slp.exchange-rate.updated ◀───────────┘
```

1. Buyer/Seller gravam a oferta (`OPEN`) + evento no outbox na mesma transação e respondem **202 Accepted** com `Location` (borda assíncrona: o match acontece depois).
2. Coordinator consome `slp.offers.created`, procura a melhor contraparte e, se houver match, executa a transação.
3. Coordinator publica `slp.trades.executed` (Buyer e Seller marcam a oferta como `EXECUTED`) e `slp.exchange-rate.updated` (nova taxa USD/Piggy).

## Regras de match (Coordinator)

- Match quando `preçoCompra >= preçoVenda`. Sempre 1 Piggy, sem match parcial, sem cancelamento/expiração.
- Nova oferta de COMPRA → procura a VENDA aberta de **menor** preço; nova VENDA → procura a COMPRA aberta de **maior** preço.
- Empate de preço → a mais antiga primeiro (prioridade preço-tempo).
- Preço executado = média das duas ofertas, `BigDecimal` escala 2, `RoundingMode.HALF_EVEN`.
- Exemplo do enunciado: venda A $20, compra B $10 (sem match), compra C $30 → match A×C a **$25**.

## Tópicos Kafka

| Tópico | Produtor | Consumidores | Chave | Schema |
|---|---|---|---|---|
| `slp.offers.created` | Buyer, Seller | Coordinator | `PIGGY-USD` (fixa) | `events/offer-created.schema.json` |
| `slp.trades.executed` | Coordinator | Buyer, Seller | `tradeId` | `events/trade-executed.schema.json` |
| `slp.exchange-rate.updated` | Coordinator | outros sistemas | `PIGGY-USD` | `events/exchange-rate-updated.schema.json` |

- Chave fixa `PIGGY-USD` em `offers.created`: todas as ofertas caem na mesma partição → o Coordinator processa em ordem e sem corrida no livro de ofertas.
- Entrega at-least-once (outbox): todo consumidor deduplica por `eventId` (tabela `processed_events`).

## Decisões / simplificações (explicar se perguntarem)

- Borda assíncrona: 202 + `GET` de status, em vez de o cliente esperar o match.
- Carteira no Coordinator como ledger (lançamentos de débito/crédito); saldo = soma dos lançamentos. Sem validação de saldo nesta versão (documentado).
- Arquitetura hexagonal em cada serviço (referência: PROJETO-DTI).
