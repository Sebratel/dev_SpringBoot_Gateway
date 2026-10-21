# contract-tests

Garante que as **164 rotas HTTP** do BFF continuam idênticas depois da migração para o gateway
(`specs/API_GATEWAY.md`). O mesmo teste roda contra o monólito (linha de base) e contra o gateway.

## Peças

| Peça | Onde | O que faz |
|---|---|---|
| Manifesto | `src/test/resources/manifest.json` | Lista das 164 rotas: método, path (com todos os aliases), serviço de destino, auth, se é segura, parâmetros e corpo |
| Gerador/guarda | `src/test/java/.../bff/contract/RouteManifestTest` (no monólito, `dev_SpringBoot_APIGateway`) | Gera o manifesto por reflexão nos controllers, sem banco. **Falha se uma rota do monólito mudar sem o manifesto ser atualizado** |
| `ManifestSanityTest` | este módulo | Confere 164 rotas únicas, contagem por serviço (84/45/14/8/8/5), só 2 rotas públicas, 3 GET com corpo |
| `*ContractTest` (um por serviço) + `PublicRoutesContractTest` | este módulo | Exercitam as rotas contra uma URL base (ver abaixo). Rodam **em paralelo** |

## Atualizar o manifesto (quando o monólito mudar de propósito)

```bash
./mvnw test -Dtest=RouteManifestTest -Dmanifest.write=true
```

Revise o diff de `manifest.json` e o inventário em `specs/API_GATEWAY_ENDPOINTS.md`.

## Rodar contra uma URL base

```bash
CONTRACT_BASE_URL=http://localhost:8085 CONTRACT_TOKEN=<id token google> ./mvnw -pl contract-tests test
```

| Variável | Efeito |
|---|---|
| `CONTRACT_BASE_URL` | Obrigatória; sem ela o `LiveContractTest` é ignorado |
| `CONTRACT_TOKEN` | ID token Google válido. Sem ele só roda a checagem de autenticação |
| `CONTRACT_BASIC_AUTH` | Alternativa ao token: header completo, ex.: `Basic xxxx`. Só em stage e só na variável de ambiente; tem precedência sobre `CONTRACT_TOKEN` |
| `CONTRACT_TIMEOUT_SECONDS` | Timeout por requisição (padrão 120) |
| `CONTRACT_RECORD=true` | Grava as respostas em `golden/` (status + formato do corpo) em vez de comparar |
| `CONTRACT_GOLDEN_DIR` | Pasta dos golden files (padrão `golden`) |

O que é verificado:
1. **Sem token**, toda rota protegida responde **401**; as 2 públicas (`GET /api/v1/matrix` e `GET /api/v1/afetados/contract/{id}`) não.
2. **Com token**, toda rota *segura* (`safe=true`: GET e 3 POSTs de consulta) não pode responder 405/502/503/504, e a resposta tem de bater com o golden file (status + estrutura de campos, sem valores).

### Paralelismo e rotas lentas
- As classes (`MassivasContractTest`, `ComercialContractTest`, `RadiusContractTest`, ...) e cada rota rodam em paralelo,
  6 por vez por padrão. Para aliviar o stage: `-Djunit.jupiter.execution.parallel.config.fixed.parallelism=4`.
- `src/test/resources/known-slow-routes.txt` lista 18 rotas que estouram o proxy do stage (**504 em ~90 s**) mesmo no
  monólito (consumo, última conexão, primeiro pagante, primeira autenticação). Nelas o teste aceita 504 e não compara golden.
  Quando uma rota passar a responder a tempo, remova a linha e grave o golden file dela.
- Cada requisição imprime uma linha `[servico] METODO path -> status (ms)`, para acompanhar o progresso.

### Segurança dos testes
Rotas que **alteram dados** (`safe=false`, 40 rotas: afetados POST/DELETE/PATCH, massivas e GMUD POST/DELETE, histórico PATCH)
**nunca são chamadas autenticadas**. Para elas só se confere o 401 sem token, que não tem efeito colateral.
Mesmo assim, **grave os golden files em stage**, nunca em produção, e sem dados reais no repositório.

## Fluxo da migração
1. Fase 0: gravar os golden files do monólito em stage (`CONTRACT_RECORD=true`) e confirmar o teste verde.
2. Fase 2: rodar o mesmo teste com `CONTRACT_BASE_URL` apontando para o gateway.
3. Fase 3: a cada serviço extraído, repetir.
