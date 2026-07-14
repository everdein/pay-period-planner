package com.example.backend.service;

import com.example.backend.config.FinancialsSecurityProperties;
import com.example.backend.domain.identity.AuthenticatedSession;
import com.example.backend.repository.AccountSessionRepository;
import com.example.backend.repository.AccountSessionRepository.UserCredential;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountSessionService {

  private static final int MINIMUM_PASSWORD_BYTES = 12;
  private static final int MAXIMUM_BCRYPT_PASSWORD_BYTES = 72;
  private static final int SESSION_TOKEN_BYTES = 32;

  private final AccountSessionRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final FinancialsSecurityProperties securityProperties;
  private final Clock clock;
  private final SecureRandom secureRandom;
  private final String dummyPasswordHash;

  @Autowired
  public AccountSessionService(
      AccountSessionRepository repository,
      PasswordEncoder passwordEncoder,
      FinancialsSecurityProperties securityProperties) {
    this(repository, passwordEncoder, securityProperties, Clock.systemUTC(), new SecureRandom());
  }

  AccountSessionService(
      AccountSessionRepository repository,
      PasswordEncoder passwordEncoder,
      FinancialsSecurityProperties securityProperties,
      Clock clock,
      SecureRandom secureRandom) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
    this.securityProperties = securityProperties;
    this.clock = clock;
    this.secureRandom = secureRandom;
    this.dummyPasswordHash = passwordEncoder.encode("not-a-real-account-password");
  }

  @Transactional
  public IssuedSession signUp(String email, String password, String displayName) {
    String normalizedEmail = email.trim();
    String normalizedDisplayName = displayName.trim();
    validateSignUp(normalizedEmail, password, normalizedDisplayName);

    try {
      long userId =
          repository.createUser(
              normalizedEmail, passwordEncoder.encode(password), normalizedDisplayName);
      long workspaceId = repository.createWorkspace(userId, "Personal");
      repository.createOwnerMembership(userId, workspaceId);
      return issueSession(userId);
    } catch (DuplicateKeyException exception) {
      throw new AccountConflictException();
    }
  }

  @Transactional
  public IssuedSession signIn(String email, String password) {
    Optional<UserCredential> user = repository.findUserCredential(email.trim());
    String storedHash = user.map(UserCredential::passwordHash).orElse(dummyPasswordHash);
    boolean passwordMatches =
        passwordWithinBcryptLimit(password) && passwordEncoder.matches(password, storedHash);

    if (user.isEmpty() || !passwordMatches || !"active".equals(user.orElseThrow().status())) {
      throw new AccountAuthenticationException();
    }

    return issueSession(user.orElseThrow().userId());
  }

  @Transactional
  public Optional<AuthenticatedSession> recover(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }

    Instant now = clock.instant();
    Optional<AuthenticatedSession> session = repository.findActiveSession(hashToken(rawToken), now);
    session.ifPresent((activeSession) -> repository.touchSession(activeSession.sessionId(), now));
    return session;
  }

  @Transactional
  public void signOut(UUID sessionId) {
    repository.revokeSession(sessionId, clock.instant());
  }

  private IssuedSession issueSession(long userId) {
    byte[] tokenBytes = new byte[SESSION_TOKEN_BYTES];
    secureRandom.nextBytes(tokenBytes);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    String tokenHash = hashToken(rawToken);
    UUID sessionId = UUID.randomUUID();
    Instant expiresAt = clock.instant().plus(securityProperties.sessionDuration());

    repository.createSession(sessionId, userId, tokenHash, expiresAt);
    AuthenticatedSession session =
        repository
            .findActiveSession(tokenHash, clock.instant())
            .orElseThrow(
                () -> new IllegalStateException("The created session could not be loaded"));
    return new IssuedSession(rawToken, session);
  }

  private void validateSignUp(String email, String password, String displayName) {
    if (email.isEmpty() || email.length() > 320) {
      throw new AccountRequestException("Email must be between 1 and 320 characters");
    }
    if (displayName.isEmpty() || displayName.length() > 120) {
      throw new AccountRequestException("Display name must be between 1 and 120 characters");
    }

    int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
    if (passwordBytes < MINIMUM_PASSWORD_BYTES || passwordBytes > MAXIMUM_BCRYPT_PASSWORD_BYTES) {
      throw new AccountRequestException("Password must be between 12 and 72 UTF-8 bytes");
    }
  }

  private boolean passwordWithinBcryptLimit(String password) {
    return password != null
        && password.getBytes(StandardCharsets.UTF_8).length <= MAXIMUM_BCRYPT_PASSWORD_BYTES;
  }

  private String hashToken(String rawToken) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  public record IssuedSession(String rawToken, AuthenticatedSession session) {}
}
