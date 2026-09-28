package co.inter.piggies.buyer.application;

import co.inter.piggies.buyer.domain.model.BuyOfferEntity;
import co.inter.piggies.buyer.domain.model.BuyOfferStatus;
import co.inter.piggies.contracts.BuyOfferCreatedMessage;
import co.inter.piggies.contracts.Topics;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Singleton
public class BuyOfferService {
    private final EntityManager entityManager;
    private final JsonMapper jsonMapper;

    public BuyOfferService(EntityManager entityManager, JsonMapper jsonMapper) {
        this.entityManager = entityManager;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public AcceptedOffer accept(UUID offerId, UUID buyerId, BigDecimal price) {
        BuyOfferEntity existing = find(offerId);
        if (existing != null) {
            if (!existing.getBuyerId().equals(buyerId) || existing.getPrice().compareTo(price) != 0) {
                throw new OfferConflictException();
            }
            return new AcceptedOffer(existing.getId(), existing.getStatus(), false);
        }

        Instant createdAt = Instant.now();
        BuyOfferEntity offer = new BuyOfferEntity(offerId, buyerId, price, createdAt);
        entityManager.persist(offer);
        try {
            UUID eventId = UUID.randomUUID();
            String payload = jsonMapper.writeValueAsString(new BuyOfferCreatedMessage(
                    eventId, "BuyOfferCreated", 1, Instant.now(),
                    new BuyOfferCreatedMessage.Payload(offerId, buyerId, price.toPlainString(), createdAt)));
            entityManager.persist(new OutboxEntity(eventId, Topics.OFFERS, Topics.PAIR_KEY, payload));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize buy offer event", exception);
        }
        return new AcceptedOffer(offerId, BuyOfferStatus.PENDING, true);
    }

    @Transactional
    public BuyOfferEntity get(UUID offerId) {
        BuyOfferEntity offer = find(offerId);
        if (offer == null) {
            throw new OfferNotFoundException();
        }
        return offer;
    }

    @Transactional
    public void execute(TransactionCompletedMessage message) {
        BuyOfferEntity offer = find(message.buyOfferId());
        if (offer != null && offer.getStatus() == BuyOfferStatus.PENDING) {
            offer.execute(message.transactionId(), message.executionPriceUsd());
        }
    }

    private BuyOfferEntity find(UUID offerId) {
        try {
            return entityManager.createQuery("from BuyOfferEntity o where o.id = :id", BuyOfferEntity.class)
                    .setParameter("id", offerId)
                    .getSingleResult();
        } catch (NoResultException ignored) {
            return null;
        }
    }

    public record AcceptedOffer(UUID offerId, BuyOfferStatus status, boolean created) { }
    public record TransactionCompletedMessage(UUID eventId, UUID transactionId, UUID buyOfferId,
                                               BigDecimal executionPriceUsd) { }

    public static final class OfferConflictException extends RuntimeException { }
    public static final class OfferNotFoundException extends RuntimeException { }
}
