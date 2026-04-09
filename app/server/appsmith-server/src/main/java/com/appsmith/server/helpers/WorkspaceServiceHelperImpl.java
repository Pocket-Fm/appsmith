package com.appsmith.server.helpers;

import com.appsmith.server.helpers.ce.WorkspaceServiceHelperCEImpl;
import com.appsmith.server.services.SessionUserService;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceServiceHelperImpl extends WorkspaceServiceHelperCEImpl implements WorkspaceServiceHelper {

    public WorkspaceServiceHelperImpl(SessionUserService sessionUserService) {
        super(sessionUserService);
    }
}
