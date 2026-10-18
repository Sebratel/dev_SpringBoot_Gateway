package br.com.sebratel.gateway.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerCliTest {

    @Test
    void generatesALongRandomPasswordWithoutAmbiguousCharacters() {
        String first = ConsumerCli.generatePassword(new SecureRandom());
        String second = ConsumerCli.generatePassword(new SecureRandom());

        assertEquals(32, first.length());
        assertNotEquals(first, second);
        assertTrue(first.matches("[A-HJ-NP-Za-km-z2-9]{32}"), "sem 0, O, 1, l, I");
    }

    @Test
    void theRenderedHashMatchesThePasswordAndTheEntryNeverHoldsThePassword() {
        String password = "senha-de-teste-123";

        String output = ConsumerCli.render("n8n-massivas", "Automacao de massivas", password);

        String hash = output.lines().filter(l -> l.contains("password-hash")).findFirst().orElseThrow()
                .replaceAll(".*password-hash: \"\\{bcrypt\\}(.*)\".*", "$1");
        assertTrue(new BCryptPasswordEncoder().matches(password, hash));
        String entry = output.substring(output.indexOf("COLE NO CADASTRO"));
        assertFalse(entry.contains(password), "a senha nao pode ir para o cadastro");
        assertTrue(entry.contains("username: n8n-massivas"));
    }

    @Test
    void theEntryItGeneratesIsAcceptedByTheRegistryParser() throws Exception {
        String output = ConsumerCli.render("sistema-x", "descricao", "qualquer");
        String entry = output.substring(output.indexOf("  - name:")).lines()
                .filter(l -> !l.trim().startsWith("#")).reduce("", (a, b) -> a + b + "\n");
        java.nio.file.Path file = java.nio.file.Files.createTempFile("consumers", ".yml");
        java.nio.file.Files.writeString(file, "consumers:\n" + entry);

        assertEquals("sistema-x", ConsumerRegistry.parse(file).get(0).name());
    }
}
