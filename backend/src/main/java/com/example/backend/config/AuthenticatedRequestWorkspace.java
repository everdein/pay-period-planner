package com.example.backend.config;

import com.example.backend.domain.identity.AuthenticatedSession;
import com.example.backend.domain.identity.WorkspaceAccess;
import com.example.backend.service.CurrentWorkspace;
import com.example.backend.service.WorkspaceAccessDeniedException;
import com.example.backend.service.WorkspaceSelectionException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedRequestWorkspace implements CurrentWorkspace {

  public static final String WORKSPACE_ID_HEADER = "X-Workspace-ID";

  private final HttpServletRequest request;

  public AuthenticatedRequestWorkspace(HttpServletRequest request) {
    this.request = request;
  }

  @Override
  public long requireWorkspaceId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof AuthenticatedSession session)) {
      throw new WorkspaceAccessDeniedException(
          "An authenticated account session is required for financial access");
    }

    List<WorkspaceAccess> memberships = session.workspaces();
    String selectedWorkspace = request.getHeader(WORKSPACE_ID_HEADER);
    if (selectedWorkspace == null || selectedWorkspace.isBlank()) {
      if (memberships.size() == 1) {
        return memberships.getFirst().workspaceId();
      }
      throw new WorkspaceSelectionException(
          "X-Workspace-ID is required when the account has multiple workspaces");
    }

    long workspaceId;
    try {
      workspaceId = Long.parseLong(selectedWorkspace.strip());
    } catch (NumberFormatException exception) {
      throw new WorkspaceSelectionException("X-Workspace-ID must be a positive whole number");
    }
    if (workspaceId < 1) {
      throw new WorkspaceSelectionException("X-Workspace-ID must be a positive whole number");
    }

    return memberships.stream()
        .mapToLong(WorkspaceAccess::workspaceId)
        .filter((membershipWorkspaceId) -> membershipWorkspaceId == workspaceId)
        .findFirst()
        .orElseThrow(
            () ->
                new WorkspaceAccessDeniedException(
                    "The authenticated account is not a member of workspace " + workspaceId));
  }
}
