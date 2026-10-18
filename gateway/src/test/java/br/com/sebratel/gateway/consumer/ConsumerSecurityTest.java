package br.com.sebratel.gateway.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Basic por consumidor de ponta a ponta: senha certa/errada, bloqueado, expirado, rota nao permitida e o admin legado. */
@SpringBootTest(properties = {"gateway.security.basic.user=admin", "gateway.security.basic.password=legado-de-teste"})
@AutoConfigureWebTestClient
class ConsumerSecurityTest {

    @DynamicPropertySource
    static void consumersFile(DynamicPropertyRegistry registry) throws IOException {
        Path dir = Files.createTempDirectory("consumers-test");
        Path yaml = dir.resolve("consumers.yml");
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);
        Files.writeString(yaml, """
                consumers:
                  - {name: n8n-massivas, username: n8n, password-hash: "{bcrypt}%s", allowed-routes: ["GET /api/v1/estoque/**"]}
                  - {name: bloqueado, username: bloqueado, password-hash: "{bcrypt}%s", enabled: false}
                  - {name: vencido, username: vencido, password-hash: "{bcrypt}%s", expires: 2020-01-01}
                """.formatted(bcrypt.encode("senha-n8n"), bcrypt.encode("senha-b"), bcrypt.encode("senha-v")));
        registry.add("gateway.consumers.file", yaml::toString);
    }

    @Autowired
    private WebTestClient client;

    @Test
    void aConsumerWithTheRightPasswordPassesAuthenticationOnAnAllowedRoute() {
        client.get().uri("/api/v1/estoque/movimentacao").headers(h -> h.setBasicAuth("n8n", "senha-n8n")).exchange()
                .expectStatus().value(status -> assertNotEquals(401, status.intValue()))
                .expectStatus().value(status -> assertNotEquals(403, status.intValue()));
    }

    @Test
    void aConsumerGets403OnARouteItIsNotAllowedToUse() {
        client.get().uri("/api/v1/radius/duplicate-prefixes").headers(h -> h.setBasicAuth("n8n", "senha-n8n")).exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void aWrongPasswordIs401() {
        client.get().uri("/api/v1/estoque/movimentacao").headers(h -> h.setBasicAuth("n8n", "errada")).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void aDisabledOrExpiredConsumerIs401EvenWithTheRightPassword() {
        client.get().uri("/api/v1/estoque/movimentacao").headers(h -> h.setBasicAuth("bloqueado", "senha-b")).exchange()
                .expectStatus().isUnauthorized();
        client.get().uri("/api/v1/estoque/movimentacao").headers(h -> h.setBasicAuth("vencido", "senha-v")).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void theLegacyAdminStillWorksOnAnyRouteWhileTheMigrationHappens() {
        client.get().uri("/api/v1/radius/duplicate-prefixes").headers(h -> h.setBasicAuth("admin", "legado-de-teste")).exchange()
                .expectStatus().value(status -> assertNotEquals(401, status.intValue()))
                .expectStatus().value(status -> assertNotEquals(403, status.intValue()));
    }

    @Test
    void theClientCannotForgeTheConsumerHeader() {
        client.get().uri("/api/v1/radius/duplicate-prefixes").header("X-Consumer", "n8n-massivas")
                .exchange().expectStatus().isUnauthorized();
    }
}
