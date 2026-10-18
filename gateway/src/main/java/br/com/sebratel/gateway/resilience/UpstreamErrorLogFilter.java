package br.com.sebratel.gateway.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Registra a excecao REAL de cada tentativa que falha ao falar com um servico. Roda depois do circuit breaker e do
 * retry (ordem 100 contra 1 e 2 nas rotas) e logo antes de a chamada ser feita: ve o erro antes de ele virar 503 ou
 * ser repetido. Sem isso, o log so mostra "503 do fallback" e a causa se perde.
 */
@Component
public class UpstreamErrorLogFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger("UPSTREAM_ERROR");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).doOnError(error -> {
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            Throwable root = error;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            LOGGER.warn("falha ao chamar {} ({} {}): {} | causa: {}", route == null ? "-" : route.getId(),
                    exchange.getRequest().getMethod(), exchange.getRequest().getPath().value(), error, root);
        });
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
