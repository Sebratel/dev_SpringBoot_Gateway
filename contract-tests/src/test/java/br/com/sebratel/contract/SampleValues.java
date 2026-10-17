package br.com.sebratel.contract;

import java.util.stream.Collectors;

/**
 * Valores de exemplo para montar uma requisicao valida em forma (nao em negocio) a partir do manifesto.
 * Um 400/404 de negocio e aceito; o que importa e a rota ser encaminhada e responder no formato esperado.
 */
final class SampleValues {

    private SampleValues() {
    }

    /** Path com as variaveis substituidas e os query params obrigatorios preenchidos. */
    static String path(Route route) {
        String path = route.path();
        for (String variable : route.pathVariables()) {
            path = path.replace("{" + variable + "}", pathValue(variable));
        }
        String query = route.queryParams().stream()
                .filter(Route.QueryParam::required)
                .map(param -> param.name() + "=" + queryValue(param))
                .collect(Collectors.joining("&"));
        return query.isEmpty() ? path : path + "?" + query;
    }

    /** Corpo JSON minimo; DTOs desconhecidos recebem objeto vazio. */
    static String body(Route route) {
        String type = route.body().replace("?", "");
        return switch (type) {
            case "InventoryRequestDTO", "ActiveSellersInputDTO" -> "{\"nome\":\"contract-test\"}";
            case "AlertaProtocoloPendenteInputDTO" -> "{\"pppoes\":[\"contract-test\"]}";
            default -> "{}";
        };
    }

    private static String pathValue(String variable) {
        return switch (variable) {
            case "pppoe", "clientId" -> "contract-test";
            default -> "1";
        };
    }

    private static String queryValue(Route.QueryParam param) {
        return switch (param.type()) {
            case "LocalDateTime" -> "2026-01-01T00:00:00";
            case "int", "Integer", "long", "Long" -> "1";
            default -> "contract-test";
        };
    }
}
