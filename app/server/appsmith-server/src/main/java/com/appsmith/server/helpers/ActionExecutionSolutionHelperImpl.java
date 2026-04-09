package com.appsmith.server.helpers;

import com.appsmith.server.helpers.ce.ActionExecutionSolutionHelperCEImpl;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;

@Component
public class ActionExecutionSolutionHelperImpl extends ActionExecutionSolutionHelperCEImpl
        implements ActionExecutionSolutionHelper {

    public ActionExecutionSolutionHelperImpl(ServerOAuth2AuthorizedClientRepository authorizedClientRepository) {
        super(authorizedClientRepository);
    }
}
