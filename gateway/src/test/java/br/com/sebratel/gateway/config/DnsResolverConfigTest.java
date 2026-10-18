package br.com.sebratel.gateway.config;

import org.junit.jupiter.api.Test;
import reactor.netty.http.client.HttpClient;

import java.security.Security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DnsResolverConfigTest {

    @Test
    void jvmDnsCacheIsShortSoARecreatedServiceIsFoundAgainQuickly() {
        DnsResolverConfig config = new DnsResolverConfig();

        assertNotNull(config);
        assertEquals("10", Security.getProperty("networkaddress.cache.ttl"));
        assertEquals("1", Security.getProperty("networkaddress.cache.negative.ttl"));
    }

    @Test
    void customizerSwitchesTheHttpClientToTheJvmResolver() {
        HttpClient customized = new DnsResolverConfig().jvmDnsResolverCustomizer().customize(HttpClient.create());

        assertNotNull(customized);
    }
}
