package br.com.sebratel.gateway.consumer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConsumerContextFilterTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private ConsumerContextFilter filter(String yaml) throws Exception {
        Path file = Files.createTempFile("consumers", ".yml");
        Files.writeString(file, yaml);
        return new ConsumerContextFilter(new ConsumerRegistry(file.toString(), "", "admin", "admin", 0), meters);
    }

    private ServerWebExchange run(ConsumerContextFilter filter, String username, HttpMethod method, String path,
                                  AtomicReference<ServerWebExchange> seen) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path)
                .header("X-Consumer", "forjado").build());
        ServerWebExchange withUser = username == null ? exchange : exchange.mutate().principal(Mono.just(
                new UsernamePasswordAuthenticationToken(username, "x", AuthorityUtils.createAuthorityList("ROLE_CONSUMER")))).build();
        GatewayFilterChain chain = next -> {
            seen.set(next);
            return Mono.empty();
        };
        filter.filter(withUser, chain).block();
        return withUser;
    }

    private static final String CONSUMERS = """
            consumers:
              - name: n8n-massivas
                username: n8n
                password-hash: "{bcrypt}$2y$04$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ0123"
                allowed-routes: ["GET /api/v1/massivas/recuperar-pelo-banco", "DELETE /api/v1/afetados/protocol/*"]
            """;

    @Test
    void sendsTheConsumerNameToTheServiceAndDiscardsAForgedOne() throws Exception {
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();

        run(filter(CONSUMERS), "n8n", HttpMethod.GET, "/api/v1/massivas/recuperar-pelo-banco", seen);

        assertEquals("n8n-massivas", seen.get().getRequest().getHeaders().getFirst("X-Consumer"));
    }

    @Test
    void theLegacyAdminCanCallAnyRoute() throws Exception {
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();

        run(filter(CONSUMERS), "admin", HttpMethod.POST, "/api/v1/qualquer-coisa", seen);

        assertEquals("legacy-admin", seen.get().getRequest().getHeaders().getFirst("X-Consumer"));
    }

    @Test
    void aConsumerWithAllowedRoutesGets403ForAnyOtherRoute() throws Exception {
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();

        ServerWebExchange denied = run(filter(CONSUMERS), "n8n", HttpMethod.POST, "/api/v1/afetados", seen);

        assertNull(seen.get(), "a chamada nao pode chegar ao servico");
        assertEquals(HttpStatus.FORBIDDEN, denied.getResponse().getStatusCode());
    }

    @Test
    void allowedRoutesMatchTheMethodAndAcceptWildcards() throws Exception {
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        ConsumerContextFilter filter = filter(CONSUMERS);

        run(filter, "n8n", HttpMethod.DELETE, "/api/v1/afetados/protocol/1804994", seen);
        assertEquals("n8n-massivas", seen.get().getRequest().getHeaders().getFirst("X-Consumer"));

        seen.set(null);
        run(filter, "n8n", HttpMethod.GET, "/api/v1/afetados/protocol/1804994", seen);
        assertNull(seen.get(), "mesmo caminho, outro metodo: negado");
    }

    @Test
    void countsRequestsPerConsumerAndStatusClass() throws Exception {
        run(filter(CONSUMERS), "admin", HttpMethod.GET, "/api/v1/matrix", new AtomicReference<>());

        assertEquals(1.0, meters.get("gateway.consumer.requests").tag("consumer", "legacy-admin").counter().count());
    }

    @Test
    void withoutAnAuthenticatedUserTheCallerIsAnonymous() throws Exception {
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();

        run(filter(CONSUMERS), null, HttpMethod.GET, "/api/v1/matrix", seen);

        assertEquals("anonymous", seen.get().getRequest().getHeaders().getFirst("X-Consumer"));
    }
}
