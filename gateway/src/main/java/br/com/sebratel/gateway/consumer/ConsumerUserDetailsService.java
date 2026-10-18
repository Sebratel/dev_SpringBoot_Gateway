package br.com.sebratel.gateway.consumer;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/** Usuarios do HTTP Basic = consumidores usaveis (habilitados e dentro do prazo). Os demais viram 401. */
@Component
public class ConsumerUserDetailsService implements ReactiveUserDetailsService {

    private final ConsumerRegistry registry;

    public ConsumerUserDetailsService(ConsumerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return Mono.justOrEmpty(registry.findByUsername(username))
                .filter(consumer -> consumer.usable(LocalDate.now()))
                .map(consumer -> User.withUsername(consumer.username())
                        .password(consumer.passwordHash())
                        .authorities(AuthorityUtils.createAuthorityList("ROLE_CONSUMER"))
                        .build());
    }
}
