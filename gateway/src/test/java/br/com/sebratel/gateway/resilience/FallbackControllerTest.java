package br.com.sebratel.gateway.resilience;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallbackControllerTest {

    @Test
    void answers503WithTheServiceNameAndTheRequestId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/sellers/ativos").build());
        exchange.getResponse().getHeaders().set("X-Request-Id", "req-1");

        ResponseEntity<Map<String, Object>> response = new FallbackController().fallback("comercial", exchange);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("30", response.getHeaders().getFirst("Retry-After"));
        assertEquals(503, response.getBody().get("status"));
        assertEquals("req-1", response.getBody().get("requestId"));
        assertTrue(response.getBody().get("message").toString().contains("comercial"));
    }
}
