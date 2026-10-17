package br.com.sebratel.gateway;

import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;

/** Utilidades de leitura das definicoes de rota nos testes. */
final class GatewayRoutesTestSupport {

    private GatewayRoutesTestSupport() {
    }

    /** Valor do predicado Path da rota (o primeiro argumento, gerado como _genkey_0). */
    static String pathOf(RouteDefinition route) {
        return route.getPredicates().stream()
                .filter(predicate -> "Path".equals(predicate.getName()))
                .map(PredicateDefinition::getArgs)
                .map(args -> args.values().iterator().next())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("rota sem Path: " + route.getId()));
    }
}
