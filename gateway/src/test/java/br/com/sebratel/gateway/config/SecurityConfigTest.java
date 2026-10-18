package br.com.sebratel.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Politica de autenticacao do gateway. As rotas autenticadas que passam pela seguranca tentam chegar ao servico
 * (que nao existe no teste) e voltam 5xx: o que importa aqui e que NAO sao 401.
 */
@SpringBootTest(properties = {"gateway.security.basic.user=n8n", "gateway.security.basic.password=segredo-de-teste",
        "management.prometheus.metrics.export.enabled=true",
        "management.server.port="})
@AutoConfigureWebTestClient
class SecurityConfigTest {

    @Autowired
    private WebTestClient client;

    @Test
    void healthIsOpen() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void everythingElseRequiresCredentials() {
        client.get().uri("/api/v1/qualquer-coisa").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void malformedBearerTokenIsRejected() {
        client.get().uri("/api/v1/estoque/tecnico").header("Authorization", "Bearer isto-nao-e-um-jwt")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void wrongBasicPasswordIsRejected() {
        client.get().uri("/api/v1/estoque/tecnico").headers(h -> h.setBasicAuth("n8n", "errada"))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void validBasicPassesAuthentication() {
        client.get().uri("/api/v1/estoque/tecnico").headers(h -> h.setBasicAuth("n8n", "segredo-de-teste"))
                .exchange().expectStatus().value(status -> assertNotEquals(401, status.intValue()));
    }

    @Test
    void publicRoutesNeedNoCredentials() {
        client.get().uri("/api/v1/matrix").exchange()
                .expectStatus().value(status -> assertNotEquals(401, status.intValue()));
        client.get().uri("/api/v1/afetados/contract/123").exchange()
                .expectStatus().value(status -> assertNotEquals(401, status.intValue()));
    }

    @Test
    void publicRouteIsOnlyPublicForGet() {
        client.post().uri("/api/v1/afetados/contract/123").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void everyResponseCarriesARequestId() {
        client.get().uri("/api/v1/qualquer-coisa").exchange().expectHeader().exists("X-Request-Id");
    }

    @Test
    void aClientRequestIdIsKept() {
        client.get().uri("/api/v1/qualquer-coisa").header("X-Request-Id", "abc-123").exchange()
                .expectHeader().valueEquals("X-Request-Id", "abc-123");
    }

    @Test
    void everyResponseCarriesATraceId() {
        client.get().uri("/api/v1/qualquer-coisa").exchange()
                .expectHeader().value("X-Trace-Id", id -> org.junit.jupiter.api.Assertions.assertTrue(id.matches("[0-9a-f]{32}")));
    }

    @Test
    void responsesCarrySecurityHeaders() {
        client.get().uri("/actuator/health").header("X-Forwarded-Proto", "https").exchange()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY")
                .expectHeader().valueEquals("Referrer-Policy", "no-referrer")
                .expectHeader().valueEquals("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
                .expectHeader().exists("Strict-Transport-Security")
                .expectHeader().exists("Permissions-Policy");
    }

    @Test
    void servicesHealthAndMetricsNeedCredentials() {
        client.get().uri("/actuator/services").exchange().expectStatus().isUnauthorized();
        client.get().uri("/actuator/prometheus").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void servicesHealthReportsEachServiceForAnAuthenticatedCaller() {
        client.get().uri("/actuator/services").headers(h -> h.setBasicAuth("n8n", "segredo-de-teste")).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.services.massivas").exists().jsonPath("$.services.pessoas").exists();
    }

    @Test
    void metricsAreExposedInPrometheusFormat() {
        client.get().uri("/actuator/prometheus").headers(h -> h.setBasicAuth("n8n", "segredo-de-teste")).exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> org.junit.jupiter.api.Assertions.assertTrue(body.contains("application=\"api-gateway\"")));
    }
}
