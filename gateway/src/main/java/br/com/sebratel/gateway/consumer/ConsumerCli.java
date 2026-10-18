package br.com.sebratel.gateway.consumer;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;

/**
 * Gera as credenciais de um consumidor novo: usuario, senha aleatoria (mostrada UMA vez) e a entrada do cadastro
 * com o hash. A senha nunca e gravada em lugar nenhum por este programa. Uso (a imagem ja traz tudo):
 *
 * <pre>
 * docker run --rm --entrypoint java ghcr.io/sebratel/api-gateway:staging \
 *   -cp app.jar -Dloader.main=br.com.sebratel.gateway.consumer.ConsumerCli \
 *   org.springframework.boot.loader.launch.PropertiesLauncher n8n-massivas "Automacao de massivas (time X)"
 * </pre>
 */
public final class ConsumerCli {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int PASSWORD_LENGTH = 32;

    private ConsumerCli() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || !args[0].matches("[a-z0-9][a-z0-9-]{1,40}")) {
            System.err.println("Uso: ConsumerCli <nome-do-consumidor (minusculas, numeros e hifen)> [descricao]");
            System.exit(2);
        }
        String name = args[0];
        String description = args.length > 1 ? args[1].replace("\"", "'") : "";
        System.out.print(render(name, description, generatePassword(new SecureRandom())));
    }

    static String generatePassword(SecureRandom random) {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }

    static String render(String name, String description, String password) {
        String hash = "{bcrypt}" + new BCryptPasswordEncoder(10).encode(password);
        return """
                ============================================================================
                CONSUMIDOR: %1$s
                ENTREGUE AO CONSUMIDOR (uma vez, por canal seguro; nao fica salvo em lugar nenhum):
                  usuario: %1$s
                  senha:   %2$s
                  uso:     curl -u %1$s:<senha> https://<host-do-gateway>/api/v1/...
                ============================================================================
                COLE NO CADASTRO (consumers.yml, so o hash; sem a senha):
                  - name: %1$s
                    username: %1$s
                    password-hash: "%3$s"
                    description: "%4$s"
                    # rate-limit: {requests-per-second: 20, burst: 100}   # opcional; sem isso vale o padrao
                    # allowed-routes: ["GET /api/v1/matrix"]              # opcional; sem isso pode todas
                    # expires: 2027-12-31                                 # opcional
                """.formatted(name, password, hash, description);
    }
}
