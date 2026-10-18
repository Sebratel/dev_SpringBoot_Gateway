package br.com.sebratel.gateway.ratelimit;

import br.com.sebratel.gateway.identity.IdentityHeadersFilter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitFilterTest {

    private final AtomicInteger reachedTheService = new AtomicInteger();
    private final GatewayFilterChain chain = exchange -> {
        reachedTheService.incrementAndGet();
        return Mono.empty();
    };

    private MockServerWebExchange callAs(RateLimitFilter filter, String user) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/matrix").build());
        if (user != null) {
            exchange.getAttributes().put(IdentityHeadersFilter.USER_ATTRIBUTE, user);
        }
        filter.filter(exchange, chain).block();
        return exchange;
    }

    @Test
    void letsTheBurstThroughAndThenAnswers429WithRetryAfter() {
        RateLimitFilter filter = new RateLimitFilter(true, 1, 3, 100);

        for (int i = 0; i < 3; i++) {
            assertNull(callAs(filter, "ana@sebratel.com.br").getResponse().getStatusCode());
        }
        MockServerWebExchange blocked = callAs(filter, "ana@sebratel.com.br");

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getResponse().getStatusCode());
        assertNotNull(blocked.getResponse().getHeaders().getFirst("Retry-After"));
        assertTrue(Long.parseLong(blocked.getResponse().getHeaders().getFirst("Retry-After")) >= 1);
        assertEquals(3, reachedTheService.get());
    }

    @Test
    void eachConsumerHasItsOwnBucket() {
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, 100);

        assertNull(callAs(filter, "ana@sebratel.com.br").getResponse().getStatusCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, callAs(filter, "ana@sebratel.com.br").getResponse().getStatusCode());
        assertNull(callAs(filter, "bia@sebratel.com.br").getResponse().getStatusCode());
    }

    @Test
    void withoutAUserTheBucketIsKeyedByAddress() {
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, 100);

        assertNull(callAs(filter, null).getResponse().getStatusCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, callAs(filter, null).getResponse().getStatusCode());
    }

    @Test
    void doesNothingWhenDisabled() {
        RateLimitFilter filter = new RateLimitFilter(false, 1, 1, 100);

        for (int i = 0; i < 10; i++) {
            assertNull(callAs(filter, "ana@sebratel.com.br").getResponse().getStatusCode());
        }
        assertEquals(10, reachedTheService.get());
    }

    @Test
    void bucketRefillsOverTime() {
        RateLimitFilter.Bucket bucket = new RateLimitFilter.Bucket(1, 0);

        assertEquals(0, bucket.tryAcquire(0, 2.0, 1));
        assertTrue(bucket.tryAcquire(0, 2.0, 1) > 0);
        assertEquals(0, bucket.tryAcquire(600_000_000L, 2.0, 1));
    }
}
