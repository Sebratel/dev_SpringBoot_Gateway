package br.com.sebratel.contract;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Contrato HTTP das rotas de UM servico de destino, contra uma URL base (o monolito hoje, o gateway depois).
 * Cada subclasse fixa o servico; as classes e as rotas rodam em paralelo (junit-platform.properties).
 *
 * <p>Variaveis de ambiente:
 * <ul>
 *   <li>{@code CONTRACT_BASE_URL}: obrigatoria; sem ela os testes sao ignorados. Use {@code https://}: o HttpClient
 *       nao segue o redirect de http para https.</li>
 *   <li>{@code CONTRACT_TOKEN}: ID token Google (enviado como Bearer).</li>
 *   <li>{@code CONTRACT_BASIC_AUTH}: alternativa ao token, header completo ({@code Basic xxxx}). Somente em stage e
 *       somente na variavel de ambiente: nunca em arquivo versionado.</li>
 *   <li>{@code CONTRACT_RECORD=true}: grava os golden files em vez de comparar.</li>
 *   <li>{@code CONTRACT_GOLDEN_DIR}: pasta dos golden files (padrao {@code golden}).</li>
 *   <li>{@code CONTRACT_TIMEOUT_SECONDS}: timeout por requisicao (padrao 120).</li>
 * </ul>
 *
 * <p>Seguranca: rotas que alteram dados ({@code safe=false}) NUNCA sao chamadas autenticadas; so se confere o 401
 * sem credencial, que nao tem efeito colateral.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractServiceContractTest {

    /** Status que indicam rota nao encaminhada ou servico fora do ar, nunca uma resposta de negocio. */
    private static final Set<Integer> ROUTING_FAILURES = Set.of(405, 502, 503);
    private static final int GATEWAY_TIMEOUT = 504;

    private static final List<Route> ROUTES = ManifestLoader.load();
    private static final KnownSlowRoutes SLOW = KnownSlowRoutes.load();

    private final String baseUrl = trimTrailingSlash(System.getenv("CONTRACT_BASE_URL"));
    private final String authorization = resolveAuthorization();
    private final boolean record = "true".equalsIgnoreCase(System.getenv("CONTRACT_RECORD"));
    private final Duration timeout = Duration.ofSeconds(
            Long.parseLong(Optional.ofNullable(System.getenv("CONTRACT_TIMEOUT_SECONDS")).orElse("120")));
    private final GoldenStore goldens = new GoldenStore(
            Path.of(Optional.ofNullable(System.getenv("CONTRACT_GOLDEN_DIR")).orElse("golden")));
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** Servico de destino exercitado por esta classe (valor do campo {@code service} do manifesto). */
    abstract String service();

    Stream<Route> protectedRoutes() {
        return routesOfService().filter(route -> !route.isPublic());
    }

    Stream<Route> safeRoutes() {
        return routesOfService().filter(Route::safe);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("protectedRoutes")
    void rejectsCallsWithoutCredentials(Route route) throws IOException, InterruptedException {
        HttpResponse<String> response = timed(route, null);
        assertEquals(401, response.statusCode(), "rota protegida deve responder 401 sem credencial");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("safeRoutes")
    void matchesGoldenFile(Route route) throws IOException, InterruptedException {
        Assumptions.assumeTrue(authorization != null, "sem CONTRACT_TOKEN/CONTRACT_BASIC_AUTH: so a checagem de 401 roda");

        boolean slow = SLOW.contains(route);
        HttpResponse<String> response;
        try {
            response = timed(route, authorization);
        } catch (java.net.http.HttpTimeoutException e) {
            Assumptions.assumeTrue(!slow, "rota lenta conhecida estourou o timeout do cliente");
            throw e;
        }
        int status = response.statusCode();

        if (slow && status == GATEWAY_TIMEOUT) {
            return;
        }
        assertFalse(ROUTING_FAILURES.contains(status) || status == GATEWAY_TIMEOUT,
                "rota nao encaminhada ou servico indisponivel: " + status);
        if (slow) {
            return;
        }

        Golden actual = new Golden(status, List.copyOf(ShapeExtractor.of(response.body())));
        if (record) {
            goldens.write(route, actual);
            return;
        }
        Optional<Golden> expected = goldens.read(route);
        if (expected.isPresent()) {
            assertEquals(expected.get(), actual, "resposta divergente do golden file");
        }
    }

    HttpResponse<String> timed(Route route, String authorizationHeader) throws IOException, InterruptedException {
        long start = System.nanoTime();
        HttpResponse<String> response = call(route, authorizationHeader);
        System.out.printf("[%s] %s -> %d (%d ms)%n", service(), route.label(), response.statusCode(),
                (System.nanoTime() - start) / 1_000_000);
        return response;
    }

    private HttpResponse<String> call(Route route, String authorizationHeader) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher body = route.hasBody()
                ? HttpRequest.BodyPublishers.ofString(SampleValues.body(route))
                : HttpRequest.BodyPublishers.noBody();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + SampleValues.path(route)))
                .timeout(timeout)
                .header("Accept", "application/json")
                .method(route.method(), body);
        if (route.hasBody()) {
            request.header("Content-Type", "application/json");
        }
        if (authorizationHeader != null) {
            request.header("Authorization", authorizationHeader);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Stream<Route> routesOfService() {
        return ROUTES.stream().filter(route -> service().equals(route.service()));
    }

    /** Header Authorization vindo do ambiente: Basic tem precedencia sobre Bearer; nenhum dos dois = null. */
    private static String resolveAuthorization() {
        String basic = System.getenv("CONTRACT_BASIC_AUTH");
        if (basic != null && !basic.isBlank()) {
            return basic;
        }
        String token = System.getenv("CONTRACT_TOKEN");
        return token == null || token.isBlank() ? null : "Bearer " + token;
    }

    private static String trimTrailingSlash(String url) {
        return url == null ? null : url.replaceAll("/+$", "");
    }
}
