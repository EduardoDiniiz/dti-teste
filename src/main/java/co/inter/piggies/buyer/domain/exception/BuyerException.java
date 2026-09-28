package co.inter.piggies.buyer.domain.exception;

/** Erros de negócio do módulo buyer; o adapter REST traduz cada um para HTTP + code do contrato. */
public abstract sealed class BuyerException extends RuntimeException
        permits InvalidPriceException, BuyOfferNotFoundException {

    protected BuyerException(String message) {
        super(message);
    }
}
