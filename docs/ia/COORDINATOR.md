# Uso de IA — implementação do Coordinator

Ferramenta: Codex, 2026-09-28.

Solicitação: “Ok, ficou definido que os colegas vão pegar os dois serviços de entrada, siga com o desenvolvimento e os testes do Coordinator e seus testes.”

Contexto: usuário confirmou que Coordinator decide matching/transação. Repositório passou a conter REQUISITOS.md, contratos da equipe e records compartilhados. Implementação segue esses requisitos novos, não a proposta anterior. Escopo preserva Buyer/Seller e schema V1.

Produzidos: domínio puro, serviço de aplicação com portas, adaptadores JDBC/Kafka, relay restrito às saídas, testes unitários e de integração e documentação de integração. Schema ExchangeRateUpdated alinhado ao record existente; proposta antiga arquivada. .gitignore ajustado de out/ para /out/, pois a regra antiga escondia código dos pacotes adapter/out e domain/port/out.

Referências: REQUISITOS.md, NEGOCIO.md, contracts/README.md, contracts/events/offer-created.schema.json, contracts/events/trade-executed.schema.json, records e V1__create_schema.sql locais. Nenhum código de terceiros foi copiado nem foi acessado o projeto PROJETO-DTI citado no README. APIs foram verificadas por compilação e execução contra as dependências existentes.

Decisões/limites: HALF_EVEN escala 2, ledger sem checagem de saldo conforme equipe, lock por único mercado, offset após commit, outbox ao menos uma vez. Domínio/testes revisados pelo assistente; equipe deve compreender e revisar antes da entrega. Resultados de testes serão registrados ao final da validação.


Pesquisa adicional para diagnóstico: https://micronaut-projects.github.io/micronaut-sql/latest/guide/ e https://docs.micronaut.io/snapshot/api/io/micronaut/jdbc/DataSourceResolver.html. API efetiva confirmada com javap nas dependências locais micronaut-jdbc 7.1.2 e micronaut-data-connection-jdbc 5.1.4 (DataSourceResolver / DelegatingDataSourceResolver). Primeiros testes detectaram falta de entidade JPA e conexão contextual: corrigidos com OrderBookEntry para validação de schema e resolução do DataSource antes de transações JDBC explícitas.

## Resultado verificado em 2026-09-28

- `mvn clean verify`: BUILD SUCCESS; 30 testes, 0 falhas, 0 erros, 0 ignorados. São 29 do Coordinator e 1 health existente.
- `scripts/validate-coordinator-contracts.py`: 4 schemas atuais, 26 casos estáticos e 3 mensagens reais capturadas no teste Kafka aprovados.
- Docker: três containers dedicados em execução, health UP com PostgreSQL e Kafka disponíveis.
- Demonstração Kafka no Docker: venda20 + compra10 sem match; compra30 gera trade25.00, duas ofertas EXECUTED e compra10 OPEN, ledger -1/+1 e ambas as saídas recebidas.
- O banco Docker contém os dados dessa demonstração. Nenhum serviço preexistente foi parado.
- Testes não validam endpoints dos colegas nem checagem de saldo; essa verificação está fora da simplificação adotada.
- A falha de publicação no teste de persistência foi simulada após a primeira mensagem, verificando rollback da marcação e reenvio com payload/IDs estáveis.

Comandos executados usaram Java 25.0.2/Maven 3.9.16 locais. A imagem Docker eclipse-temurin:25-jre foi obtida pelo Docker e executa o JAR validado como usuário 10001. Não houve cópia de código da imagem. Os logs detalhados ficam em target/validation-logs e target/coordinator-demo.log, artefatos locais ignorados pelo Git. Nenhum commit/push foi executado automaticamente.
