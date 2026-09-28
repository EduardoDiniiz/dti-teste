package co.inter.piggies.coordinator.domain.model;

public enum OfferSide {
    BUY, SELL;

    public OfferSide opposite() {
        return this == BUY ? SELL : BUY;
    }
}
