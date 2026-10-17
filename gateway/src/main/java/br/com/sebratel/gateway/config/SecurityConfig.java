package br.com.sebratel.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Autenticacao do ecossistema: acontece AQUI e so aqui. Os servicos nao validam credencial; recebem os dados do
 * usuario nos cabecalhos X-User-* (ver {@code IdentityHeadersFilter}).
 *
 * <ul>
 *   <li>JWT (ID token do Google): emissor do Google e e-mail do dominio da empresa;</li>
 *   <li>HTTP Basic legado: so existe se SECURITY_USER e SECURITY_PASS estiverem definidos (paridade com o BFF
 *       original, usado por scripts e pelo n8n);</li>
 *   <li>rotas publicas: health check, {@code GET /api/v1/matrix} e {@code GET /api/v1/afetados/contract/**}
 *       (iguais as do BFF original).</li>
 * </ul>
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${gateway.security.allowed-email-domain:@sebratel.com.br}")
    private String allowedEmailDomain;

    @Value("${gateway.security.basic.user:}")
    private String basicUser;

    @Value("${gateway.security.basic.password:}")
    private String basicPassword;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/error").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/matrix").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/afetados/contract/**").permitAll()
                        .anyExchange().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(unauthorized()))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(unauthorized())
                        .jwt(Customizer.withDefaults()))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(unauthorized()))
                .build();
    }

    /**
     * CORS e do gateway (os servicos nao respondem CORS). Expoe ao front os avisos de rota obsoleta e o id da
     * requisicao. Fica no Security para o preflight (OPTIONS, sem credencial) ser respondido antes da autenticacao.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Deprecation", "Sunset", "Link", "X-Request-Id"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(jwtValidator(allowedEmailDomain));
        return decoder;
    }

    /** Emissor do Google + e-mail do dominio da empresa (a claim aud ainda nao e validada, como no BFF original). */
    static OAuth2TokenValidator<Jwt> jwtValidator(String emailDomain) {
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(GOOGLE_ISSUER);
        OAuth2TokenValidator<Jwt> domain = new JwtClaimValidator<String>("email",
                email -> email != null && email.endsWith(emailDomain));
        return new DelegatingOAuth2TokenValidator<>(withIssuer, domain);
    }

    /** Basic legado: sem SECURITY_USER/SECURITY_PASS nao ha usuario valido (a senha vira aleatoria). */
    @Bean
    public ReactiveUserDetailsService basicUserDetailsService() {
        boolean configured = !basicUser.isBlank() && !basicPassword.isBlank();
        UserDetails user = User.builder()
                .username(configured ? basicUser : "basic-disabled")
                .password("{noop}" + (configured ? basicPassword : UUID.randomUUID()))
                .roles("USER")
                .build();
        return new MapReactiveUserDetailsService(List.of(user));
    }

    private ServerAuthenticationEntryPoint unauthorized() {
        return (exchange, e) -> {
            var response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
            DataBuffer body = response.bufferFactory()
                    .wrap("Acesso restrito: token inválido ou não fornecido.".getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(body));
        };
    }
}
