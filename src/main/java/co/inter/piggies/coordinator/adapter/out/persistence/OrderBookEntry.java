package co.inter.piggies.coordinator.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Hibernate validates the V1 order-book schema; transactional writes use JdbcCoordinatorStore. */
@Entity
@Table(name = "offers")
public class OrderBookEntry {
    @Id
    private UUID id;
    @Column(name = "side", nullable = false, length = 4)
    private String side;
    @Column(name = "participant_id", nullable = false)
    private UUID participantId;
    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;
    @Column(name = "status", nullable = false, length = 20)
    private String status;
    @Column(name = "trade_id")
    private UUID tradeId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderBookEntry() { }
}
