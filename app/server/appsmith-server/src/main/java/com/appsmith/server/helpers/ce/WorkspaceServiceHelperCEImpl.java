package com.appsmith.server.helpers.ce;

import com.appsmith.server.services.SessionUserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

/**
 * PocketFM CE fork: Only super admins (instance admins) can create workspaces.
 * Non-admin users must be added to existing workspaces by an admin.
 */
@Slf4j
@Component
@AllArgsConstructor
public class WorkspaceServiceHelperCEImpl implements WorkspaceServiceHelperCE {

    private final SessionUserService sessionUserService;

    @Override
    public Mono<Boolean> isCreateWorkspaceAllowed(Boolean isDefaultWorkspace) {
        return sessionUserService.getCurrentUser()
                .map(user -> {
                    if (Boolean.TRUE.equals(user.getIsSuperUser())) {
                        return TRUE;
                    }
                    log.debug("Workspace creation denied for non-super-user: {}", user.getEmail());
                    return FALSE;
                })
                .defaultIfEmpty(FALSE);
    }

    @Override
    public Mono<String> generateDefaultWorkspaceName(String firstName) {
        return Mono.just(firstName + "'s apps");
    }
}
