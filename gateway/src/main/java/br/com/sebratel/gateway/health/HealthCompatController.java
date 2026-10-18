package br.com.sebratel.gateway.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /actuator/health} na porta publica, como o gateway sempre respondeu. O actuator de verdade (metricas,
 * saude agregada dos servicos) fica em porta propria e nao publicada. Aqui so ha a resposta de "o gateway esta de pe".
 */
@RestController
public class HealthCompatController {

    @GetMapping({"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
