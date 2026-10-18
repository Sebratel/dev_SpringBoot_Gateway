package br.com.sebratel.gateway.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Deixa no log o que o circuit breaker decide: cada erro que ele CONTA como falha (com a excecao real) e cada
 * mudanca de estado (fechado, aberto, meio-aberto). Sem isso, um circuito que abre so deixa "503 do fallback" e
 * nenhuma pista de qual falha o abriu.
 */
@Configuration
public class CircuitBreakerLogging {

    private static final Logger LOGGER = LoggerFactory.getLogger("CIRCUIT_BREAKER");

    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerLogger() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> event) {
                CircuitBreaker breaker = event.getAddedEntry();
                breaker.getEventPublisher()
                        .onError(e -> LOGGER.warn("falha contada em '{}' ({} ms): {}", e.getCircuitBreakerName(),
                                e.getElapsedDuration().toMillis(), e.getThrowable().toString()))
                        .onStateTransition(e -> LOGGER.warn("'{}' mudou de estado: {}",
                                e.getCircuitBreakerName(), e.getStateTransition()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> event) {
                // nada a registrar
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> event) {
                // nada a registrar
            }
        };
    }
}
