# Consumidores do gateway: um usuario por sistema

## Por que
Hoje **todo** o trafego entra com um unico usuario Basic (`admin`): 147.290 requisicoes de 20/08 a 18/10, 100% do total
(medido no Kibana de producao; nenhuma chamada com JWT). Com um usuario so:
- nao da para saber **quem** chama o que, nem de onde vem um pico;
- revogar um sistema derruba todos (a senha e a mesma);
- nao ha limite nem permissao por sistema: um laco com defeito de um consumidor consome a cota de todos
  (nos dados, `DELETE /afetados/protocol/{n}` aparece 30 a 50 vezes por protocolo: alguem repete a chamada).

## O que muda
Cada sistema/time ganha **um consumidor** com:

| Campo | Para que serve |
|---|---|
| `username` + senha | credencial propria (senha aleatoria de 32 caracteres; no cadastro fica so o **hash bcrypt**) |
| `rate-limit` (opcional) | limite proprio de req/s e rajada; sem isso vale o padrao |
| `allowed-routes` (opcional) | lista de rotas que o sistema pode chamar (`"GET /api/v1/matrix"`, aceita `*` e `**`); outra rota = `403` |
| `expires` (opcional) | data em que a credencial deixa de valer (obriga a renovar; some sozinha) |
| `enabled: false` | bloqueia na hora, sem apagar |

O gateway passa a registrar o consumidor em **tudo**: no log (`ACCESS ... user=`), no cabecalho `X-Consumer` enviado ao
servico, na metrica `gateway_consumer_requests_total{consumer,status}` (Grafana: "Requisicoes por segundo, por consumidor")
e na chave do limite de taxa (compartilhado entre as instancias, no nginx).

Autorizacao por regra de negocio continua sendo de cada aplicacao. `allowed-routes` e so privilegio minimo: "este sistema
so precisa destas rotas".

## Migracao sem quebrar ninguem
O `admin` atual **continua funcionando** como o consumidor `legacy-admin` (variaveis `SECURITY_USER`/`SECURITY_PASS`),
sem restricao. Nada muda para quem ainda nao migrou.

1. **Descobrir quem e quem.** O log do nginx (`api-gateway-lb`) traz o IP real (`xff=`) e a rota de cada chamada.
   Agrupe por IP: cada grupo e um sistema. Ate isso, use a proposta abaixo (por familia de rota).
2. **Criar os consumidores** (comando abaixo), um por sistema.
3. **Entregar a credencial** a cada dono, por canal seguro, e pedir a troca do `admin` pela credencial nova.
4. **Acompanhar** no Grafana a linha `legacy-admin` cair. Quando chegar a zero por 1 semana, remover `SECURITY_USER` e
   `SECURITY_PASS` da stack: o `admin` deixa de existir.
5. **Limitar**: com cada sistema identificado, ajustar `rate-limit` e `allowed-routes` ao uso real.

### Proposta inicial (por familia de rota, com o uso medido)
| Consumidor sugerido | Rotas (uso 20/08 a 18/10) | Observacao |
|---|---|---|
| `app-corporativo` | `GET employee/is-corporativo` (74.095), `GET employee/get-person-id-by-email` (2.413) | maior volume; so leitura |
| `massivas-leitura` | `GET massivas/recuperar-pelo-banco` (52.715), `GET sites/search` (84) | so leitura |
| `massivas-protocolos` | `POST afetados` (622), `DELETE afetados/protocol/*` (~11 mil), `PATCH afetados/protocol/*`, `POST massivas/salvar-massiva-via-api` (678), `DELETE massivas/finalizar-chamado-via-api` (581), `POST massivas/abrir-protocolo-infra-via-api` (211) | grava dados; **investigar a repeticao de DELETE por protocolo** |
| `gmud-automacao` | `POST gmud/abrir-via-api`, `DELETE gmud/encerrar-via-api`, `POST gmud/relato-via-api` | poucas chamadas; grava |
| `radius-relatorios` | `GET radius/duplicate-calling-stations`, `GET radius/duplicate-prefixes` | so leitura |
| `comercial-relatorios` | `GET relatorios/clientes-nomes-duplicados` | so leitura |
| `splitters-alertas` | `POST splitters/alertas-protocolo-pendente` (122), `GET splitters/solicitacoes/cliente/*` | leitura |

## Criar um consumidor
O comando gera a senha **uma vez** e a entrada do cadastro (so o hash). Nada fica gravado em disco.

```bash
docker run --rm --entrypoint java ghcr.io/sebratel/api-gateway:staging \
  -cp app.jar -Dloader.main=br.com.sebratel.gateway.consumer.ConsumerCli \
  org.springframework.boot.loader.launch.PropertiesLauncher n8n-massivas "Automacao de massivas (time NOC)"
```

1. Copie o bloco **ENTREGUE AO CONSUMIDOR** para quem vai usar (e nao guarde).
2. Cole o bloco **COLE NO CADASTRO** num arquivo `consumers.yml` (modelo: `consumers.example.yml`) e ajuste
   `rate-limit`, `allowed-routes` e `expires`.
3. Converta o arquivo para uma linha e coloque na variavel `GATEWAY_CONSUMERS_B64` da stack `api-gateway-staging-novo`
   (Portainer -> Stacks -> Environment variables) e faca o redeploy:
   ```bash
   base64 -w0 consumers.yml        # Linux/macOS/Git Bash
   ```
   Alternativa sem redeploy para trocar o cadastro: montar o arquivo no host e apontar `GATEWAY_CONSUMERS_FILE`; o gateway
   le o arquivo de novo a cada 30 s.

## Regras de seguranca
- Nunca versionar `consumers.yml` com hashes reais nem qualquer senha. O hash bcrypt nao revela a senha, mas tambem nao
  precisa estar no Git.
- Senha de consumidor so existe na mao de quem a usa. Perdeu: crie outra e bloqueie a antiga (`enabled: false`).
- Credencial com `expires` forca a renovacao (recomendado: no maximo 12 meses).
- Basic so deve trafegar por HTTPS (o NPM termina o TLS; a porta 8082/8094 em HTTP e para uso interno).
