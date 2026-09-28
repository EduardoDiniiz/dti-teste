# Coordinator — implementação e integração com a equipe

## Contrato usado

Esta implementação segue **REQUISITOS.md**, **contracts/README.md** e os records de **co.inter.piggies.contracts** adicionados pela equipe: OPEN/EXECUTED, price numérico, TradeExecuted, três tópicos sem sufixo v1 e média HALF_EVEN com duas casas. Não implementa a proposta anterior PENDING/REJECTED com envelopes e validação de saldo.

Os contratos HTTP em contracts/openapi/ pertencem aos colegas. Os arquivos buyer.openapi.json, seller.openapi.json, contratos antigos de BuyOfferCreated/SellOfferCreated/TransactionCompleted/OfferRejected, exemplos antigos e kafka-topology.json ainda representam a proposta anterior. Não usá-los como contrato do Coordinator. A consolidação dos contratos Buyer/Seller deve ser feita pelos responsáveis para não sobrescrever trabalho da equipe. O script validate-contracts.py valida a proposta anterior e não deve ser usado para certificar a versão atual; use scripts/validate-coordinator-contracts.py, os testes do Coordinator e os schemas ativos abaixo.

O schema ativo da taxa foi corrigido para corresponder a ExchangeRateUpdatedMessage. A versão antiga foi preservada em docs/ia/exchange-rate-updated-proposta-anterior.json. coordinator.schema.json aponta para a entrada e as saídas atuais.

## Limites do módulo

O Coordinator não possui endpoints REST. Colegas implementam POST/GET, criação de oferta/outbox e consumo de TradeExecuted para atualizar buy_offers/sell_offers. Aqui não se lê nem se escreve nessas tabelas. Nenhuma classe de buyer/seller é importada.

| Entrada/saída | Tópico | Chave | Campos |
|---|---|---|---|
| Entrada OfferCreated | slp.offers.created | PIGGY-USD | eventId, offerId, side BUY/SELL, participantId, price, createdAt |
| Saída TradeExecuted | slp.trades.executed | tradeId | eventId, tradeId, buyOfferId, sellOfferId, buyerId, sellerId, price, executedAt |
| Saída ExchangeRateUpdated | slp.exchange-rate.updated | PIGGY-USD | eventId, tradeId, pair PIGGY-USD, rate, occurredAt |

Buyer e Seller devem ter grupos distintos (slp-buyer e slp-seller). O Coordinator usa slp-coordinator e confirmação síncrona do offset após retorno bem-sucedido do listener. Reentrega é esperada. Retries mantêm eventId/payload do evento original.

## Fluxo e decisões

1. OfferCreatedListener verifica a chave; OfferCreatedDecoder rejeita campos ausentes/extras, UUID inválido, preço em string, side inválido e preço fora da precisão suportada.
2. CoordinateOfferService inicia uma unidade de trabalho PostgreSQL e reivindica o eventId em processed_events, consumer slp-coordinator.
3. Oferta repetida idêntica não reabre o livro; mesmo offerId com payload diferente falha e faz rollback. eventId deve identificar conteúdo imutável; produtores não podem reutilizá-lo com outro payload.
4. Insere oferta OPEN. BUY busca menor venda compatível; SELL busca maior compra compatível. Empate: createdAt, depois UUID como desempate determinístico. Mesma chave Kafka preserva ordem de consumo por partição, não necessariamente ordem de criação entre produtores.
5. Sem match, confirma somente oferta e deduplicação. Com match, uma transação grava trade, fecha duas ofertas, insere -1/+1 no ledger e dois eventos na outbox.
6. Commit termina antes de confirmar o offset. O relay publica eventos persistidos; falha deixa registros pendentes.

Preço usa BigDecimal, compareTo e HALF_EVEN em escala 2: 20.01 e 20.00 resultam 20.00; 20.03 e 20.00 resultam 20.02. Isso é a regra nova da equipe, não média exata de três casas. Entradas precisam caber em NUMERIC(19,2). Timestamps são normalizados a microssegundos para comparar replays com TIMESTAMP(6).

Um advisory transaction lock PostgreSQL (732011,1) serializa alterações no único mercado. Protege também chamadas concorrentes/replays entre processos e é liberado em commit/rollback. Constraints UNIQUE das ofertas em trades reforçam a proteção. O adaptador busca uma contraparte com ORDER BY/LIMIT/FOR UPDATE, sem carregar todo o livro. Se o sistema ganhar múltiplos mercados, o particionamento do lock deve ser revisto.

