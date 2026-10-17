# dev_SpringBoot_Gateway

API Gateway da Sebratel (Spring Cloud Gateway, WebFlux) e o módulo `contract-tests`, que garante que as **156 rotas** do BFF
continuam idênticas atrás do gateway. Parte da migração descrita em `Sebratel/dev_SpringBoot_APIGateway` (`specs/API_GATEWAY.md`).

> **Estado: esqueleto.** O gateway sobe, valida o JWT do Google e tem a política de tempo definida, mas **ainda não roteia o
> tráfego**: só as 18 rotas lentas estão declaradas (apontando ao monólito). As demais rotas são geradas a partir do manifesto
> nas fases 2 e 3 da migração.

## Módulos
| Módulo | O que é |
|---|---|
| `gateway` | `api-gateway` (porta 8085): autenticação, CORS, resiliência, rotas. **Sem lógica de domínio e sem banco** |
| `contract-tests` | Manifesto das rotas, testes de sanidade e testes de paridade por serviço, rodando contra qualquer URL base |

## Política de tempo
- Limite padrão de resposta de **60 s** (`GATEWAY_DEFAULT_RESPONSE_TIMEOUT`): estourou, o gateway responde **504**.
- **Sem limite** só nas 18 rotas lentas conhecidas (`gateway/src/main/resources/routes/slow-routes.yml`, metadata `response-timeout: -1`).
  A lista é a mesma de `contract-tests/src/test/resources/known-slow-routes.txt`; o `TimeoutPolicyTest` garante que são iguais.
- Isso só vale atrás do gateway: o proxy do stage corta em ~90 s e precisa de `proxy_read_timeout` maior nessas rotas.

## Desenvolvimento
O `sebratel-parent` vem do GitHub Packages (privado); crie um servidor `github` no `~/.m2/settings.xml` com token `read:packages`.

```bash
./mvnw verify
./mvnw -pl gateway spring-boot:run
```

Testes de paridade contra uma URL base: ver `contract-tests/README.md`.

## Atualizar o manifesto das rotas
O manifesto é gerado pelo monólito (`RouteManifestTest`, em `dev_SpringBoot_APIGateway`). Quando ele mudar, copie
`docs/contract/manifest.json` de lá para `contract-tests/src/test/resources/manifest.json` e ajuste as contagens do `ManifestSanityTest`.

## Regras do repositório
`main` só recebe PR com 1 aprovação e `ci-ok` verde; Conventional Commits; `.env` nunca vai para o Git.
