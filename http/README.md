# Requisições de teste manual

Suba a aplicação (`./mvnw mn:run`, porta 8081) e use:

- **IntelliJ:** abra `buyer/buy-offers.http` e clique no ▶ de cada requisição (guarda o `offerId` para os GETs).
- **curl (Git Bash):**

```bash
curl -i -X POST localhost:8081/buy-offers -H "Content-Type: application/json" -d @http/buyer/compra-b-10.json
curl -i -X POST localhost:8081/buy-offers -H "Content-Type: application/json" -d @http/buyer/compra-c-30.json
curl -s localhost:8081/buy-offers/<offerId>
```

| Arquivo | Comprador | Valor | Esperado |
|---|---|---|---|
| `buyer/compra-b-10.json` | B `bbbbbbbb-…` | $10.00 | 202 · fica PENDING (sem venda ≤ 10) |
| `buyer/compra-c-30.json` | C `cccccccc-…` | $30.00 | 202 · EXECUTED a 25.000 depois do match com a venda A de $20 |
| `buyer/compra-invalida.json` | B | $10.001 | 400 (mais de 2 casas) |

Os ids dos participantes são fixos para facilitar a conferência; o `offerId` é gerado pelo servidor.
