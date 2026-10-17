package br.com.sebratel.gateway.identity;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class IdentityHeadersFilterTest {

    private final IdentityHeadersFilter filter = new IdentityHeadersFilter();

    private HttpHeaders forwardedHeaders(Principal principal, MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        ServerWebExchange withPrincipal = principal == null ? exchange
                : exchange.mutate().principal(Mono.just(principal)).build();
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        GatewayFilterChain chain = next -> {
            seen.set(next);
            return Mono.empty();
        };
        filter.filter(withPrincipal, chain).block();
        return seen.get().getRequest().getHeaders();
    }

    private static JwtAuthenticationToken jwtOf(String email, String name, String subject) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(subject)
                .claim("email", email).claim("name", name).build();
        return new JwtAuthenticationToken(jwt);
    }

    @Test
    void jwtUserBecomesIdentityHeadersAndTheTokenIsNotForwarded() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/estoque/tecnico")
                .header("Authorization", "Bearer segredo").build();

        HttpHeaders headers = forwardedHeaders(jwtOf("ana@sebratel.com.br", "Ana João", "1234"), request);

        assertEquals("ana@sebratel.com.br", headers.getFirst(IdentityHeadersFilter.EMAIL));
        assertEquals("Ana João", URLDecoder.decode(headers.getFirst(IdentityHeadersFilter.NAME), StandardCharsets.UTF_8));
        assertEquals("1234", headers.getFirst(IdentityHeadersFilter.ID));
        assertEquals("jwt", headers.getFirst(IdentityHeadersFilter.METHOD));
        assertNull(headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void nameWithAccentsTravelsAsAsciiSoTheHeaderIsValid() {
        HttpHeaders headers = forwardedHeaders(jwtOf("a@sebratel.com.br", "José Antônio", "1"),
                MockServerHttpRequest.get("/x").build());

        assertEquals("Jos%C3%A9+Ant%C3%B4nio", headers.getFirst(IdentityHeadersFilter.NAME));
    }

    @Test
    void spoofedIdentityHeadersFromTheClientAreDiscarded() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/x")
                .header("X-User-Email", "diretor@sebratel.com.br")
                .header("X-User-Role", "admin")
                .header("X-Auth-Method", "jwt").build();

        HttpHeaders headers = forwardedHeaders(null, request);

        assertNull(headers.getFirst(IdentityHeadersFilter.EMAIL));
        assertNull(headers.getFirst("X-User-Role"));
        assertEquals("anonymous", headers.getFirst(IdentityHeadersFilter.METHOD));
    }

    @Test
    void basicUserIsIdentifiedByTheUsernameOnly() {
        UsernamePasswordAuthenticationToken basic = UsernamePasswordAuthenticationToken.authenticated("n8n", null, List.of());

        HttpHeaders headers = forwardedHeaders(basic, MockServerHttpRequest.get("/x").build());

        assertEquals("basic", headers.getFirst(IdentityHeadersFilter.METHOD));
        assertEquals("n8n", headers.getFirst(IdentityHeadersFilter.ID));
        assertNull(headers.getFirst(IdentityHeadersFilter.EMAIL));
    }

    @Test
    void anonymousCallsCarryNoUserData() {
        HttpHeaders headers = forwardedHeaders(null, MockServerHttpRequest.get("/api/v1/matrix").build());

        assertEquals("anonymous", headers.getFirst(IdentityHeadersFilter.METHOD));
        assertFalse(headers.containsKey(IdentityHeadersFilter.EMAIL));
        assertFalse(headers.containsKey(IdentityHeadersFilter.NAME));
        assertFalse(headers.containsKey(IdentityHeadersFilter.ID));
    }
}
