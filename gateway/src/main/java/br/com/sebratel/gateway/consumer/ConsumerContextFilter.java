package br.com.sebratel.gateway.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Principal;

/**
 * Identifica QUEM esta chamando (o consumidor) e aplica o que o cadastro permite:
 * <ul>
 *   <li>grava o nome do consumidor no exchange (log, limite de taxa) e envia {@code X-Consumer} aos servicos;</li>
 *   <li>se o consumidor tem {@code allowed-routes}, qualquer outra rota responde {@code 403};</li>
 *   <li>conta as requisicoes por consumidor e classe de status ({@code gateway_consumer_requests_total}).
 *       Usuarios de JWT entram agregados como {@code jwt}: o numero de series fica limitado.</li>
 * </ul>
 * Autorizacao por papel/regra de negocio continua sendo de cada aplicacao; aqui e so "este sistema pode usar esta rota".
 */
@Component
public class ConsumerContextFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Consumer";
    public static final String NAME_ATTRIBUTE = "gateway.consumer";
    public static final String CONSUMER_ATTRIBUTE = "gateway.consumer.object";

    private static final String JWT = "jwt";
    private static final String ANONYMOUS = "anonymous";

    private final ConsumerRegistry registry;
    private final MeterRegistry meters;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public ConsumerContextFilter(ConsumerRegistry registry, MeterRegistry meters) {
        this.registry = registry;
        this.meters = meters;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .map(this::consumerOf)
                .defaultIfEmpty(new Resolved(ANONYMOUS, null))
                .flatMap(resolved -> {
                    exchange.getAttributes().put(NAME_ATTRIBUTE, resolved.name());
                    if (resolved.consumer() != null) {
                        exchange.getAttributes().put(CONSUMER_ATTRIBUTE, resolved.consumer());
                    }
                    ServerWebExchange forwarded = exchange.mutate()
                            .request(request -> request.headers(headers -> headers.set(HEADER, resolved.name())))
                            .build();
                    Mono<Void> result = allowed(resolved.consumer(), forwarded)
                            ? chain.filter(forwarded) : forbidden(forwarded, resolved.name());
                    return result.doFinally(signal -> count(resolved.name(), forwarded));
                });
    }

    private Resolved consumerOf(Principal principal) {
        if (principal instanceof JwtAuthenticationToken) {
            return new Resolved(JWT, null);
        }
        if (principal instanceof Authentication authentication && authentication.isAuthenticated()) {
            return registry.findByUsername(authentication.getName())
                    .map(consumer -> new Resolved(consumer.name(), consumer))
                    .orElse(new Resolved(ANONYMOUS, null));
        }
        return new Resolved(ANONYMOUS, null);
    }

    boolean allowed(Consumer consumer, ServerWebExchange exchange) {
        if (consumer == null || consumer.allowedRoutes().isEmpty()) {
            return true;
        }
        String request = exchange.getRequest().getMethod() + " " + exchange.getRequest().getPath().value();
        return consumer.allowedRoutes().stream().anyMatch(pattern -> matcher.match(pattern, request));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String consumer) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer body = response.bufferFactory().wrap(("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"O consumidor '"
                + consumer + "' nao tem permissao para esta rota.\"}").getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(body));
    }

    private void count(String consumer, ServerWebExchange exchange) {
        var status = exchange.getResponse().getStatusCode();
        String statusClass = status == null ? "unknown" : (status.value() / 100) + "xx";
        meters.counter("gateway.consumer.requests", "consumer", consumer, "status", statusClass).increment();
    }

    /** Depois da identidade (+100) e antes do limite de taxa (+200). */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 110;
    }

    private record Resolved(String name, Consumer consumer) {
    }
}
