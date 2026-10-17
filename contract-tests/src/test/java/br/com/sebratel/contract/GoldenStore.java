package br.com.sebratel.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Le e grava as respostas de referencia em contract-tests/golden (um arquivo por rota). */
final class GoldenStore {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final Path dir;

    GoldenStore(Path dir) {
        this.dir = dir;
    }

    Optional<Golden> read(Route route) throws IOException {
        Path file = file(route);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), Golden.class));
    }

    void write(Route route, Golden golden) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(file(route), MAPPER.writeValueAsString(golden) + "\n", StandardCharsets.UTF_8);
    }

    private Path file(Route route) {
        String name = (route.method() + route.path()).replaceAll("[^A-Za-z0-9]+", "_");
        return dir.resolve(name + ".json");
    }
}
