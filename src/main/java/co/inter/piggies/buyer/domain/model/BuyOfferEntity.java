package co.inter.piggies.buyer.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "buy_offers")
public class BuyOfferEntity {
    @Id
    private UUID id;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BuyOfferStatus status;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "execution_price", precision = 19, scale = 3)
    private BigDecimal executionPrice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected BuyOfferEntity() {
    }

    public BuyOfferEntity(UUID id, UUID buyerId, BigDecimal price, Instant createdAt) {
        this.id = id;
        this.buyerId = buyerId;
        this.price = price;
        this.status = BuyOfferStatus.PENDING;
        this.createdAt = createdAt;
    }

    public void execute(UUID transactionId, BigDecimal executionPrice) {
        if (status == BuyOfferStatus.EXECUTED) {
            return;
        }
        this.transactionId = transactionId;
        this.executionPrice = executionPrice;
        this.status = BuyOfferStatus.EXECUTED;
    }

    public UUID getId() { return id; }
    public UUID getBuyerId() { return buyerId; }
    public BigDecimal getPrice() { return price; }
    public BuyOfferStatus getStatus() { return status; }
    public UUID getTransactionId() { return transactionId; }
    public BigDecimal getExecutionPrice() { return executionPrice; }
    public Instant getCreatedAt() { return createdAt; }
}
