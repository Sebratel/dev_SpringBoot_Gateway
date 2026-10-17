package br.com.sebratel.contract;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Carrega o manifesto versionado em src/test/resources/manifest.json. */
final class ManifestLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ManifestLoader() {
    }

    static List<Route> load() {
        try (InputStream in = ManifestLoader.class.getResourceAsStream("/manifest.json")) {
            if (in == null) {
                throw new IllegalStateException("manifest.json ausente em src/test/resources");
            }
            return MAPPER.readValue(in, MAPPER.getTypeFactory().constructCollectionType(List.class, Route.class));
        } catch (IOException e) {
            throw new IllegalStateException("manifest.json invalido", e);
        }
    }
}
