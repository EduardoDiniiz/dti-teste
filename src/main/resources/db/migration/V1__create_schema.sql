-- ===== buyer: ofertas de compra (fonte da verdade do status para o comprador)

CREATE TABLE buy_offers (
    id              UUID PRIMARY KEY,
    buyer_id        UUID           NOT NULL,
    price           NUMERIC(19, 2) NOT NULL CHECK (price > 0),
    status          VARCHAR(20)    NOT NULL,
    transaction_id  UUID,
    execution_price NUMERIC(19, 3),
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    executed_at     TIMESTAMP(6) WITH TIME ZONE,
    idempotency_key VARCHAR(100) UNIQUE,
    version         BIGINT         NOT NULL,
    CONSTRAINT buy_offers_status_check CHECK (status IN ('PENDING', 'EXECUTED'))
);

-- ===== seller: ofertas de venda

CREATE TABLE sell_offers (
    id              UUID PRIMARY KEY,
    seller_id        UUID           NOT NULL,
    price           NUMERIC(19, 2) NOT NULL CHECK (price > 0),
    status          VARCHAR(20)    NOT NULL,
    trade_id        UUID,
    executed_price  NUMERIC(19, 2),
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    executed_at     TIMESTAMP(6) WITH TIME ZONE,
    idempotency_key VARCHAR(100) UNIQUE,
    version         BIGINT         NOT NULL
);

-- ===== coordinator: livro de ofertas, trades e carteira

-- Livro de ofertas: espelho das ofertas recebidas de Buyer e Seller.
CREATE TABLE offers (
    id             UUID PRIMARY KEY,
    side           VARCHAR(4)     NOT NULL CHECK (side IN ('BUY', 'SELL')),
    participant_id UUID           NOT NULL,
    price          NUMERIC(19, 2) NOT NULL CHECK (price > 0),
    status         VARCHAR(20)    NOT NULL,
    trade_id       UUID,
    created_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

-- Busca da melhor contraparte aberta (preço, depois a mais antiga).
CREATE INDEX idx_offers_open ON offers (side, price, created_at) WHERE status = 'OPEN';

CREATE TABLE trades (
    id            UUID PRIMARY KEY,
    buy_offer_id  UUID           NOT NULL UNIQUE REFERENCES offers (id),
    sell_offer_id UUID           NOT NULL UNIQUE REFERENCES offers (id),
    buyer_id      UUID           NOT NULL,
    seller_id     UUID           NOT NULL,
    price         NUMERIC(19, 2) NOT NULL CHECK (price > 0),
    executed_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

-- Carteira como ledger: saldo = soma dos lançamentos por participante e ativo.
CREATE TABLE wallet_entries (
    id             BIGSERIAL PRIMARY KEY,
    participant_id UUID           NOT NULL,
    asset          VARCHAR(10)    NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL,
    trade_id       UUID           NOT NULL REFERENCES trades (id),
    created_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_wallet_participant ON wallet_entries (participant_id, asset);

-- ===== shared: outbox + idempotência por consumidor

CREATE TABLE outbox (
    id           BIGSERIAL PRIMARY KEY,
    event_id     UUID         NOT NULL UNIQUE,
    topic        VARCHAR(100) NOT NULL,
    message_key  VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP(6) WITH TIME ZONE
);

CREATE INDEX idx_outbox_pending ON outbox (id) WHERE published_at IS NULL;

-- (consumer, event_id): o mesmo TradeExecuted é consumido por buyer E seller; cada um deduplica o seu.
CREATE TABLE processed_events (
    consumer     VARCHAR(50) NOT NULL,
    event_id     UUID        NOT NULL,
    processed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    PRIMARY KEY (consumer, event_id)
);
