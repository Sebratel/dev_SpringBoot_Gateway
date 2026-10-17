package br.com.sebratel.gateway.deprecation;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.UriUtils;
import org.springframework.web.util.pattern.PathPattern;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Rota marcada como obsoleta: metodo HTTP, o template da rota e, quando existe, o alias do mesmo handler que
 * recebeu trafego (sucessor).
 */
public record DeprecatedRoute(String method, String template, PathPattern pattern, String successorTemplate) {

    /** Chave "METODO /template", a mesma usada em docs/uso-endpoints-elk.csv e no OpenAPI. */
    public String key() {
        return method + " " + template;
    }

    /** Casa a rota com um caminho real, ou devolve vazio. */
    public Optional<Map<String, String>> match(String requestPath) {
        PathPattern.PathMatchInfo info = pattern.matchAndExtract(PathContainer.parsePath(requestPath));
        return Optional.ofNullable(info).map(PathPattern.PathMatchInfo::getUriVariables);
    }

    /** Sucessor com as variaveis do caminho chamado preenchidas, ex.: /api/v1/afetados/contract/123. */
    public Optional<String> successorFor(Map<String, String> uriVariables) {
        if (successorTemplate == null) {
            return Optional.empty();
        }
        String expanded = successorTemplate;
        for (Map.Entry<String, String> variable : uriVariables.entrySet()) {
            expanded = expanded.replace("{" + variable.getKey() + "}",
                    UriUtils.encodePathSegment(variable.getValue(), StandardCharsets.UTF_8));
        }
        return expanded.contains("{") ? Optional.empty() : Optional.of(expanded);
    }
}
