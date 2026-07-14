package com.example.backend.support;

import com.example.backend.domain.identity.AuthenticatedSession;
import com.example.backend.domain.identity.WorkspaceAccess;
import com.example.backend.repository.AccountSessionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.dao.DuplicateKeyException;

public class InMemoryAccountSessionRepository implements AccountSessionRepository {

  private final AtomicLong nextUserId = new AtomicLong(1);
  private final AtomicLong nextWorkspaceId = new AtomicLong(1);
  private final Map<String, UserCredential> usersByEmail = new HashMap<>();
  private final Map<Long, WorkspaceRecord> workspacesById = new HashMap<>();
  private final Map<Long, List<WorkspaceAccess>> accessByUserId = new HashMap<>();
  private final Map<String, StoredSession> sessionsByTokenHash = new HashMap<>();

  @Override
  public Optional<UserCredential> findUserCredential(String email) {
    return Optional.ofNullable(usersByEmail.get(normalizeEmail(email)));
  }

  @Override
  public long createUser(String email, String passwordHash, String displayName) {
    String normalizedEmail = normalizeEmail(email);
    if (usersByEmail.containsKey(normalizedEmail)) {
      throw new DuplicateKeyException("Duplicate normalized email");
    }

    long userId = nextUserId.getAndIncrement();
    usersByEmail.put(
        normalizedEmail, new UserCredential(userId, email, passwordHash, displayName, "active"));
    return userId;
  }

  @Override
  public long createWorkspace(long userId, String name) {
    long workspaceId = nextWorkspaceId.getAndIncrement();
    workspacesById.put(workspaceId, new WorkspaceRecord(workspaceId, userId, name));
    return workspaceId;
  }

  @Override
  public void createOwnerMembership(long userId, long workspaceId) {
    WorkspaceRecord workspace = requiredWorkspace(workspaceId);
    accessByUserId
        .computeIfAbsent(userId, (ignored) -> new ArrayList<>())
        .add(new WorkspaceAccess(workspace.id(), workspace.name(), "owner"));
  }

  @Override
  public void createSession(UUID sessionId, long userId, String tokenHash, Instant expiresAt) {
    sessionsByTokenHash.put(
        tokenHash, new StoredSession(sessionId, userId, tokenHash, expiresAt, false));
  }

  @Override
  public Optional<AuthenticatedSession> findActiveSession(String tokenHash, Instant now) {
    StoredSession storedSession = sessionsByTokenHash.get(tokenHash);
    if (storedSession == null
        || storedSession.revoked()
        || !storedSession.expiresAt().isAfter(now)) {
      return Optional.empty();
    }

    UserCredential user = userById(storedSession.userId());
    if (!"active".equals(user.status())) {
      return Optional.empty();
    }

    return Optional.of(
        new AuthenticatedSession(
            storedSession.sessionId(),
            user.userId(),
            user.email(),
            user.displayName(),
            storedSession.expiresAt(),
            accessByUserId.getOrDefault(user.userId(), List.of())));
  }

  @Override
  public void touchSession(UUID sessionId, Instant now) {
    // Activity writes are intentionally irrelevant to the in-memory test double.
  }

  @Override
  public void revokeSession(UUID sessionId, Instant now) {
    sessionsByTokenHash.replaceAll(
        (tokenHash, session) ->
            session.sessionId().equals(sessionId)
                ? new StoredSession(
                    session.sessionId(),
                    session.userId(),
                    session.tokenHash(),
                    session.expiresAt(),
                    true)
                : session);
  }

  public String passwordHash(String email) {
    return findUserCredential(email).orElseThrow().passwordHash();
  }

  public List<String> sessionTokenHashes() {
    return List.copyOf(sessionsByTokenHash.keySet());
  }

  public void disableUser(String email) {
    String normalizedEmail = normalizeEmail(email);
    UserCredential user = usersByEmail.get(normalizedEmail);
    if (user == null) {
      throw new IllegalArgumentException("Unknown test user");
    }
    usersByEmail.put(
        normalizedEmail,
        new UserCredential(
            user.userId(), user.email(), user.passwordHash(), user.displayName(), "disabled"));
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private WorkspaceRecord requiredWorkspace(long workspaceId) {
    WorkspaceRecord workspace = workspacesById.get(workspaceId);
    if (workspace == null) {
      throw new IllegalArgumentException("Unknown test workspace");
    }
    return workspace;
  }

  private UserCredential userById(long userId) {
    return usersByEmail.values().stream()
        .filter((user) -> user.userId() == userId)
        .findFirst()
        .orElseThrow();
  }

  private record WorkspaceRecord(long id, long userId, String name) {}

  private record StoredSession(
      UUID sessionId, long userId, String tokenHash, Instant expiresAt, boolean revoked) {}
}
