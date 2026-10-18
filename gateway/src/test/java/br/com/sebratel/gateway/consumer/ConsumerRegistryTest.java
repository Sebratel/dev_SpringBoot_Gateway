package br.com.sebratel.gateway.consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerRegistryTest {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(4);

    @TempDir
    Path dir;

    private Path write(String content) throws IOException {
        Path file = dir.resolve("consumers.yml");
        Files.writeString(file, content);
        return file;
    }

    private static String hash(String password) {
        return "{bcrypt}" + BCRYPT.encode(password);
    }

    private ConsumerRegistry registry(Path file, String legacyUser, String legacyPass) {
        return new ConsumerRegistry(file == null ? "" : file.toString(), "", legacyUser, legacyPass, 0);
    }

    @Test
    void loadsConsumersWithTheirLimitsAndAllowedRoutes() throws IOException {
        Path file = write("""
                consumers:
                  - name: n8n-massivas
                    username: n8n-massivas
                    password-hash: "%s"
                    description: Automacao de massivas
                    rate-limit: {requests-per-second: 20, burst: 100}
                    allowed-routes: ["GET /api/v1/massivas/recuperar-pelo-banco", "POST /api/v1/afetados"]
                    expires: 2099-12-31
                """.formatted(hash("segredo")));

        Consumer consumer = registry(file, "", "").findByUsername("n8n-massivas").orElseThrow();

        assertEquals("n8n-massivas", consumer.name());
        assertEquals(20, consumer.requestsPerSecond());
        assertEquals(100, consumer.burst());
        assertEquals(List.of("GET /api/v1/massivas/recuperar-pelo-banco", "POST /api/v1/afetados"), consumer.allowedRoutes());
        assertTrue(consumer.usable(LocalDate.now()));
    }

    @Test
    void theOldSingleBasicUserBecomesTheLegacyAdminConsumer() {
        Consumer legacy = registry(null, "admin", "admin").findByUsername("admin").orElseThrow();

        assertEquals(Consumer.LEGACY_NAME, legacy.name());
        assertEquals("{noop}admin", legacy.passwordHash());
        assertTrue(legacy.allowedRoutes().isEmpty());
        assertEquals(0, legacy.requestsPerSecond());
    }

    @Test
    void withoutLegacyCredentialsThereIsNoLegacyUser() {
        assertTrue(registry(null, "", "").all().isEmpty());
        assertTrue(registry(null, "admin", "").all().isEmpty());
    }

    @Test
    void disabledAndExpiredConsumersAreNotUsable() throws IOException {
        Path file = write("""
                consumers:
                  - {name: a, username: a, password-hash: "%s", enabled: false}
                  - {name: b, username: b, password-hash: "%s", expires: 2020-01-01}
                """.formatted(hash("x"), hash("y")));

        ConsumerRegistry registry = registry(file, "", "");

        assertFalse(registry.findByUsername("a").orElseThrow().usable(LocalDate.now()));
        assertFalse(registry.findByUsername("b").orElseThrow().usable(LocalDate.now()));
    }

    @Test
    void aPlainPasswordInTheFileIsRejectedAndTheOldRegistryIsKept() throws IOException {
        Path file = write("consumers:\n  - {name: a, username: a, password-hash: \"" + hash("x") + "\"}\n");
        ConsumerRegistry registry = registry(file, "", "");
        assertEquals(1, registry.all().size());

        Files.writeString(file, "consumers:\n  - {name: b, username: b, password-hash: senha-em-texto-puro}\n");
        file.toFile().setLastModified(System.currentTimeMillis() + 5000);
        registry.reload();

        assertTrue(registry.findByUsername("a").isPresent(), "cadastro anterior deve ser mantido");
        assertTrue(registry.findByUsername("b").isEmpty());
    }

    @Test
    void reloadPicksUpANewConsumerWithoutRestart() throws IOException {
        Path file = write("consumers:\n  - {name: a, username: a, password-hash: \"" + hash("x") + "\"}\n");
        ConsumerRegistry registry = registry(file, "", "");
        assertTrue(registry.findByUsername("novo").isEmpty());

        Files.writeString(file, "consumers:\n  - {name: a, username: a, password-hash: \"" + hash("x") + "\"}\n"
                + "  - {name: novo, username: novo, password-hash: \"" + hash("y") + "\"}\n");
        file.toFile().setLastModified(System.currentTimeMillis() + 5000);
        registry.reload();

        assertTrue(registry.findByUsername("novo").isPresent());
    }

    @Test
    void aMissingFileLeavesOnlyTheLegacyUser() {
        ConsumerRegistry registry = registry(dir.resolve("nao-existe.yml"), "admin", "admin");

        assertEquals(1, registry.all().size());
        assertNull(registry.findByUsername("outro").orElse(null));
    }

    @Test
    void aDuplicateUsernameDoesNotReplaceTheFirstOne() throws IOException {
        Path file = write("consumers:\n  - {name: primeiro, username: u, password-hash: \"" + hash("x") + "\"}\n"
                + "  - {name: segundo, username: u, password-hash: \"" + hash("y") + "\"}\n");

        assertEquals("primeiro", registry(file, "", "").findByUsername("u").orElseThrow().name());
    }
}