Conforme simplificação da equipe: ledger sem saldo inicial/verificação de fundos; o vendedor recebe lançamento -1, o comprador +1. Não há movimentação USD. Não foi adicionada proibição de negociação do mesmo participante, pois REQUISITOS.md não a prevê. Não há expiração/cancelamento/match parcial.

## Classes

| Classe | Papel |
|---|---|
| OrderOffer, OfferSide, Trade | Modelos imutáveis com invariantes |
| MatchingEngine | Regra pura preço-tempo e média, sem Micronaut/banco/Kafka |
| CoordinateOfferUseCase | Porta de entrada |
| CoordinateOfferService | Orquestra deduplicação, livro, matching e eventos em unidade atômica |
| CoordinatorStore / Transaction | Porta de saída para transação e persistência |
| TradeEvents / OutboxMessage | Porta de geração de mensagens e registro persistente |
| OrderBookEntry | Mapeamento JPA da tabela offers para validação Hibernate; escrita permanece no adaptador JDBC |
| JdbcCoordinatorStore | Adaptador JDBC/PostgreSQL para o schema V1 existente |
| JsonTradeEvents | Serialização dos records compartilhados para os eventos de saída |
| OfferCreatedDecoder / OfferCreatedListener | Adaptador Kafka de entrada |
| CoordinatorKafkaProducer / CoordinatorOutboxRelay | Publicação das duas saídas pela outbox |
| CoordinatorFactory | Montagem dos componentes, Clock UTC e motor de matching |

DataSourceResolver resolve o DataSource real do Micronaut para as transações JDBC manuais; não se mistura commit manual com conexão contextual.

JDBC é usado explicitamente para controlar a transação, locks e o schema existente sem adicionar entidades aos módulos dos colegas. Domínio e aplicação não importam Micronaut ou JDBC. Não há alteração em V1 nem em pom.xml.

## Atenção ao relay compartilhado

Como a infraestrutura shared descrita no README ainda não existia no início desta implementação, foi criado um relay **restrito aos tópicos de saída do Coordinator**, dentro deste módulo. Ele NÃO publica slp.offers.created. Essa publicação precisa ser feita pelos módulos de entrada ou pelo relay shared da equipe.

Configurações existentes outbox.relay.enabled, interval, initial-delay e batch-size são respeitadas. Controles adicionais:

```yaml
coordinator:
  kafka:
    enabled: true
  outbox:
    enabled: true
```

Os dois controles são true por padrão. Quando o relay shared passar a publicar todos os tópicos, desative coordinator.outbox.enabled para evitar dois mecanismos concorrentes. Nunca execute ambos sobre as mesmas linhas sem protocolo de locking comum.

O relay usa um lock (732011,2) e confirma publicação antes de preencher published_at. A espera pelo Kafka ocorre no scheduler, não na borda HTTP. Timeout de 15s; falha faz rollback do lote e será retentada. Se parte do lote já saiu, pode haver duplicação: consumidores devem deduplicar. A sequência entre tópicos não é uma garantia global de observação.

Listener inválido: registra erro, repete até três vezes com intervalo de 1s e configura stopOnExhaustedRetry. Não descarta silenciosamente; exige correção e recuperação operacional. DLT e procedimento automatizado de recuperação não estão implementados.

## Testes e comandos

PowerShell na raiz do repositório, Java 25 ativo. Maven Wrapper já existe:

```powershell
# Sem Docker: domínio e decisões de aplicação
.\mvnw.bat '-Dtest=MatchingEngineTest,CoordinateOfferServiceTest' test

# Docker Desktop em modo Linux: PostgreSQL + Kafka temporários
.\mvnw.bat '-Dtest=CoordinatorPersistenceTest,CoordinatorKafkaIntegrationTest' test

# Suíte do projeto
.\mvnw.bat clean verify
```

Se utiliza Mise, pode executar `mise exec -- mvn` em lugar do wrapper, após configurar Java 25/Maven nesta pasta. O projeto atual não contém mise.toml. Testcontainers não depende de docker-compose.yml; não é preciso liberar 5432/9092 locais para os testes.

MatchingEngineTest verifica preço incompatível, igualdade, melhor preço, desempate por tempo/ID, arredondamento e entradas inválidas. CoordinateOfferServiceTest verifica oferta sem match, exemplo 20/10/30, replay e conflito de ID.

CoordinatorPersistenceTest usa PostgreSQL real: trade/ledger/outbox, rollback após falha de serialização, concorrência, conflito, consulta de melhor contraparte, falha/retry do publicador e validação da entrada. A falha do publicador é simulada; não afirma testar queda real do broker.

