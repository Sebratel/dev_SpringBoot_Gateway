package br.com.sebratel.gateway.observability;

import br.com.sebratel.gateway.identity.IdentityHeadersFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Correlacao e log de acesso do gateway. Roda antes da seguranca, entao registra tambem os 401:
 * <ul>
 *   <li>{@code X-Request-Id}: usa o do cliente ou gera um; vai para o servico e volta na resposta;</li>
 *   <li>uma linha {@code ACCESS} por requisicao: metodo, caminho, status, duracao, rota (servico), usuario e id.</li>
 * </ul>
 */
@Component
public class AccessLogWebFilter implements WebFilter, Ordered {

    public static final String REQUEST_ID = "X-Request-Id";

    private static final Logger ACCESS = LoggerFactory.getLogger("ACCESS");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start = System.nanoTime();
        String requestId = requestIdOf(exchange.getRequest());
        TraceContext trace = TraceContext.continueOrStart(exchange.getRequest().getHeaders().getFirst(TraceContext.HEADER));
        ServerWebExchange tracked = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.set(REQUEST_ID, requestId);
                    headers.set(TraceContext.HEADER, trace.traceparent());
                }))
                .build();
        tracked.getResponse().getHeaders().set(REQUEST_ID, requestId);
        tracked.getResponse().getHeaders().set(TraceContext.RESPONSE_HEADER, trace.traceId());

        return chain.filter(tracked).doFinally(signal -> {
            Route route = tracked.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            Object user = tracked.getAttribute(IdentityHeadersFilter.USER_ATTRIBUTE);
            var status = tracked.getResponse().getStatusCode();
            ACCESS.info("method={} path={} status={} ms={} route={} user={} requestId={} traceId={}",
                    tracked.getRequest().getMethod(), tracked.getRequest().getPath().value(),
                    status == null ? "-" : status.value(), (System.nanoTime() - start) / 1_000_000,
                    route == null ? "-" : route.getId(), user == null ? "-" : user, requestId, trace.traceId());
        });
    }

    private static String requestIdOf(ServerHttpRequest request) {
        String incoming = request.getHeaders().getFirst(REQUEST_ID);
        boolean valid = incoming != null && !incoming.isBlank() && incoming.length() <= 100
                && incoming.matches("[A-Za-z0-9._-]+");
        return valid ? incoming : UUID.randomUUID().toString();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
