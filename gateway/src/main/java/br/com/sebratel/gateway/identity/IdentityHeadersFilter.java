package br.com.sebratel.gateway.identity;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

/**
 * Entrega aos servicos os dados de quem chamou, ja autenticado pelo gateway, e nada mais:
 * <ul>
 *   <li>{@code X-User-Email}, {@code X-User-Name} (codificado como URL, por causa de acentos), {@code X-User-Id}
 *       (claim sub) e {@code X-Auth-Method} (jwt, basic ou anonymous);</li>
 *   <li>qualquer {@code X-User-*}, {@code X-Auth-*} e {@code Authorization} vindos de fora sao descartados antes:
 *       um cliente nao consegue se passar por outro usuario e a credencial nunca chega aos servicos.</li>
 * </ul>
 */
@Component
public class IdentityHeadersFilter implements GlobalFilter, Ordered {

    public static final String EMAIL = "X-User-Email";
    public static final String NAME = "X-User-Name";
    public static final String ID = "X-User-Id";
    public static final String METHOD = "X-Auth-Method";

    /** Atributo do exchange com o e-mail (ou usuario Basic), lido pelo log de acesso. */
    public static final String USER_ATTRIBUTE = "gateway.user";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .map(principal -> identityOf(principal))
                .defaultIfEmpty(Identity.ANONYMOUS)
                .flatMap(identity -> {
                    exchange.getAttributes().put(USER_ATTRIBUTE, identity.subject());
                    ServerWebExchange forwarded = exchange.mutate()
                            .request(request -> request.headers(headers -> apply(headers, identity)))
                            .build();
                    return chain.filter(forwarded);
                });
    }

    private static void apply(HttpHeaders headers, Identity identity) {
        headers.keySet().removeIf(name -> {
            String lower = name.toLowerCase();
            return lower.startsWith("x-user-") || lower.startsWith("x-auth-") || lower.equals("authorization")
                    || lower.equals("x-consumer");
        });
        headers.set(METHOD, identity.method());
        if (!identity.email().isEmpty()) {
            headers.set(EMAIL, identity.email());
        }
        if (!identity.name().isEmpty()) {
            headers.set(NAME, URLEncoder.encode(identity.name(), StandardCharsets.UTF_8));
        }
        if (!identity.id().isEmpty()) {
            headers.set(ID, identity.id());
        }
    }

    static Identity identityOf(Principal principal) {
        if (principal instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            return new Identity("jwt", text(jwt.getClaimAsString("email")), text(jwt.getClaimAsString("name")),
                    text(jwt.getSubject()));
        }
        if (principal instanceof UsernamePasswordAuthenticationToken basic && basic.isAuthenticated()) {
            return new Identity("basic", "", "", text(basic.getName()));
        }
        if (principal instanceof Authentication authentication && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {
            return new Identity("basic", "", "", text(authentication.getName()));
        }
        return Identity.ANONYMOUS;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    /** Dados do chamador; {@code subject} e o que aparece no log (e-mail, ou o usuario Basic). */
    record Identity(String method, String email, String name, String id) {
        static final Identity ANONYMOUS = new Identity("anonymous", "", "", "");

        String subject() {
            return !email.isEmpty() ? email : (id.isEmpty() ? "-" : id);
        }
    }

    /** Depois da autenticacao e antes dos filtros de roteamento. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
