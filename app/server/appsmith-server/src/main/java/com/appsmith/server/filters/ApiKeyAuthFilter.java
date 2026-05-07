package com.appsmith.server.filters;

import com.appsmith.server.domains.User;
import com.appsmith.server.repositories.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collections;

public class ApiKeyAuthFilter implements WebFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final String expectedApiKey;
    private final UserRepository userRepository;
    private final String serviceAccountEmail;

    private volatile User cachedServiceUser;

    public ApiKeyAuthFilter(String expectedApiKey, UserRepository userRepository, String serviceAccountEmail) {
        this.expectedApiKey = expectedApiKey;
        this.userRepository = userRepository;
        this.serviceAccountEmail = serviceAccountEmail;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);

        if (apiKey == null || apiKey.isEmpty()) {
            return chain.filter(exchange);
        }

        if (!expectedApiKey.equals(apiKey)) {
            return chain.filter(exchange);
        }

        return getServiceUser()
                .flatMap(user -> {
                    UsernamePasswordAuthenticationToken authentication =
                            UsernamePasswordAuthenticationToken.authenticated(
                                    user, null, Collections.emptyList());

                    SecurityContextImpl securityContext = new SecurityContextImpl(authentication);

                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                                    Mono.just(securityContext)));
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<User> getServiceUser() {
        if (cachedServiceUser != null) {
            return Mono.just(cachedServiceUser);
        }

        return userRepository.findByEmail(serviceAccountEmail)
                .doOnNext(user -> cachedServiceUser = user);
    }
}
