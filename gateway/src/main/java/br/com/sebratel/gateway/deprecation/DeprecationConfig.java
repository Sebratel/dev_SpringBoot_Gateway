package br.com.sebratel.gateway.deprecation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Beans da deprecacao de rotas; as datas vem de deprecation.since e deprecation.sunset (ISO AAAA-MM-DD). */
@Configuration
public class DeprecationConfig {

    @Bean
    public DeprecatedRoutes deprecatedRoutes() {
        return new DeprecatedRoutes();
    }

    @Bean
    public DeprecationHeadersFilter deprecationHeadersFilter(DeprecatedRoutes deprecatedRoutes,
                                                             @Value("${deprecation.since:}") String since,
                                                             @Value("${deprecation.sunset:}") String sunset) {
        return new DeprecationHeadersFilter(deprecatedRoutes, since, sunset);
    }
}
