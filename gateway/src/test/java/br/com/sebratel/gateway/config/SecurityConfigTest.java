package br.com.sebratel.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
class SecurityConfigTest {

    @Autowired
    private WebTestClient client;

    @Test
    void healthIsOpen() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void everythingElseRequiresAToken() {
        client.get().uri("/api/v1/qualquer-coisa").exchange().expectStatus().isUnauthorized();
    }
}
