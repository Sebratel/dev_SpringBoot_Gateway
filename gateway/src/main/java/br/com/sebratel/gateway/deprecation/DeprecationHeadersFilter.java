package br.com.sebratel.gateway.deprecation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Sinaliza rotas obsoletas ao consumidor (RFC 9745 e RFC 8594), sem alterar status nem corpo:
 * {@code Deprecation: @epoch}, {@code Sunset: <data HTTP>} e {@code Link: <sucessora>; rel="successor-version"}.
 * Cada chamada a uma rota obsoleta tambem gera uma linha no log DEPRECATED_ROUTE.
 *
 * <p>Vive no gateway (e nao nos servicos) porque a lista de rotas obsoletas e da API publica, nao de um servico.
 */
public class DeprecationHeadersFilter implements GlobalFilter, Ordered {

    private static final Logger AUDIT = LoggerFactory.getLogger("DEPRECATED_ROUTE");

    private final DeprecatedRoutes deprecatedRoutes;
    private final String deprecationValue;
    private final String sunsetValue;

    public DeprecationHeadersFilter(DeprecatedRoutes deprecatedRoutes, String since, String sunset) {
        this.deprecatedRoutes = deprecatedRoutes;
        this.deprecationValue = since.isBlank() ? "true" : "@" + startOfDayEpoch(since);
        this.sunsetValue = sunset.isBlank() ? null : httpDate(sunset);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String method = exchange.getRequest().getMethod().name();
        String path = normalized(exchange.getRequest().getPath().pathWithinApplication().value());
        Optional<DeprecatedRoutes.Match> match = deprecatedRoutes.find(method, path);
        match.ifPresent(found -> {
            Optional<String> successor = found.route().successorFor(found.uriVariables());
            exchange.getResponse().beforeCommit(() -> {
                HttpHeaders headers = exchange.getResponse().getHeaders();
                headers.set("Deprecation", deprecationValue);
                if (sunsetValue != null) {
                    headers.set("Sunset", sunsetValue);
                }
                successor.ifPresent(link -> headers.add(HttpHeaders.LINK, "<" + link + ">; rel=\"successor-version\""));
                return Mono.empty();
            });
            AUDIT.info("method={} path={} successor={}", method, found.route().template(), successor.orElse("-"));
        });
        return chain.filter(exchange);
    }

    private static String normalized(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static long startOfDayEpoch(String isoDate) {
        return LocalDate.parse(isoDate).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
    }

    private static String httpDate(String isoDate) {
        ZonedDateTime date = LocalDate.parse(isoDate).atStartOfDay(ZoneOffset.UTC);
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(date);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }
}
