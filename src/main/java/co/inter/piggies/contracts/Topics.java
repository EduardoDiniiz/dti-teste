package co.inter.piggies.contracts;

/** Tópicos do SLP (contracts/README.md). */
public final class Topics {

    public static final String OFFERS_CREATED = "slp.offers.created";
    public static final String TRADES_EXECUTED = "slp.trades.executed";
    public static final String EXCHANGE_RATE_UPDATED = "slp.exchange-rate.updated";

    /** Chave fixa: todas as ofertas na mesma partição, processadas em ordem pelo Coordinator. */
    public static final String PAIR_KEY = "PIGGY-USD";

    private Topics() {
    }
}
