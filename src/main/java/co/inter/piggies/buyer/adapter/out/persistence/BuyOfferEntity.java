package co.inter.piggies.buyer.adapter.out.persistence;

import co.inter.piggies.buyer.domain.model.BuyOfferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "buy_offers")
@Getter
@Setter
@NoArgsConstructor
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

    @Column(name = "executed_price", precision = 19, scale = 3)
    private BigDecimal executedPrice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "rejection_code", length = 50)
    private String rejectionCode;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** Lock otimista: o listener de TransactionCompleted vai atualizar o status concorrendo com leituras. */
    @Version
    private Long version;
}
