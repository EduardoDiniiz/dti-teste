Regras de negócio (valem para todos)
- RN01: toda oferta é de exatamente 1 Piggy, com preço em USD maior que zero e até 2 casas decimais.
- RN02: há match quando preço de compra >= preço de venda.
- RN03: o preço executado é a média das duas ofertas: (compra + venda) / 2, com escala 2 e HALF_EVEN.
- RN04: nunca há match parcial. Uma oferta participa de no máximo um match.
- RN05: não existe cancelamento nem expiração. A oferta fica aberta até dar match.
- RN06: no match, o vendedor é debitado em 1 Piggy e o comprador é creditado em 1 Piggy.
- RN07: a cada match, a nova taxa (USD/Piggy) é publicada para outros sistemas.
- RN08: prioridade: compra nova casa com a venda aberta de menor preço; venda nova casa com a compra aberta de maior preço; em empate de preço, a mais antiga primeiro.

PiggiesBuyerService
- RF01: POST /buy-offers com buyerId e price cria a oferta com status OPEN e responde 202 com Location.
- RF02: publica OfferCreated (side=BUY) em slp.offers.created.
- RF03: GET /buy-offers/{id} devolve a oferta com status OPEN ou EXECUTED; se não existir, 404 em problem+json.
- RF04: consome slp.trades.executed e marca a oferta como EXECUTED, com tradeId, executedPrice e executedAt.
- RF05: entrada inválida (preço ≤ 0, mais de 2 casas, buyerId ausente) responde 400.

PiggiesSellerService
- RF06 a RF10: iguais aos do Buyer, com /sell-offers, sellerId e side=SELL.

PiggiesTransactionCoordinator
- RF11: consome slp.offers.created e registra a oferta no livro de ofertas.
- RF12: ao registrar, procura a melhor contraparte aberta (RN02 e RN08).
- RF13: se houver match, na mesma transação: fecha as duas ofertas, cria o trade com o preço médio e lança débito e crédito na carteira.
- RF14: publica TradeExecuted em slp.trades.executed.
- RF15: publica ExchangeRateUpdated em slp.exchange-rate.updated.
- RF16: sem match, a oferta fica aberta no livro.

Requisitos não funcionais
- RNF01 (contract-first): OpenAPI para Buyer e Seller e JSON Schema para os 3 eventos, commitados antes do código. Já estão em contracts/.
- RNF02 (borda assíncrona): o POST não espera o match; responde 202 e o status é consultado depois.
- RNF03 (confiabilidade): eventos saem pelo outbox, na mesma transação da gravação.
- RNF04 (idempotência): consumidores deduplicam por eventId. O POST aceita Idempotency-Key.
- RNF05 (ordem e concorrência): a chave PIGGY-USD em offers.created faz o Coordinator processar uma oferta de cada vez.
- RNF06: BigDecimal com compareTo para dinheiro.
- RNF07: testes unitários (regra de match) e de integração (Testcontainers com Postgres e Kafka) obrigatórios.
- RNF08: arquitetura hexagonal em cada serviço.
- RNF09: prompts de IA e código de referência commitados em docs/ia/.

Critérios de aceite do fluxo mínimo, usando o exemplo do enunciado
1. POST /sell-offers com A a $20 → 202, status OPEN.
2. POST /buy-offers com B a $10 → 202, status OPEN, e nenhum trade.
3. POST /buy-offers com C a $30 → 202, e depois:
    - existe um trade A×C a $25.00;
    - A e C ficam EXECUTED com executedPrice 25.00, e B continua OPEN;
    - a carteira fica com vendedor A −1 Piggy e comprador C +1 Piggy;
    - slp.exchange-rate.updated recebe rate = 25.00.

Primeira entrega (MVP), em ordem
1. Coordinator: MatchingEngine com os testes unitários de RN02, RN03 e RN08 e o exemplo acima. Não depende de nada e prova a regra.
2. Buyer e Seller: RF01 a RF03 (POST 202 e GET), com teste de integração.
3. Coordinator: RF11 a RF16 consumindo o Kafka.
4. Buyer e Seller: RF04, fechando o ciclo de status.