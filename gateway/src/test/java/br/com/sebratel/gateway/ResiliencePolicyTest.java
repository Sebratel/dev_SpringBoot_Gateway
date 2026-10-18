package br.com.sebratel.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Politica de resiliencia: toda rota de servico (svc-*) tem circuit breaker com fallback do proprio servico e retry
 * so de GET; o corpo da requisicao tem limite para todas as rotas.
 */
@SpringBootTest
class ResiliencePolicyTest {

    @Autowired
    private RouteDefinitionLocator routeLocator;

    @Autowired
    private Environment environment;

    private List<RouteDefinition> serviceRoutes() {
        return routeLocator.getRouteDefinitions().collectList().block().stream()
                .filter(route -> route.getId().startsWith("svc-")).toList();
    }

    private static FilterDefinition filter(RouteDefinition route, String name) {
        return route.getFilters().stream().filter(f -> name.equals(f.getName())).findFirst()
                .orElseThrow(() -> new AssertionError(route.getId() + " sem filtro " + name));
    }

    @Test
    void thereAreSixServiceRoutes() {
        assertEquals(6, serviceRoutes().size());
    }

    @Test
    void everyServiceRouteHasACircuitBreakerWithItsOwnFallback() {
        for (RouteDefinition route : serviceRoutes()) {
            Map<String, String> args = filter(route, "CircuitBreaker").getArgs();
            String service = route.getId().substring("svc-".length());
            assertEquals(service, args.get("name"), route.getId());
            assertEquals("forward:/fallback/" + service, args.get("fallbackUri"), route.getId());
            assertEquals("502,503,504", args.get("statusCodes"), route.getId());
        }
    }

    @Test
    void retryIsOnlyForGetAndNeverForBusinessErrors() {
        for (RouteDefinition route : serviceRoutes()) {
            Map<String, String> args = filter(route, "Retry").getArgs();
            assertEquals("GET", args.get("methods"), route.getId());
            assertEquals("BAD_GATEWAY,SERVICE_UNAVAILABLE", args.get("statuses"), route.getId());
            assertEquals("java.io.IOException", args.get("exceptions"), route.getId());
            assertEquals("2", args.get("retries"), route.getId());
        }
    }

    @Test
    void circuitBreakerWrapsTheRetrySoOneCallCountsOnce() {
        for (RouteDefinition route : serviceRoutes()) {
            assertEquals("CircuitBreaker", route.getFilters().get(0).getName(), route.getId());
            assertEquals("Retry", route.getFilters().get(1).getName(), route.getId());
        }
    }

    @Test
    void thereIsABodySizeLimitForEveryRoute() {
        assertEquals("RequestSize", environment.getProperty("spring.cloud.gateway.server.webflux.default-filters[0].name"));
        assertEquals("2MB", environment.getProperty("spring.cloud.gateway.server.webflux.default-filters[0].args.maxSize"));
    }

    @Test
    void circuitBreakerOpensOnFailureRateAndRecoversByItself() {
        assertEquals("50", environment.getProperty("resilience4j.circuitbreaker.configs.default.failure-rate-threshold"));
        assertEquals("true", environment.getProperty(
                "resilience4j.circuitbreaker.configs.default.automatic-transition-from-open-to-half-open-enabled"));
        assertTrue(Integer.parseInt(environment.getProperty(
                "resilience4j.circuitbreaker.configs.default.minimum-number-of-calls")) >= 10);
    }
}
