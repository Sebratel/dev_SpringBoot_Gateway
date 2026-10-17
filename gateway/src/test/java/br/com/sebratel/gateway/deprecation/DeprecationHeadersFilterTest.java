package br.com.sebratel.gateway.deprecation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeprecationHeadersFilterTest {

    private static final Path MANIFEST = Path.of("..", "contract-tests", "src", "test", "resources", "manifest.json");

    private final DeprecatedRoutes routes = new DeprecatedRoutes();
    private final DeprecationHeadersFilter filter = new DeprecationHeadersFilter(routes, "2026-10-17", "2027-01-17");

    private HttpHeaders responseHeadersFor(String method, String path) {
        MockServerHttpRequest request = MockServerHttpRequest.method(org.springframework.http.HttpMethod.valueOf(method), path).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = next -> next.getResponse().setComplete();
        filter.filter(exchange, chain).block();
        return exchange.getResponse().getHeaders();
    }

    @Test
    void deprecatedRouteGetsDeprecationAndSunset() {
        HttpHeaders headers = responseHeadersFor("GET", "/api/v1/afetados");

        assertEquals("@1792195200", headers.getFirst("Deprecation"));
        assertEquals("Sun, 17 Jan 2027 00:00:00 GMT", headers.getFirst("Sunset"));
    }

    @Test
    void routeWithASuccessorGetsALinkWithTheVariablesFilledIn() {
        HttpHeaders headers = responseHeadersFor("GET", "/api/v1/usuario-afetado/contract/123");

        assertEquals("<" + "/api/v1/afetados/contract/123" + ">; rel=\"successor-version\"", headers.getFirst(HttpHeaders.LINK));
    }

    @Test
    void routeInUseIsNotMarked() {
        HttpHeaders headers = responseHeadersFor("GET", "/api/v1/afetados/contract/123");

        assertNull(headers.getFirst("Deprecation"));
        assertNull(headers.getFirst("Sunset"));
    }

    @Test
    void theListHas136RoutesAndEveryOneExistsInTheManifest() throws IOException {
        Set<String> manifest = new HashSet<>();
        for (JsonNode route : new ObjectMapper().readTree(MANIFEST.toFile())) {
            manifest.add(route.get("method").asText() + " " + route.get("path").asText());
        }

        assertEquals(136, routes.all().size());
        for (DeprecatedRoute route : routes.all()) {
            assertTrue(manifest.contains(route.key()), "rota obsoleta fora do manifesto: " + route.key());
        }
        assertFalse(routes.keys().isEmpty());
    }

    @Test
    void successorOfADeprecatedRouteIsNotDeprecatedItself() {
        for (DeprecatedRoute route : routes.all()) {
            if (route.successorTemplate() != null) {
                assertFalse(routes.keys().contains(route.method() + " " + route.successorTemplate()), route.key());
            }
        }
    }
}
