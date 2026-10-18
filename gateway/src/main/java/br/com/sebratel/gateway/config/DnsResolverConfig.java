package br.com.sebratel.gateway.config;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.Security;

/**
 * Resolucao de nomes dos servicos pelo resolvedor do JVM, com cache curto.
 *
 * <p>O resolvedor proprio do Netty guarda o IP pelo TTL que o DNS do Docker informa (600 s). Quando um servico e
 * recriado (redeploy, troca de rede) o IP muda e o gateway continuaria batendo no IP antigo por ate 10 minutos,
 * respondendo 503 de um servico que esta saudavel. Com o resolvedor do JVM o cache e de {@value #DNS_TTL_SECONDS} s.
 */
@Configuration
public class DnsResolverConfig {

    static final int DNS_TTL_SECONDS = 10;

    static {
        Security.setProperty("networkaddress.cache.ttl", Integer.toString(DNS_TTL_SECONDS));
        Security.setProperty("networkaddress.cache.negative.ttl", "1");
    }

    @Bean
    public HttpClientCustomizer jvmDnsResolverCustomizer() {
        return httpClient -> httpClient.resolver(DefaultAddressResolverGroup.INSTANCE);
    }
}
