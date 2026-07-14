package com.example.backend.domain.identity;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuthenticatedSession(
    UUID sessionId,
    long userId,
    String email,
    String displayName,
    Instant expiresAt,
    List<WorkspaceAccess> workspaces)
    implements Principal {

  public AuthenticatedSession {
    workspaces = List.copyOf(workspaces);
  }

  @Override
  public String getName() {
    return email;
  }
}
