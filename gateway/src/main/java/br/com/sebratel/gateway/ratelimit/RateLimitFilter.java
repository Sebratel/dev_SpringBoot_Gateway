package br.com.sebratel.gateway.ratelimit;

import br.com.sebratel.gateway.identity.IdentityHeadersFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de taxa por consumidor (balde de fichas, em memoria: o gateway roda em uma instancia; com mais de uma o
 * limite vale por instancia). A chave e o usuario autenticado (e-mail ou usuario Basic) e, na falta dele, o IP.
 *
 * <p>Estourou: {@code 429} com {@code Retry-After}. Valores em {@code gateway.rate-limit.*}.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final long IDLE_NANOS = 10L * 60 * 1_000_000_000L;

    private final boolean enabled;
    private final double refillPerSecond;
    private final int burst;
    private final int maxKeys;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${gateway.rate-limit.enabled:true}") boolean enabled,
            @Value("${gateway.rate-limit.requests-per-second:30}") double refillPerSecond,
            @Value("${gateway.rate-limit.burst:100}") int burst,
            @Value("${gateway.rate-limit.max-keys:10000}") int maxKeys) {
        this.enabled = enabled;
        this.refillPerSecond = refillPerSecond;
        this.burst = burst;
        this.maxKeys = maxKeys;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }
        long now = System.nanoTime();
        Bucket bucket = buckets.computeIfAbsent(keyOf(exchange), key -> new Bucket(burst, now));
        long waitSeconds = bucket.tryAcquire(now, refillPerSecond, burst);
        if (buckets.size() > maxKeys) {
            buckets.entrySet().removeIf(entry -> now - entry.getValue().lastSeen() > IDLE_NANOS);
        }
        if (waitSeconds == 0) {
            return chain.filter(exchange);
        }
        return tooManyRequests(exchange, waitSeconds);
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, long waitSeconds) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("Retry-After", Long.toString(waitSeconds));
        response.getHeaders().set("X-RateLimit-Limit", Double.toString(refillPerSecond));
        DataBuffer body = response.bufferFactory().wrap(
                ("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Limite de requisicoes excedido. Tente novamente em "
                        + waitSeconds + " s.\"}").getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(body));
    }

    private static String keyOf(ServerWebExchange exchange) {
        Object user = exchange.getAttribute(IdentityHeadersFilter.USER_ATTRIBUTE);
        if (user != null && !"-".equals(user.toString())) {
            return "u:" + user;
        }
        InetSocketAddress address = exchange.getRequest().getRemoteAddress();
        return "ip:" + (address == null || address.getAddress() == null ? "unknown" : address.getAddress().getHostAddress());
    }

    /** Depois da identidade (que define o usuario) e antes do roteamento. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }

    /** Balde de fichas: {@code burst} fichas no maximo, reposicao de {@code refillPerSecond} por segundo. */
    static final class Bucket {
        private double tokens;
        private long lastNanos;

        Bucket(int burst, long nowNanos) {
            this.tokens = burst;
            this.lastNanos = nowNanos;
        }

        /** 0 = liberado; senao, os segundos ate haver uma ficha. */
        synchronized long tryAcquire(long nowNanos, double refillPerSecond, int burst) {
            tokens = Math.min(burst, tokens + (nowNanos - lastNanos) / 1_000_000_000.0 * refillPerSecond);
            lastNanos = nowNanos;
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            return Math.max(1, (long) Math.ceil((1 - tokens) / refillPerSecond));
        }

        synchronized long lastSeen() {
            return lastNanos;
        }
    }
}
