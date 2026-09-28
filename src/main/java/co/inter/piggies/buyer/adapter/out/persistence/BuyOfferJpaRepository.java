package co.inter.piggies.buyer.adapter.out.persistence;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.UUID;

@Repository
interface BuyOfferJpaRepository extends JpaRepository<BuyOfferEntity, UUID> {
}
