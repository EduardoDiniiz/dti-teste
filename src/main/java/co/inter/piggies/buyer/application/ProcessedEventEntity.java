package co.inter.piggies.buyer.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventEntity.Key.class)
public class ProcessedEventEntity {
    @Id
    @Column(nullable = false, length = 50)
    private String consumer;

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventEntity() { }

    public record Key(String consumer, UUID eventId) implements Serializable { }
}
