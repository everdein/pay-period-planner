package com.example.backend.dto.identity;

import com.example.backend.domain.identity.WorkspaceAccess;

public record WorkspaceAccessResponse(long id, String name, String role) {

  public static WorkspaceAccessResponse from(WorkspaceAccess workspace) {
    return new WorkspaceAccessResponse(
        workspace.workspaceId(), workspace.workspaceName(), workspace.role());
  }
}
