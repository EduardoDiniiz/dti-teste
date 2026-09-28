-- buyer: alinha buy_offers ao contrato buyer.openapi.json
-- (offerId vem do cliente e já garante idempotência; execução com 3 casas; rejeição com código e motivo)
ALTER TABLE buy_offers RENAME COLUMN trade_id TO transaction_id;
ALTER TABLE buy_offers ALTER COLUMN executed_price TYPE NUMERIC(19, 3);
ALTER TABLE buy_offers DROP COLUMN idempotency_key;
ALTER TABLE buy_offers ADD COLUMN rejection_code VARCHAR(50);
ALTER TABLE buy_offers ADD COLUMN rejection_reason VARCHAR(500);
ALTER TABLE buy_offers ADD CONSTRAINT chk_buy_offers_status CHECK (status IN ('PENDING', 'EXECUTED', 'REJECTED'));
