package br.com.sebratel.gateway.resilience;

import br.com.sebratel.gateway.observability.AccessLogWebFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resposta quando o circuit breaker de um servico esta aberto (ou o servico nao respondeu): 503 padronizado, em vez
 * de segurar a requisicao. Abre quando a taxa de falhas (502/503/504 e erros de conexao) passa do limite e volta a
 * testar sozinho depois de {@code wait-duration-in-open-state}.
 */
@RestController
public class FallbackController {

    @RequestMapping(value = "/fallback/{service}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> fallback(@PathVariable String service, ServerWebExchange exchange) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", 503);
        body.put("error", "Service Unavailable");
        body.put("message", "O servico '" + service + "' esta indisponivel no momento. Tente novamente em instantes.");
        body.put("requestId", exchange.getResponse().getHeaders().getFirst(AccessLogWebFilter.REQUEST_ID));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "30").body(body);
    }
}
