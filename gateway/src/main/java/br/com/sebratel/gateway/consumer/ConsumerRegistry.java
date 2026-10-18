package br.com.sebratel.gateway.consumer;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cadastro de consumidores do gateway.
 *
 * <p>Fonte: um arquivo YAML FORA do Git ({@code gateway.consumers.file}) so com hashes bcrypt, relido a cada
 * {@code gateway.consumers.reload-seconds} s: criar, bloquear ou trocar a senha de um consumidor nao exige
 * redeploy. Mais o usuario Basic antigo (SECURITY_USER/SECURITY_PASS), que vira o consumidor {@code legacy-admin}
 * ate todos migrarem. Se o arquivo estiver invalido, vale o ultimo cadastro bom (o erro vai para o log).
 */
@Component
public class ConsumerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerRegistry.class);

    private final Path file;
    private final List<Consumer> inline;
    private final Consumer legacy;
    private final ScheduledExecutorService reloader;

    private volatile Map<String, Consumer> byUsername = Map.of();
    private volatile long lastModified = -1;

    public ConsumerRegistry(
            @Value("${gateway.consumers.file:}") String file,
            @Value("${gateway.consumers.inline-base64:}") String inlineBase64,
            @Value("${gateway.security.basic.user:}") String legacyUser,
            @Value("${gateway.security.basic.password:}") String legacyPassword,
            @Value("${gateway.consumers.reload-seconds:30}") long reloadSeconds) {
        this.file = file == null || file.isBlank() ? null : Path.of(file);
        this.inline = parseInline(inlineBase64);
        this.legacy = legacyUser.isBlank() || legacyPassword.isBlank() ? null
                : new Consumer(Consumer.LEGACY_NAME, legacyUser, "{noop}" + legacyPassword,
                        "Usuario Basic unico antigo; migrar cada sistema para um consumidor proprio",
                        0, 0, List.of(), true, null);
        reload();
        if (this.file != null && reloadSeconds > 0) {
            this.reloader = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "consumer-registry-reload");
                thread.setDaemon(true);
                return thread;
            });
            this.reloader.scheduleWithFixedDelay(this::reload, reloadSeconds, reloadSeconds, TimeUnit.SECONDS);
        } else {
            this.reloader = null;
        }
    }

    public Optional<Consumer> findByUsername(String username) {
        return Optional.ofNullable(byUsername.get(username));
    }

    /** Todos os consumidores conhecidos (sem as senhas), para diagnostico. */
    public List<Consumer> all() {
        return List.copyOf(byUsername.values());
    }

    /** Relê o arquivo se ele mudou. Publico para os testes e para forcar a leitura. */
    public synchronized void reload() {
        Map<String, Consumer> next = new LinkedHashMap<>();
        if (legacy != null) {
            next.put(legacy.username(), legacy);
        }
        for (Consumer consumer : inline) {
            next.putIfAbsent(consumer.username(), consumer);
        }
        if (file != null) {
            try {
                if (!Files.exists(file)) {
                    LOGGER.warn("Arquivo de consumidores {} nao existe: so vale o usuario legado", file);
                } else {
                    long modified = Files.getLastModifiedTime(file).toMillis();
                    if (modified == lastModified && !byUsername.isEmpty()) {
                        return;
                    }
                    for (Consumer consumer : parse(file)) {
                        if (next.putIfAbsent(consumer.username(), consumer) != null) {
                            LOGGER.warn("Consumidor '{}' repete um usuario ja existente ('{}'); ignorado",
                                    consumer.name(), consumer.username());
                        }
                    }
                    lastModified = modified;
                    LOGGER.info("Consumidores carregados de {}: {}", file, names(next));
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Arquivo de consumidores {} invalido; mantendo o cadastro anterior: {}", file, e.getMessage());
                return;
            }
        }
        byUsername = Collections.unmodifiableMap(next);
    }

    /** Alternativa ao arquivo para quem so tem variaveis de ambiente (Portainer): o mesmo YAML, em base64, numa linha. */
    private static List<Consumer> parseInline(String base64) {
        if (base64 == null || base64.isBlank()) {
            return List.of();
        }
        try {
            String yaml = new String(java.util.Base64.getDecoder().decode(base64.trim()), java.nio.charset.StandardCharsets.UTF_8);
            return parse(new java.io.ByteArrayInputStream(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            LOGGER.error("GATEWAY_CONSUMERS_B64 invalido; ignorado: {}", e.getMessage());
            return List.of();
        }
    }

    private static List<String> names(Map<String, Consumer> consumers) {
        return consumers.values().stream().map(Consumer::name).toList();
    }

    static List<Consumer> parse(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return parse(in);
        }
    }

    @SuppressWarnings("unchecked")
    static List<Consumer> parse(InputStream in) throws IOException {
        Object root = new Yaml().load(in);
        if (root == null) {
            return List.of();
        }
        Object list = ((Map<String, Object>) root).get("consumers");
        if (list == null) {
            return List.of();
        }
        List<Consumer> result = new ArrayList<>();
        for (Object item : (List<Object>) list) {
            result.add(toConsumer((Map<String, Object>) item));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Consumer toConsumer(Map<String, Object> m) {
        String name = text(m, "name");
        String username = text(m, "username");
        String hash = text(m, "password-hash");
        if (name.isBlank() || username.isBlank() || hash.isBlank()) {
            throw new IllegalArgumentException("consumidor sem name, username ou password-hash: " + name);
        }
        if (!hash.startsWith("{bcrypt}")) {
            throw new IllegalArgumentException("password-hash de '" + name + "' deve comecar com {bcrypt}");
        }
        Map<String, Object> limit = m.get("rate-limit") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        List<String> routes = new ArrayList<>();
        if (m.get("allowed-routes") instanceof List<?> allowed) {
            allowed.forEach(route -> routes.add(String.valueOf(route).trim()));
        }
        Object expires = m.get("expires");
        return new Consumer(name, username, hash, text(m, "description"),
                number(limit.get("requests-per-second")).doubleValue(), number(limit.get("burst")).intValue(),
                List.copyOf(routes), !Boolean.FALSE.equals(m.get("enabled")), dateOf(expires));
    }

    /** O YAML devolve {@code expires: 2027-06-30} (sem aspas) como Date (meia-noite UTC); com aspas, como texto. */
    private static LocalDate dateOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value).trim());
    }

    private static String text(Map<String, Object> m, String key) {
        Object value = m.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Number number(Object value) {
        return value instanceof Number n ? n : 0;
    }

    @PreDestroy
    void stop() {
        if (reloader != null) {
            reloader.shutdownNow();
        }
    }
}
