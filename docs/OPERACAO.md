# Operacao do gateway: balanceador, limites, metricas

## Desenho (staging)
```
cliente -> NPM -> gateway-lb (nginx :8082 / :8094) -> 2 instancias do gateway -> 6 servicos
                                   |                              |
                          rede "proxy" (compartilhada)     rede privada "gateway-backend"
```
- So o **nginx** esta na rede `proxy`. As instancias do gateway e os servicos ficam na rede privada `gateway-backend`:
  ninguem de fora chama o gateway sem passar pelo nginx, nem os servicos sem passar pelo gateway.
- O endereco publico nao mudou: o NPM continua apontando para a porta 8082 do host (agora do nginx).
- **Mais instancias**: mude `replicas` em `docker-compose-stage.yml` (stack `dev_springboot_gateway`) e faca o redeploy.
  O nginx reencontra as instancias pelo DNS do Docker a cada 5 s.
- O nginx **e uma instancia so**. Se cair, o gateway fica inacessivel (o Docker o reinicia sozinho). Para eliminar esse
  ponto unico, o proximo passo e um segundo nginx (VRRP/keepalived ou DNS) - decisao de infraestrutura.

## Dimensionamento dos limites (medido em producao, Kibana, 20/08 a 18/10/2026)
| Medida | Valor |
|---|---|
| Total de requisicoes (linhas `AUTH_AUDIT`) | 147.290, todas Basic `admin` |
| Media | ~2 req/min (maximo de 96 req/min) |
| Maior minuto | 96 req/min (16/09, 01:22) |
| **Maior segundo** | **46 req/s** (03/09, 21:00:37), uma rajada pontual |
| Crescimento previsto | +30% no proximo ano |

Calculo: pico por segundo x 1,3 = **60 req/s**; media por minuto do pior minuto x 1,3 = ~2,1 req/s.

| Onde | Valor | Por que |
|---|---|---|
| **nginx** (`limit_req`, por consumidor, compartilhado entre instancias) | **20 req/s sustentado, rajada de 150** | a rajada cobre o pico de 60 req/s com folga de 2,5x; o sustentado e ~10x o maior minuto previsto. Barra laco com defeito sem tocar no uso real |
| **gateway** (por instancia, rede de seguranca) | 50 req/s, rajada de 300 | so entra se o nginx for contornado; cada consumidor pode ter o seu no cadastro |
| **Retries** | 2, so `GET` em 502/503/erro de conexao, espera de 100 ms a 1 s | nunca em `POST`/`PATCH`/`DELETE` (nao repete escrita); nao repete timeout (nao dobra carga de relatorio pesado) |
| **Corpo da requisicao** | 2 MB no nginx e no gateway | **sem medicao**: o log nao registra o tamanho. Rotas que gravam (`salvar-massiva`, `afetados`, `gmud`) sao JSON pequeno; 2 MB e ~20x o esperado. Medir antes de reduzir |
| **Timeout de resposta** | 600 s no gateway; 900 s no nginx | relatorios pesados; o NPM do stage ainda corta em 90 s |

Para mudar sem rebuild: `GATEWAY_RATE_LIMIT_RPS`, `GATEWAY_RATE_LIMIT_BURST`, `GATEWAY_RETRIES`, `GATEWAY_MAX_BODY_SIZE`
(gateway) e o texto do nginx na propria `docker-compose-stage.yml` (`rate=20r/s`, `burst=150`, `client_max_body_size`).

## Metricas e alertas (stack `api-gateway-monitoring`)
- **Prometheus** coleta a cada 15 s as instancias do gateway (porta `8086`, nao publicada) e os 6 servicos; retencao 30 dias.
- **Grafana** (porta `3001`; primeiro acesso `admin`/`admin`, o Grafana pede a troca): painel **API Gateway** ja provisionado
  (instancias e servicos no ar, requisicoes por consumidor/servico/instancia, erros 5xx, latencia p95, circuit breaker, heap e CPU).
- **Alertas** (aba *Alerts* do Prometheus): gateway fora do ar, menos de 2 instancias, servico fora do ar, 5xx > 5%,
  p95 > 10 s, consumidor recebendo muito 4xx (limite/permissao), heap > 85%.
- Falta: **Alertmanager** (envio por e-mail/Slack/Teams). Precisa de um destino e credencial; o Prometheus ja avalia as regras.

## Rastreamento
O gateway cria/continua o `traceparent` (W3C) e o repassa; os servicos gravam `[traceId,requestId]` em cada linha de log.
Para seguir uma requisicao: pegue o `X-Trace-Id` da resposta e busque pelo id no log do gateway e dos servicos.

## DNS e troca de rede/IP de um servico
O gateway resolve os servicos pelo resolvedor do JVM com cache de 10 s. Provado em 18/10/2026: com o IP do estoque e do pessoas
**trocados entre si**, o gateway voltou a atender os dois em menos de 40 s, sem redeploy.

## Voltar atras
- Sem o nginx: mudar o compose para publicar `8082:8085` direto no `api-gateway` (1 replica) e remover `gateway-lb`.
- Sem rede privada: voltar o compose dos servicos para `proxy` e redeployar (gateway primeiro).
