package br.com.sebratel.gateway.observability;

import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contexto de rastreamento no formato W3C ({@code traceparent: 00-<trace-id>-<span-id>-<flags>}).
 *
 * <p>O gateway e a origem do rastro: reaproveita o {@code trace-id} que o cliente mandou (se for valido) ou cria um,
 * e abre um {@code span-id} proprio para o salto gateway -> servico. Os servicos leem o cabecalho, colocam o
 * {@code trace-id} no log (MDC) e o repassam; assim uma requisicao se segue do cliente ate o log de cada servico.
 * O formato e o padrao do OpenTelemetry, entao um agente OTel/APM pode assumir depois sem mudar os servicos.
 */
public record TraceContext(String traceId, String spanId, String flags) {

    public static final String HEADER = "traceparent";
    public static final String RESPONSE_HEADER = "X-Trace-Id";

    private static final Pattern TRACEPARENT = Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");
    private static final String ZERO_TRACE = "0".repeat(32);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Continua o rastro de {@code incoming} (se valido) com um novo span; senao inicia um rastro novo. */
    public static TraceContext continueOrStart(String incoming) {
        if (incoming != null) {
            Matcher matcher = TRACEPARENT.matcher(incoming.trim());
            if (matcher.matches() && !ZERO_TRACE.equals(matcher.group(1)) && !"0000000000000000".equals(matcher.group(2))) {
                return new TraceContext(matcher.group(1), randomHex(8), matcher.group(3));
            }
        }
        return new TraceContext(randomHex(16), randomHex(8), "01");
    }

    /** Valor do cabecalho {@code traceparent} que segue para o servico. */
    public String traceparent() {
        return "00-" + traceId + "-" + spanId + "-" + flags;
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        StringBuilder hex = new StringBuilder(bytes * 2);
        for (byte b : value) {
            hex.append(String.format("%02x", b));
        }
        String result = hex.toString();
        return result.chars().allMatch(c -> c == '0') ? randomHex(bytes) : result;
    }
}
