package br.com.sebratel.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Uma rota do manifesto gerado pelo RouteManifestTest do monolito. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Route(
        String method,
        String path,
        String service,
        String auth,
        boolean safe,
        String handler,
        List<String> pathVariables,
        List<QueryParam> queryParams,
        List<String> headers,
        String body) {

    public boolean isPublic() {
        return "public".equals(auth);
    }

    public boolean hasBody() {
        return body != null;
    }

    public String label() {
        return method + " " + path;
    }

    /** Nome legivel no relatorio dos testes parametrizados. */
    @Override
    public String toString() {
        return label();
    }

    /** Parametro de query declarado no controller. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QueryParam(String name, String type, boolean required, @JsonProperty("default") String defaultValue) {
    }
}
