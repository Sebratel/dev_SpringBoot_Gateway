package br.com.sebratel.contract;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Garante que o manifesto continua coerente com o desenho da migracao (specs/API_GATEWAY*.md). */
class ManifestSanityTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("[{]([^}]+)[}]");

    private final List<Route> routes = ManifestLoader.load();

    @Test
    void hasAllRoutes() {
        assertEquals(158, routes.size());
    }

    @Test
    void routesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (Route route : routes) {
            assertTrue(seen.add(route.label()), "rota duplicada: " + route.label());
        }
    }

    @Test
    void routesPerServiceMatchTheSpec() {
        Map<String, Long> perService = routes.stream()
                .collect(Collectors.groupingBy(Route::service, Collectors.counting()));
        assertEquals(Map.of("massivas", 78L, "comercial", 45L, "radius", 14L,
                "estoque", 8L, "splitters", 8L, "pessoas", 5L), perService);
    }

    @Test
    void everyRouteLivesUnderApiV1() {
        routes.forEach(route -> assertTrue(route.path().startsWith("/api/v1/"), route.label()));
    }

    @Test
    void placeholdersMatchDeclaredPathVariables() {
        for (Route route : routes) {
            Matcher matcher = PLACEHOLDER.matcher(route.path());
            Set<String> inPath = new HashSet<>();
            while (matcher.find()) {
                inPath.add(matcher.group(1));
            }
            assertEquals(inPath, new HashSet<>(route.pathVariables()), route.label());
        }
    }

    @Test
    void onlyTheTwoDocumentedRoutesArePublic() {
        Set<String> publicRoutes = routes.stream().filter(Route::isPublic).map(Route::label).collect(Collectors.toSet());
        assertEquals(Set.of("GET /api/v1/afetados/contract/{contractId}", "GET /api/v1/matrix"), publicRoutes);
    }

    @Test
    void getRoutesWithBodyAreTheKnownThree() {
        Set<String> getWithBody = routes.stream()
                .filter(route -> "GET".equals(route.method()) && route.hasBody())
                .map(Route::label).collect(Collectors.toSet());
        assertEquals(Set.of("GET /api/v1/matrix", "GET /api/v1/weekly-reports/vendedor",
                "GET /api/v1/apoio-semanal/vendedor"), getWithBody);
    }

    @Test
    void mutatingRoutesAreNeverMarkedSafe() {
        for (Route route : routes) {
            if (route.method().equals("DELETE") || route.method().equals("PATCH")) {
                assertTrue(!route.safe(), route.label());
            }
        }
    }

    @Test
    void knownSlowRoutesExistAndAreSafe() {
        Set<String> safeLabels = routes.stream().filter(Route::safe).map(Route::label).collect(Collectors.toSet());
        for (String label : KnownSlowRoutes.load().labels()) {
            assertTrue(safeLabels.contains(label), "rota lenta conhecida nao existe no manifesto ou nao e segura: " + label);
        }
    }
}
