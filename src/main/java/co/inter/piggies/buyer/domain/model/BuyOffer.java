package co.inter.piggies.buyer.domain.model;

import co.inter.piggies.buyer.domain.exception.InvalidPriceException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado: lance de compra de 1 Piggy. Só registra a intenção (PENDING);
 * quem junta compra e venda é o coordinator.
 */
public final class BuyOffer {

    public static final int PRICE_SCALE = 2;
    public static final int MAX_INTEGER_DIGITS = 12;

    private final UUID offerId;
    private final UUID buyerId;
    private final BigDecimal priceUsd;
    private final Instant createdAt;
    private final BuyOfferStatus status;
    private final UUID transactionId;
    private final BigDecimal executionPriceUsd;
    private final String rejectionCode;
    private final String rejectionReason;
    private final Long version;

    private BuyOffer(UUID offerId, UUID buyerId, BigDecimal priceUsd, Instant createdAt, BuyOfferStatus status,
                     UUID transactionId, BigDecimal executionPriceUsd, String rejectionCode, String rejectionReason,
                     Long version) {
        this.offerId = Objects.requireNonNull(offerId);
        this.buyerId = Objects.requireNonNull(buyerId);
        this.priceUsd = priceUsd;
        this.createdAt = createdAt;
        this.status = status;
        this.transactionId = transactionId;
        this.executionPriceUsd = executionPriceUsd;
        this.rejectionCode = rejectionCode;
        this.rejectionReason = rejectionReason;
        this.version = version;
    }

    /**
     * Novo lance: o offerId é gerado aqui, o preço é validado e começa PENDING.
     * buyerId é opcional (simplificação do MVP): se não vier, também é gerado.
     */
    public static BuyOffer create(UUID buyerId, BigDecimal priceUsd, Clock clock) {
        return new BuyOffer(UUID.randomUUID(), buyerId != null ? buyerId : UUID.randomUUID(), validPrice(priceUsd), clock.instant().truncatedTo(ChronoUnit.MICROS),
                BuyOfferStatus.PENDING, null, null, null, null, null);
    }

    /** Reconstrói uma oferta persistida, sem revalidar. */
    public static BuyOffer restore(UUID offerId, UUID buyerId, BigDecimal priceUsd, Instant createdAt,
                                   BuyOfferStatus status, UUID transactionId, BigDecimal executionPriceUsd,
                                   String rejectionCode, String rejectionReason, Long version) {
        return new BuyOffer(offerId, buyerId, priceUsd, createdAt, status, transactionId, executionPriceUsd,
                rejectionCode, rejectionReason, version);
    }

    private static BigDecimal validPrice(BigDecimal price) {
        if (price == null) {
            throw new InvalidPriceException("priceUsd is required");
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPriceException("priceUsd must be greater than zero");
        }
        BigDecimal normalized = price.stripTrailingZeros();
        if (normalized.scale() > PRICE_SCALE) {
            throw new InvalidPriceException("priceUsd must have at most " + PRICE_SCALE + " decimal places");
        }
        if (normalized.precision() - normalized.scale() > MAX_INTEGER_DIGITS) {
            throw new InvalidPriceException("priceUsd must have at most " + MAX_INTEGER_DIGITS + " integer digits");
        }
        return price.setScale(PRICE_SCALE, RoundingMode.UNNECESSARY);
    }

    public UUID offerId() {
        return offerId;
    }

    public UUID buyerId() {
        return buyerId;
    }

    public BigDecimal priceUsd() {
        return priceUsd;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public BuyOfferStatus status() {
        return status;
    }

    public UUID transactionId() {
        return transactionId;
    }

    public BigDecimal executionPriceUsd() {
        return executionPriceUsd;
    }

    public String rejectionCode() {
        return rejectionCode;
    }

    public String rejectionReason() {
        return rejectionReason;
    }

    public Long version() {
        return version;
    }
}
