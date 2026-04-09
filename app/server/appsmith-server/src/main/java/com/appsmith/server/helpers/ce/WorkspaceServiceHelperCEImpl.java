package com.appsmith.server.helpers.ce;

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

    private final UserUtilsCE userUtils;

    @Override
    public Mono<Boolean> isCreateWorkspaceAllowed(Boolean isDefaultWorkspace) {
        return userUtils.isCurrentUserSuperUser()
                .map(isSuperUser -> {
                    if (Boolean.TRUE.equals(isSuperUser)) {
                        return TRUE;
                    }
                    log.debug("Workspace creation denied for non-super-user");
                    return FALSE;
                })
                .defaultIfEmpty(FALSE);
    }

    @Override
    public Mono<String> generateDefaultWorkspaceName(String firstName) {
        return Mono.just(firstName + "'s apps");
    }
}
