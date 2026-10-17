package br.com.sebratel.contract;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** As duas rotas permitAll (SecurityConfig) precisam continuar acessiveis sem credencial. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "CONTRACT_BASE_URL", matches = ".+")
class PublicRoutesContractTest {

    private final String baseUrl = System.getenv("CONTRACT_BASE_URL").replaceAll("/+$", "");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final List<Route> routes = ManifestLoader.load();

    Stream<Route> publicRoutes() {
        return routes.stream().filter(Route::isPublic);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("publicRoutes")
    void answersWithoutCredentials(Route route) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher body = route.hasBody()
                ? HttpRequest.BodyPublishers.ofString(SampleValues.body(route))
                : HttpRequest.BodyPublishers.noBody();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + SampleValues.path(route)))
                .timeout(Duration.ofSeconds(120))
                .header("Accept", "application/json")
                .method(route.method(), body);
        if (route.hasBody()) {
            request.header("Content-Type", "application/json");
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        System.out.printf("[public] %s -> %d%n", route.label(), response.statusCode());
        assertNotEquals(401, response.statusCode());
        assertNotEquals(403, response.statusCode());
        assertFalse(List.of(405, 502, 503, 504).contains(response.statusCode()), "status " + response.statusCode());
    }
}