CoordinatorKafkaIntegrationTest envia eventos por cliente Kafka externo, espera status no banco e recebe TradeExecuted/ExchangeRateUpdated reais publicados pelo relay Micronaut. Um evento posterior serve como barreira para comprovar que o replay foi consumido antes das asserções finais. Não chama endpoints dos colegas.

Testes de integração compartilham containers da base e limpam somente tabelas do Coordinator e suas linhas de outbox/deduplicação. Executar sequencialmente; não habilitar execução paralela dessas classes sem isolar bancos/tópicos. Os testes não certificam o fluxo HTTP dos colegas.

## Integração e próximos passos da equipe

- Implementar Buyer/Seller com os records compartilhados e outbox atômica.
- Escolher um único relay por linha de outbox e publicar ofertas na chave PIGGY-USD.
- Atualizar status em transação com deduplicação separada por grupo; nunca executar transferência nos serviços de entrada.
- Confirmar interpretação de três serviços: repositório atual é uma aplicação com três módulos, não três deployments independentes.
- Consolidar os contratos antigos para que a equipe não implemente dois formatos incompatíveis.

## Validação de contratos do Coordinator

Após instalar scripts/requirements-contracts.txt em ambiente Python isolado:

```powershell
python scripts/validate-coordinator-contracts.py
```

Valida schemas ativos, casos negativos e, após o teste Kafka, três mensagens efetivamente transmitidas gravadas em target/coordinator-contract-examples. Usa Decimal para evitar erro de representação binária na validação multipleOf 0.01. Os arquivos target são artefatos locais, não devem ser comitados.


## Ambiente Docker dedicado (pronto para testes manuais)

Use o arquivo específico do Coordinator, sem alterar docker-compose.yml da equipe. Portas escolhidas para não conflitar com os projetos já existentes: aplicação/health 8083, PostgreSQL 55433, Kafka externo 59093. Internamente a JVM conecta a postgres:5432 e kafka:9092. Nenhum serviço Buyer/Seller novo é implementado por esse Compose; ele executa o JAR atual.

Na raiz do projeto, Java 25 e Docker Desktop disponíveis:

```powershell
.\mvnw.bat clean verify
docker compose -f docker-compose.coordinator.yml up -d --build
docker compose -f docker-compose.coordinator.yml ps
Invoke-RestMethod http://localhost:8083/health
docker compose -f docker-compose.coordinator.yml logs --tail 50 coordinator
```

O Dockerfile usa o JAR gerado em target/slp-0.1.jar. Após editar Java, gere o JAR e refaça o build da imagem. Health UP mostra disponibilidade, não substitui teste de matching. PostgreSQL usa usuário/senha slp para desenvolvimento. A imagem Java roda como usuário não root.

Para produzir ofertas reais no Kafka e conferir trade, ledger e eventos de saída, sem depender dos colegas:

```powershell
./scripts/coordinator-demo.ps1
```

O script prepara o classpath Maven e usa Java 25 para executar scripts/CoordinatorDemo.java. Cria dados no ambiente dedicado: venda20, compra10, compra30 e replay. Execute sem outras ofertas compatíveis inseridas manualmente para obter exatamente esse resultado. Não usa endpoints HTTP e não modifica buy_offers/sell_offers. No teste automatizado há asserções mais extensas e barreira para replay; a demonstração manual é um smoke test.

Inspecionar dados:

```powershell
docker compose -f docker-compose.coordinator.yml exec postgres psql -U slp -d slp -c 'SELECT id,side,price,status,trade_id FROM offers;'
docker compose -f docker-compose.coordinator.yml exec postgres psql -U slp -d slp -c 'SELECT * FROM trades;'
docker compose -f docker-compose.coordinator.yml exec postgres psql -U slp -d slp -c 'SELECT * FROM wallet_entries;'
docker compose -f docker-compose.coordinator.yml exec postgres psql -U slp -d slp -c 'SELECT event_id,topic,published_at FROM outbox;'
```

Para pausar sem remover containers: `docker compose -f docker-compose.coordinator.yml stop`; retomar: `docker compose -f docker-compose.coordinator.yml start`. PostgreSQL tem volume nomeado; Kafka não possui volume persistente neste ambiente. Remover/recriar o broker perde seu histórico, mesmo que o banco seja preservado. Não usar este ambiente como produção nem pressupor replay de eventos após recriar Kafka.
