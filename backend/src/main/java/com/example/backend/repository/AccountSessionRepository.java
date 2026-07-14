package com.example.backend.repository;

import com.example.backend.domain.identity.AuthenticatedSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountSessionRepository {

  Optional<UserCredential> findUserCredential(String email);

  long createUser(String email, String passwordHash, String displayName);

  long createWorkspace(long userId, String name);

  void createOwnerMembership(long userId, long workspaceId);

  void createSession(UUID sessionId, long userId, String tokenHash, Instant expiresAt);

  Optional<AuthenticatedSession> findActiveSession(String tokenHash, Instant now);

  void touchSession(UUID sessionId, Instant now);

  void revokeSession(UUID sessionId, Instant now);

  record UserCredential(
      long userId, String email, String passwordHash, String displayName, String status) {}
}
