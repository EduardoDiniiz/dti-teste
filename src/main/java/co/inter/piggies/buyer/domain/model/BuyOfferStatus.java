package co.inter.piggies.buyer.domain.model;

/** PENDING: aceita, aguardando publicação/match. EXECUTED e REJECTED são terminais (não regridem). */
public enum BuyOfferStatus {
    PENDING, EXECUTED, REJECTED
}
