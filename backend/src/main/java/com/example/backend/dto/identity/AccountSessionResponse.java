package com.example.backend.dto.identity;

import com.example.backend.domain.identity.AuthenticatedSession;
import java.time.Instant;
import java.util.List;

public record AccountSessionResponse(
    long userId,
    String email,
    String displayName,
    Instant expiresAt,
    List<WorkspaceAccessResponse> workspaces) {

  public static AccountSessionResponse from(AuthenticatedSession session) {
    return new AccountSessionResponse(
        session.userId(),
        session.email(),
        session.displayName(),
        session.expiresAt(),
        session.workspaces().stream().map(WorkspaceAccessResponse::from).toList());
  }
}
