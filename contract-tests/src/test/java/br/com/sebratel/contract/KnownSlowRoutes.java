package br.com.sebratel.contract;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

/** Rotas que estouram o proxy do stage mesmo no monolito (known-slow-routes.txt). */
final class KnownSlowRoutes {

    private final Set<String> labels;

    private KnownSlowRoutes(Set<String> labels) {
        this.labels = labels;
    }

    static KnownSlowRoutes load() {
        try (InputStream in = KnownSlowRoutes.class.getResourceAsStream("/known-slow-routes.txt")) {
            if (in == null) {
                return new KnownSlowRoutes(Set.of());
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return new KnownSlowRoutes(reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet()));
        } catch (IOException e) {
            throw new IllegalStateException("known-slow-routes.txt ilegivel", e);
        }
    }

    Set<String> labels() {
        return labels;
    }

    boolean contains(Route route) {
        return labels.contains(route.label());
    }
}
