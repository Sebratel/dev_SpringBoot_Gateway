package br.com.sebratel.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.reactive.CorsWebFilter;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CORS do gateway: o preflight e respondido sem credencial e os avisos de deprecacao ficam visiveis ao front. */
class CorsTest {

    private static final String ORIGIN = "https://app.sebratel.com.br";

    private final CorsWebFilter filter = new CorsWebFilter(new SecurityConfig().corsConfigurationSource());

    @Test
    void preflightIsAnsweredWithTheAllowedOriginAndMethods() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("http://gateway.local/api/v1/estoque/tecnico")
                        .header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "PATCH")
                        .build());

        filter.filter(exchange, next -> Mono.error(new IllegalStateException("o preflight nao vai ao servico"))).block();

        assertNotEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertEquals(ORIGIN, exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"));
        assertTrue(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Methods").contains("PATCH"));
    }

    @Test
    void actualResponsesExposeTheDeprecationAndRequestIdHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "http://gateway.local/api/v1/matrix")
                        .header("Origin", ORIGIN).build());

        filter.filter(exchange, next -> Mono.empty()).block();

        String exposed = exchange.getResponse().getHeaders().getFirst("Access-Control-Expose-Headers");
        assertTrue(exposed.contains("Deprecation") && exposed.contains("Sunset") && exposed.contains("Link")
                && exposed.contains("X-Request-Id"), exposed);
    }
}
