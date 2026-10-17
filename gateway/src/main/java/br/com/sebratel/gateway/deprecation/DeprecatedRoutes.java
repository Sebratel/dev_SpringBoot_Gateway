package br.com.sebratel.gateway.deprecation;

import org.springframework.core.io.ClassPathResource;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rotas obsoletas, lidas de {@code deprecated-routes.txt}. Fonte unica para o filtro de headers e para o OpenAPI.
 */
public class DeprecatedRoutes {

    private static final String RESOURCE = "deprecated-routes.txt";
    private static final String SUCCESSOR_SEPARATOR = " -> ";

    private final List<DeprecatedRoute> routes;

    public DeprecatedRoutes() {
        this(RESOURCE);
    }

    public DeprecatedRoutes(String resource) {
        this.routes = load(resource);
    }

    /** Resultado de uma busca: a rota obsoleta e as variaveis do caminho chamado. */
    public record Match(DeprecatedRoute route, Map<String, String> uriVariables) {
    }

    public Optional<Match> find(String method, String requestPath) {
        for (DeprecatedRoute route : routes) {
            if (route.method().equalsIgnoreCase(method)) {
                Optional<Map<String, String>> variables = route.match(requestPath);
                if (variables.isPresent()) {
                    return Optional.of(new Match(route, variables.get()));
                }
            }
        }
        return Optional.empty();
    }

    public Set<String> keys() {
        return routes.stream().map(DeprecatedRoute::key).collect(Collectors.toUnmodifiableSet());
    }

    public List<DeprecatedRoute> all() {
        return routes;
    }

    private static List<DeprecatedRoute> load(String resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(resource).getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(DeprecatedRoutes::parse)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel ler " + resource, e);
        }
    }

    private static DeprecatedRoute parse(String line) {
        String successor = null;
        String left = line;
        int arrow = line.indexOf(SUCCESSOR_SEPARATOR);
        if (arrow >= 0) {
            left = line.substring(0, arrow).trim();
            successor = line.substring(arrow + SUCCESSOR_SEPARATOR.length()).trim();
        }
        int space = left.indexOf(' ');
        if (space <= 0) {
            throw new IllegalStateException("Linha invalida em deprecated-routes.txt: " + line);
        }
        String method = left.substring(0, space).trim();
        String template = left.substring(space + 1).trim();
        return new DeprecatedRoute(method, template, PathPatternParser.defaultInstance.parse(template), successor);
    }
}
