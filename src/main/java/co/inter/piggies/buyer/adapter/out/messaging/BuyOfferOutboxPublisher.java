package co.inter.piggies.buyer.adapter.out.messaging;

import co.inter.piggies.buyer.domain.model.BuyOffer;
import co.inter.piggies.buyer.domain.port.out.BuyOfferEventPublisher;
import co.inter.piggies.contracts.Topics;
import co.inter.piggies.shared.adapter.out.messaging.OutboxWriter;
import jakarta.inject.Singleton;

import java.util.UUID;

/**
 * Grava BuyOfferCreated no outbox (mesma transação da oferta). O OutboxRelay publica no Kafka depois.
 * Tópico e chave conforme contracts/kafka-topology.json.
 */
@Singleton
class BuyOfferOutboxPublisher implements BuyOfferEventPublisher {

    /** Tópico combinado com o coordinator (difere de contracts/kafka-topology.json, que indica slp.offers.v1). */
    static final String TOPIC = "slp.exchange-rate.updated";

    private final OutboxWriter outbox;

    BuyOfferOutboxPublisher(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void offerCreated(BuyOffer offer) {
        UUID eventId = UUID.randomUUID();
        BuyOfferCreatedMessage message = new BuyOfferCreatedMessage(eventId, BuyOfferCreatedMessage.EVENT_TYPE,
                BuyOfferCreatedMessage.SCHEMA_VERSION, offer.createdAt(),
                new BuyOfferCreatedMessage.Payload(offer.offerId(), offer.buyerId(),
                        offer.priceUsd().toPlainString(), offer.createdAt()));
        outbox.write(eventId, TOPIC, Topics.PAIR_KEY, message);
    }
}
