package br.com.sebratel.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Quem entra pela porta do actuator (8086, nao publicada) nao precisa de credencial; pela porta publica, sim. */
class ManagementPortMatcherTest {

    private static ServerWebExchange arrivingAt(int port) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/prometheus")
                .localAddress(new InetSocketAddress("192.168.112.2", port)).build());
    }

    private static boolean matches(String configured, int port) {
        return SecurityConfig.managementPortMatcher(configured).matches(arrivingAt(port)).block().isMatch();
    }

    @Test
    void matchesOnlyTheConfiguredManagementPort() {
        assertTrue(matches("8086", 8086));
        assertFalse(matches("8086", 8085), "a porta publica continua exigindo credencial");
    }

    @Test
    void withoutASeparateManagementPortNothingIsOpened() {
        assertFalse(matches("", 8085));
        assertFalse(matches(null, 8086));
        assertFalse(matches("-1", 8086));
        assertFalse(matches("abc", 8086));
    }

    @Test
    void aRequestWithoutALocalAddressIsNotOpened() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/prometheus").build());

        assertFalse(SecurityConfig.managementPortMatcher("8086").matches(exchange).block().isMatch());
    }
}
