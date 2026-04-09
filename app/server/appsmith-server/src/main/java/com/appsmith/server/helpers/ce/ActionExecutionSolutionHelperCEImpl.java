package com.appsmith.server.helpers.ce;

import com.appsmith.external.dtos.ExecuteActionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static com.appsmith.server.configurations.ClientUserRepository.OAUTH2_ACCESS_TOKEN_SESSION_KEY;

/**
 * PocketFM CE fork: Extracts the user's OAuth2 access token from the web session
 * and injects it into the ExecuteActionDTO so that datasource API calls can
 * substitute the <<APPSMITH_USER_OAUTH2_ACCESS_TOKEN>> placeholder.
 *
 * The token is stored in the session as a plain string by ClientUserRepository
 * during OAuth2 login, making it available in all subsequent requests without
 * needing the original OAuth2AuthenticationToken or OAuth2AuthorizedClient.
 */
@Slf4j
@Component
public class ActionExecutionSolutionHelperCEImpl implements ActionExecutionSolutionHelperCE {

    @Override
    public Mono<ExecuteActionDTO> populateExecuteActionDTOWithSystemInfo(ExecuteActionDTO executeActionDTO) {
        return Mono.deferContextual(contextView -> {
            ServerWebExchange exchange = contextView.getOrDefault(ServerWebExchange.class, null);
            if (exchange == null) {
                log.debug("ServerWebExchange not in Reactor context — cannot retrieve OAuth2 token");
                return Mono.just(executeActionDTO);
            }

            return exchange.getSession()
                    .map(session -> {
                        String token = session.getAttribute(OAUTH2_ACCESS_TOKEN_SESSION_KEY);
                        if (token != null) {
                            executeActionDTO.setSystemOAuth2AccessToken(token);
                            log.debug("Injected OAuth2 access token from session into ExecuteActionDTO");
                        }
                        return executeActionDTO;
                    })
                    .defaultIfEmpty(executeActionDTO);
        });
    }
}
