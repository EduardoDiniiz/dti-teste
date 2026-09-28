package co.inter.piggies.shared.domain.port.out;

import java.util.UUID;

public interface ProcessedEventRepository {

    /**
     * Registra o eventId para o consumidor na transação corrente.
     *
     * @param consumer quem está processando (ex.: "buyer", "seller", "coordinator")
     * @return false se esse consumidor já tinha processado o evento
     */
    boolean registerIfAbsent(String consumer, UUID eventId);
}
