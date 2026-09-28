package co.inter.piggies.buyer.adapter.out.persistence;

import co.inter.piggies.buyer.domain.model.BuyOffer;
import co.inter.piggies.buyer.domain.port.out.BuyOfferRepository;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.UUID;

@Singleton
class BuyOfferPersistenceAdapter implements BuyOfferRepository {

    private final BuyOfferJpaRepository repository;

    BuyOfferPersistenceAdapter(BuyOfferJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<BuyOffer> findById(UUID offerId) {
        return repository.findById(offerId).map(BuyOfferPersistenceAdapter::toDomain);
    }

    @Override
    public void insert(BuyOffer offer) {
        BuyOfferEntity entity = new BuyOfferEntity();
        entity.setId(offer.offerId());
        entity.setBuyerId(offer.buyerId());
        entity.setPrice(offer.priceUsd());
        entity.setStatus(offer.status());
        entity.setCreatedAt(offer.createdAt());
        repository.persist(entity);
    }

    private static BuyOffer toDomain(BuyOfferEntity e) {
        return BuyOffer.restore(e.getId(), e.getBuyerId(), e.getPrice(), e.getCreatedAt(), e.getStatus(),
                e.getTransactionId(), e.getExecutedPrice(), e.getRejectionCode(), e.getRejectionReason(),
                e.getVersion());
    }
}
