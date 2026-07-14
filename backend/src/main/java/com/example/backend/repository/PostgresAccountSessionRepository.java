package com.example.backend.repository;

import com.example.backend.domain.identity.AuthenticatedSession;
import com.example.backend.domain.identity.WorkspaceAccess;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresAccountSessionRepository implements AccountSessionRepository {

  private final JdbcTemplate jdbcTemplate;

  public PostgresAccountSessionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<UserCredential> findUserCredential(String email) {
    return jdbcTemplate
        .query(
            """
            select id, email, password_hash, display_name, status
            from application_user
            where normalized_email = lower(btrim(?))
            """,
            (resultSet, rowNumber) ->
                new UserCredential(
                    resultSet.getLong("id"),
                    resultSet.getString("email"),
                    resultSet.getString("password_hash"),
                    resultSet.getString("display_name"),
                    resultSet.getString("status")),
            email)
        .stream()
        .findFirst();
  }

  @Override
  public long createUser(String email, String passwordHash, String displayName) {
    Long userId =
        jdbcTemplate.queryForObject(
            """
            insert into application_user (email, password_hash, display_name)
            values (?, ?, ?)
            returning id
            """,
            Long.class,
            email,
            passwordHash,
            displayName);
    return requiredId(userId, "user");
  }

  @Override
  public long createWorkspace(long userId, String name) {
    Long workspaceId =
        jdbcTemplate.queryForObject(
            """
            insert into workspace (name, created_by_user_id)
            values (?, ?)
            returning id
            """,
            Long.class,
            name,
            userId);
    return requiredId(workspaceId, "workspace");
  }

  @Override
  public void createOwnerMembership(long userId, long workspaceId) {
    jdbcTemplate.update(
        """
        insert into workspace_membership (workspace_id, user_id, role)
        values (?, ?, 'owner')
        """,
        workspaceId,
        userId);
  }

  @Override
  public void createSession(UUID sessionId, long userId, String tokenHash, Instant expiresAt) {
    jdbcTemplate.update(
        """
        insert into application_session (id, user_id, token_hash, expires_at)
        values (?, ?, ?, ?)
        """,
        sessionId,
        userId,
        tokenHash,
        Timestamp.from(expiresAt));
  }

  @Override
  public Optional<AuthenticatedSession> findActiveSession(String tokenHash, Instant now) {
    Optional<SessionUser> sessionUser =
        jdbcTemplate
            .query(
                """
                select
                    session.id as session_id,
                    session.user_id,
                    session.expires_at,
                    app_user.email,
                    app_user.display_name
                from application_session session
                join application_user app_user on app_user.id = session.user_id
                where session.token_hash = ?
                  and session.revoked_at is null
                  and session.expires_at > ?
                  and app_user.status = 'active'
                """,
                (resultSet, rowNumber) ->
                    new SessionUser(
                        resultSet.getObject("session_id", UUID.class),
                        resultSet.getLong("user_id"),
                        resultSet.getString("email"),
                        resultSet.getString("display_name"),
                        resultSet.getTimestamp("expires_at").toInstant()),
                tokenHash,
                Timestamp.from(now))
            .stream()
            .findFirst();

    return sessionUser.map(this::withWorkspaceAccess);
  }

  @Override
  public void touchSession(UUID sessionId, Instant now) {
    jdbcTemplate.update(
        """
        update application_session
        set last_seen_at = ?
        where id = ?
          and last_seen_at < ?
          and revoked_at is null
        """,
        Timestamp.from(now),
        sessionId,
        Timestamp.from(now.minusSeconds(300)));
  }

  @Override
  public void revokeSession(UUID sessionId, Instant now) {
    jdbcTemplate.update(
        """
        update application_session
        set revoked_at = ?
        where id = ?
          and revoked_at is null
        """,
        Timestamp.from(now),
        sessionId);
  }

  private AuthenticatedSession withWorkspaceAccess(SessionUser sessionUser) {
    List<WorkspaceAccess> workspaces =
        jdbcTemplate.query(
            """
            select membership.workspace_id, workspace.name, membership.role
            from workspace_membership membership
            join workspace on workspace.id = membership.workspace_id
            where membership.user_id = ?
            order by membership.workspace_id
            """,
            (resultSet, rowNumber) ->
                new WorkspaceAccess(
                    resultSet.getLong("workspace_id"),
                    resultSet.getString("name"),
                    resultSet.getString("role")),
            sessionUser.userId());

    return new AuthenticatedSession(
        sessionUser.sessionId(),
        sessionUser.userId(),
        sessionUser.email(),
        sessionUser.displayName(),
        sessionUser.expiresAt(),
        workspaces);
  }

  private long requiredId(Long id, String recordType) {
    if (id == null) {
      throw new IllegalStateException(
          "PostgreSQL did not return the created " + recordType + " ID");
    }
    return id;
  }

  private record SessionUser(
      UUID sessionId, long userId, String email, String displayName, Instant expiresAt) {}
}
