package br.com.sebratel.gateway.health;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Saude agregada: consulta {@code /actuator/health} de cada servico por tras do gateway, em paralelo, com
 * limite de 2 s por servico. Fica em {@code /actuator/services} (exige autenticacao: revela a topologia).
 * Nao entra no {@code /actuator/health} do gateway de proposito: servico fora do ar nao deve fazer o orquestrador
 * reiniciar o gateway.
 */
@Component
@Endpoint(id = "services")
public class ServicesEndpoint {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final Map<String, String> upstreams;
    private final WebClient client = WebClient.create();

    public ServicesEndpoint(Environment environment) {
        this.upstreams = new TreeMap<>(Binder.get(environment)
                .bind("gateway.upstreams", Bindable.mapOf(String.class, String.class)).orElseGet(Map::of));
    }

    @ReadOperation
    public Mono<Map<String, Object>> health() {
        return Flux.fromIterable(upstreams.entrySet())
                .flatMap(entry -> statusOf(entry.getValue()).map(status -> Map.entry(entry.getKey(), status)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue, TreeMap::new)
                .map(statuses -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("status", statuses.values().stream().allMatch("UP"::equals) ? "UP" : "DEGRADED");
                    body.put("services", statuses);
                    return body;
                });
    }

    private Mono<String> statusOf(String baseUrl) {
        return client.get().uri(baseUrl + "/actuator/health")
                .exchangeToMono(response -> Mono.just(response.statusCode().is2xxSuccessful() ? "UP" : "DOWN"))
                .timeout(TIMEOUT)
                .onErrorReturn("DOWN");
    }
}
