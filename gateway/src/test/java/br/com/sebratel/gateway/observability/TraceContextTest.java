package br.com.sebratel.gateway.observability;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceContextTest {

    private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Test
    void startsANewTraceWhenTheClientSentNone() {
        TraceContext context = TraceContext.continueOrStart(null);

        assertTrue(context.traceId().matches("[0-9a-f]{32}"));
        assertTrue(context.spanId().matches("[0-9a-f]{16}"));
        assertTrue(context.traceparent().matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01"));
    }

    @Test
    void keepsTheTraceIdOfTheClientAndOpensANewSpan() {
        TraceContext context = TraceContext.continueOrStart("00-" + TRACE + "-00f067aa0ba902b7-00");

        assertEquals(TRACE, context.traceId());
        assertNotEquals("00f067aa0ba902b7", context.spanId());
        assertEquals("00", context.flags());
    }

    @Test
    void invalidOrAllZeroHeadersStartANewTrace() {
        for (String bad : new String[] {"lixo", "00-" + TRACE + "-00f067aa0ba902b7", "00-" + "0".repeat(32) + "-00f067aa0ba902b7-01",
                "00-" + TRACE + "-0000000000000000-01", "00-" + TRACE.toUpperCase() + "-00f067aa0ba902b7-01"}) {
            assertNotEquals(TRACE, TraceContext.continueOrStart(bad).traceId(), bad);
        }
    }

    @Test
    void accessLogFilterSendsTheTraceToTheServiceAndBackToTheClient() {
        AccessLogWebFilter filter = new AccessLogWebFilter();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/matrix")
                .header("traceparent", "00-" + TRACE + "-00f067aa0ba902b7-01").build());
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        org.springframework.web.server.WebFilterChain chain = next -> {
            seen.set(next);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        HttpHeaders toService = seen.get().getRequest().getHeaders();
        assertTrue(toService.getFirst("traceparent").startsWith("00-" + TRACE + "-"));
        assertNotEquals("00-" + TRACE + "-00f067aa0ba902b7-01", toService.getFirst("traceparent"));
        assertEquals(TRACE, exchange.getResponse().getHeaders().getFirst("X-Trace-Id"));
        assertEquals(toService.getFirst("X-Request-Id"), exchange.getResponse().getHeaders().getFirst("X-Request-Id"));
    }
}
