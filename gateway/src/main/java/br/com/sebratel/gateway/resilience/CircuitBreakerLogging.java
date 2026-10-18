package br.com.sebratel.gateway.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Deixa no log o que o circuit breaker decide: cada erro que ele CONTA como falha (com a excecao real) e cada
 * mudanca de estado (fechado, aberto, meio-aberto). Sem isso, um circuito que abre so deixa "503 do fallback" e
 * nenhuma pista de qual falha o abriu. Liga direto no registro (o usado pelo filtro CircuitBreaker das rotas).
 */
@Component
public class CircuitBreakerLogging {

    private static final Logger LOGGER = LoggerFactory.getLogger("CIRCUIT_BREAKER");

    private final CircuitBreakerRegistry registry;

    public CircuitBreakerLogging(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    void listen() {
        registry.getAllCircuitBreakers().forEach(CircuitBreakerLogging::attach);
        registry.getEventPublisher().onEntryAdded(event -> attach(event.getAddedEntry()));
    }

    static void attach(CircuitBreaker breaker) {
        breaker.getEventPublisher()
                .onError(e -> LOGGER.warn("falha contada em '{}' ({} ms): {}", e.getCircuitBreakerName(),
                        e.getElapsedDuration().toMillis(), e.getThrowable().toString()))
                .onStateTransition(e -> LOGGER.warn("'{}' mudou de estado: {}",
                        e.getCircuitBreakerName(), e.getStateTransition()));
    }
}
