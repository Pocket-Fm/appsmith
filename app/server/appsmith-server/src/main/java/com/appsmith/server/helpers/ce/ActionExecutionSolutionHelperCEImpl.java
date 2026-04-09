package com.appsmith.server.helpers.ce;

import com.appsmith.external.dtos.ExecuteActionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * PocketFM CE fork: Extracts the user's OAuth2 access token from the session
 * and injects it into the ExecuteActionDTO so that datasource API calls can
 * substitute the <<APPSMITH_USER_OAUTH2_ACCESS_TOKEN>> placeholder.
 */
@Slf4j
@Component
public class ActionExecutionSolutionHelperCEImpl implements ActionExecutionSolutionHelperCE {

    private final ServerOAuth2AuthorizedClientRepository authorizedClientRepository;

    public ActionExecutionSolutionHelperCEImpl(ServerOAuth2AuthorizedClientRepository authorizedClientRepository) {
        this.authorizedClientRepository = authorizedClientRepository;
    }

    @Override
    public Mono<ExecuteActionDTO> populateExecuteActionDTOWithSystemInfo(ExecuteActionDTO executeActionDTO) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(this::extractOAuth2AccessToken)
                .doOnNext(token -> {
                    executeActionDTO.setSystemOAuth2AccessToken(token);
                    log.debug("Injected OAuth2 access token into ExecuteActionDTO");
                })
                .thenReturn(executeActionDTO)
                .onErrorResume(e -> {
                    log.debug("Could not extract OAuth2 token from session: {}", e.getMessage());
                    return Mono.just(executeActionDTO);
                })
                .defaultIfEmpty(executeActionDTO);
    }

    /**
     * Extracts the OAuth2 access token from the authorized client stored in the session.
     * Only works when the user authenticated via OAuth2 (OIDC, Google, GitHub, etc.).
     * Returns empty Mono for form-login users.
     */
    private Mono<String> extractOAuth2AccessToken(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            // User did not log in via OAuth2 (e.g., form login)
            return Mono.empty();
        }

        String clientRegistrationId = oauthToken.getAuthorizedClientRegistrationId();

        // ServerOAuth2AuthorizedClientRepository.loadAuthorizedClient requires a ServerWebExchange.
        // In Spring WebFlux, the exchange is available in the Reactor subscriber context.
        return Mono.deferContextual(contextView -> {
            ServerWebExchange exchange = contextView.getOrDefault(ServerWebExchange.class, null);
            if (exchange == null) {
                log.debug("ServerWebExchange not found in Reactor context — cannot retrieve OAuth2 token");
                return Mono.empty();
            }

            return authorizedClientRepository
                    .<OAuth2AuthorizedClient>loadAuthorizedClient(clientRegistrationId, authentication, exchange)
                    .map(authorizedClient -> authorizedClient.getAccessToken().getTokenValue());
        });
    }
}
