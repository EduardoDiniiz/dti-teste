package co.inter.piggies.shared.adapter.out.persistence;

import co.inter.piggies.shared.domain.port.out.ProcessedEventRepository;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;

import java.util.UUID;

@Singleton
class ProcessedEventPersistenceAdapter implements ProcessedEventRepository {

    private final EntityManager entityManager;

    ProcessedEventPersistenceAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** ON CONFLICT DO NOTHING: no Postgres um erro de unique abortaria a transação inteira. */
    @Override
    public boolean registerIfAbsent(String consumer, UUID eventId) {
        int inserted = entityManager.createNativeQuery("""
                        INSERT INTO processed_events (consumer, event_id, processed_at)
                        VALUES (:consumer, :eventId, now())
                        ON CONFLICT (consumer, event_id) DO NOTHING
                        """)
                .setParameter("consumer", consumer)
                .setParameter("eventId", eventId)
                .executeUpdate();
        return inserted == 1;
    }
}
