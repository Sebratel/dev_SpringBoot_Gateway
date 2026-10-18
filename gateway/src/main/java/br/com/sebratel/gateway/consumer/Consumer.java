package br.com.sebratel.gateway.consumer;

import java.time.LocalDate;
import java.util.List;

/**
 * Quem chama o gateway com HTTP Basic: um sistema ou time, com usuario proprio.
 *
 * @param name            nome que aparece em log, metrica e no cabecalho {@code X-Consumer} enviado aos servicos
 * @param username        usuario do Basic
 * @param passwordHash    hash bcrypt, no formato {@code {bcrypt}$2y$10$...} (nunca a senha)
 * @param description     para que serve e quem e o responsavel
 * @param requestsPerSecond limite proprio de requisicoes por segundo; 0 = usa o padrao do gateway
 * @param burst           rajada propria; 0 = usa o padrao do gateway
 * @param allowedRoutes   rotas permitidas ({@code "GET /api/v1/matrix"}, aceita {@code *} e {@code **}); vazio = todas
 * @param enabled         {@code false} bloqueia sem apagar
 * @param expires         ultimo dia de validade; {@code null} = sem prazo
 */
public record Consumer(String name, String username, String passwordHash, String description,
                       double requestsPerSecond, int burst, List<String> allowedRoutes, boolean enabled,
                       LocalDate expires) {

    /** Consumidor que representa o usuario Basic unico antigo (SECURITY_USER/SECURITY_PASS), ate a migracao. */
    public static final String LEGACY_NAME = "legacy-admin";

    public boolean isExpired(LocalDate today) {
        return expires != null && today.isAfter(expires);
    }

    public boolean usable(LocalDate today) {
        return enabled && !isExpired(today);
    }
}
