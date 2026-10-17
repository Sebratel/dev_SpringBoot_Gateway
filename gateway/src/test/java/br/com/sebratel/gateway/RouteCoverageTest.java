package br.com.sebratel.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Cobertura do roteamento: todo caminho do manifesto (156 rotas do BFF original) precisa estar roteado
 * para o servico dono dele, e nenhuma rota extra pode existir.
 */
@SpringBootTest
class RouteCoverageTest {

    private static final Path MANIFEST = Path.of("..", "contract-tests", "src", "test", "resources", "manifest.json");

    @Autowired
    private RouteDefinitionLocator routeLocator;

    @Test
    void everyManifestPathIsRoutedToItsService() throws IOException {
        Map<String, Set<String>> expected = new HashMap<>();
        for (JsonNode route : new ObjectMapper().readTree(MANIFEST.toFile())) {
            expected.computeIfAbsent(route.get("service").asText(), key -> new TreeSet<>()).add(route.get("path").asText());
        }

        Map<String, Set<String>> routed = new HashMap<>();
        List<RouteDefinition> definitions = routeLocator.getRouteDefinitions().collectList().block();
        for (RouteDefinition definition : definitions) {
            if (definition.getId().startsWith("svc-")) {
                Set<String> paths = new TreeSet<>();
                definition.getPredicates().stream()
                        .filter(predicate -> "Path".equals(predicate.getName()))
                        .forEach(predicate -> predicate.getArgs().values().forEach(paths::add));
                routed.put(definition.getId().substring("svc-".length()), paths);
            }
        }

        assertFalse(expected.isEmpty());
        assertEquals(expected, routed, "caminhos roteados divergem do manifesto");
    }

    @Test
    void slowRoutesWinOverTheServiceRoutes() {
        List<RouteDefinition> definitions = routeLocator.getRouteDefinitions().collectList().block();
        for (RouteDefinition definition : definitions) {
            if (definition.getId().startsWith("slow-")) {
                assertEquals(-1, definition.getOrder(), definition.getId());
            }
        }
    }
}
