# SLP — Sistema de Leilão de Porquinhos

Enunciado: [NEGOCIO.md](NEGOCIO.md) · Estratégia: [ESTRATEGIA.md](ESTRATEGIA.md) · Requisitos: [REQUISITOS.md](REQUISITOS.md) · Contratos: [contracts/](contracts/README.md) · Uso de IA: [docs/ia/](docs/ia/README.md)

## Estrutura

Um único projeto Micronaut com **3 módulos hexagonais** (um por dev) que se comunicam **somente por eventos Kafka**.

```
src/main/java/co/inter/piggies/
├── Application.java
├── contracts/      ← Topics + records das mensagens Kafka (espelho de contracts/events/*.schema.json)
├── shared/         ← infra comum: OutboxWriter, OutboxRelay, SlpProducer, TransactionRunner,
│                     ProcessedEventRepository (idempotência por consumidor), ClockFactory
├── buyer/          ← Dev 1   domain / application / adapter (rest, kafka, persistence, messaging)
├── seller/         ← Dev 2   mesma estrutura do buyer
└── coordinator/    ← Dev 3   domain / application / adapter (kafka, persistence, messaging)
src/main/resources/db/migration/V1__create_schema.sql   ← tabelas dos 3 módulos + outbox + processed_events
src/test/java/co/inter/piggies/support/                 ← Containers, IntegrationTest, KafkaTestClient
```

| Módulo | Pacote | Tabelas (V1) | Consumer group |
|---|---|---|---|
| Buyer | `co.inter.piggies.buyer` | `buy_offers` | `slp-buyer` |
| Seller | `co.inter.piggies.seller` | `sell_offers` | `slp-seller` |
| Coordinator | `co.inter.piggies.coordinator` | `offers`, `trades`, `wallet_entries` | `slp-coordinator` |
| shared | `co.inter.piggies.shared` | `outbox`, `processed_events` | — |

Stack: Java 25, Micronaut 5.1.5, Maven, Micronaut Data JPA, PostgreSQL 16, Flyway, Kafka (apache/kafka-native 4.3.1), JUnit 5, AssertJ, Awaitility, Testcontainers.

## Rodar

```bash
podman compose up -d     # Postgres + Kafka
./mvnw mn:run            # porta 8081
./mvnw test              # o Testcontainers sobe Postgres e Kafka
```

Com Podman no Windows (testado), antes do `./mvnw test`:

```bash
export DOCKER_HOST="npipe:////./pipe/podman-machine-default"
export TESTCONTAINERS_RYUK_DISABLED=true
```

## Coordinator implementado

O módulo Coordinator e seus testes estão documentados em [docs/COORDINATOR.md](docs/COORDINATOR.md). O ambiente Docker dedicado usa `docker-compose.coordinator.yml` (aplicação 8083, PostgreSQL 55433 e Kafka 59093), preservando o Compose original da equipe. `scripts/coordinator-demo.ps1` envia ofertas diretamente ao Kafka e verifica trade, ledger e eventos; não depende dos endpoints Buyer/Seller. A implementação segue os contratos novos de `contracts/README.md`, com média HALF_EVEN em duas casas e ledger sem validação de saldo.
