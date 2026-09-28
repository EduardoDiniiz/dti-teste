package co.inter.piggies.shared.adapter.out.persistence;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;

@Repository
public interface OutboxJpaRepository extends JpaRepository<OutboxEntity, Long> {

    /** SKIP LOCKED: várias instâncias do relay podem rodar sem publicar o mesmo evento duas vezes. */
    @Query(value = """
            SELECT * FROM outbox
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEntity> lockPending(int limit);

    List<OutboxEntity> findByMessageKey(String messageKey);
}
