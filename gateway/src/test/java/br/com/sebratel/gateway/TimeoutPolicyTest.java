package br.com.sebratel.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Politica de tempo do gateway:
 * <ul>
 *   <li>toda rota tem limite padrao de resposta (60 s); estourou, o gateway responde 504;</li>
 *   <li>so as rotas lentas conhecidas ficam sem limite ({@code response-timeout: -1}), e a lista e a mesma
 *       de contract-tests/known-slow-routes.txt.</li>
 * </ul>
 */
@SpringBootTest
class TimeoutPolicyTest {

    private static final Path KNOWN_SLOW = Path.of("..", "contract-tests", "src", "test", "resources", "known-slow-routes.txt");

    @Autowired
    private Environment environment;

    @Autowired
    private RouteDefinitionLocator routeLocator;

    @Test
    void everyRouteHasADefaultResponseTimeout() {
        assertEquals("60s", environment.getProperty("spring.cloud.gateway.server.webflux.httpclient.response-timeout"));
    }

    @Test
    void circuitBreakerDoesNotApplyATimeLimiter() {
        assertEquals("true", environment.getProperty("spring.cloud.circuitbreaker.resilience4j.disable-time-limiter"));
    }

    @Test
    void onlyTheKnownSlowRoutesHaveNoResponseTimeout() throws IOException {
        List<RouteDefinition> definitions = routeLocator.getRouteDefinitions().collectList().block();

        Set<String> unlimited = definitions.stream()
                .filter(route -> Integer.valueOf(-1).equals(route.getMetadata().get("response-timeout")))
                .map(GatewayRoutesTestSupport::pathOf)
                .collect(Collectors.toSet());

        Set<String> expected = Files.readAllLines(KNOWN_SLOW).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(line -> line.substring(line.indexOf(' ') + 1))
                .collect(Collectors.toSet());

        assertEquals(18, expected.size());
        assertEquals(expected, unlimited, "rotas sem limite de tempo divergem de known-slow-routes.txt");
    }

    @Test
    void noOtherRouteOverridesTheDefaultLimit() {
        List<RouteDefinition> definitions = routeLocator.getRouteDefinitions().collectList().block();
        for (RouteDefinition route : definitions) {
            Object override = route.getMetadata().get("response-timeout");
            assertTrue(override == null || Integer.valueOf(-1).equals(override), route.getId());
        }
    }
}
