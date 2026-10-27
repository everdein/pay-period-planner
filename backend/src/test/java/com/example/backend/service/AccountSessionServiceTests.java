package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.backend.config.FinancialsSecurityProperties;
import com.example.backend.config.RegistrationMode;
import com.example.backend.domain.identity.AuthenticatedSession;
import com.example.backend.service.AccountSessionService.IssuedSession;
import com.example.backend.support.InMemoryAccountSessionRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountSessionServiceTests {

  private static final Instant NOW = Instant.parse("2026-07-14T18:00:00Z");

  private InMemoryAccountSessionRepository repository;
  private PasswordEncoder passwordEncoder;
  private AccountSessionService service;
  private FinancialsSecurityProperties properties;

  @BeforeEach
  void setUp() {
    repository = new InMemoryAccountSessionRepository();
    passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    properties = new FinancialsSecurityProperties();
    properties.setSessionDuration(Duration.ofHours(2));
    service =
        new AccountSessionService(
            repository,
            passwordEncoder,
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC),
            new SecureRandom());
  }

  @Test
  void closedRegistrationRejectsSignupButPreservesSignInAndSessionRecovery() {
    IssuedSession owner = service.signUp("owner@example.test", "owner-password-123", "Owner");
    properties.setRegistrationMode(RegistrationMode.DISABLED);
    assertThat(service.registrationEnabled()).isFalse();
    assertThatThrownBy(() -> service.signUp("other@example.test", "other-password-123", "Other"))
        .isInstanceOf(RegistrationClosedException.class);
    assertThat(service.signIn("owner@example.test", "owner-password-123").session().userId())
        .isEqualTo(owner.session().userId());
    assertThat(service.recover(owner.rawToken())).isPresent();
  }

  @Test
  void ownerBootstrapRequiresProofAndRejectsLaterAccountsWithoutRevealingCounts() {
    String token = "synthetic-bootstrap-token-at-least-32-characters";
    properties.setRegistrationMode(RegistrationMode.OWNER_BOOTSTRAP);
    properties.setOwnerBootstrapTokenHash(sha256(token));
    assertThat(service.registrationEnabled()).isFalse();
    for (String rejected :
        new String[] {null, "short", "wrong-token-at-least-32-characters", "x".repeat(1025)}) {
      assertThatThrownBy(
              () -> service.signUp("owner@example.test", "owner-password-123", "Owner", rejected))
          .isInstanceOf(RegistrationClosedException.class)
          .hasMessage("Account registration is unavailable");
    }
    IssuedSession issued =
        service.signUp("owner@example.test", "owner-password-123", "Owner", token);
    assertThat(service.recover(issued.rawToken())).isPresent();
    assertThatThrownBy(
            () -> service.signUp("other@example.test", "other-password-123", "Other", token))
        .isInstanceOf(RegistrationClosedException.class)
        .hasMessage("Account registration is unavailable");
  }

  @Test
  void missingAndInvalidBootstrapVerifiersFailClosed() {
    properties.setRegistrationMode(RegistrationMode.OWNER_BOOTSTRAP);
    for (String verifier : new String[] {null, "", "not-a-verifier", "z".repeat(64)}) {
      properties.setOwnerBootstrapTokenHash(verifier);
      assertThatThrownBy(
              () ->
                  service.signUp(
                      "owner@example.test",
                      "owner-password-123",
                      "Owner",
                      "synthetic-bootstrap-token-at-least-32-characters"))
          .isInstanceOf(RegistrationClosedException.class);
    }
  }

  private String sha256(String token) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Test
  void signsUpHashesSecretsRecoversAndRevokesTheSession() {
    IssuedSession issued =
        service.signUp(" Owner@Example.com ", "correct-horse-battery", " Owner ");

    assertThat(
            passwordEncoder.matches(
                "correct-horse-battery", repository.passwordHash("owner@example.com")))
        .isTrue();
    assertThat(repository.sessionTokenHashes())
        .singleElement()
        .satisfies(
            (tokenHash) -> {
              assertThat(tokenHash).hasSize(64);
              assertThat(tokenHash).isNotEqualTo(issued.rawToken());
            });
    assertThat(issued.session().email()).isEqualTo("Owner@Example.com");
    assertThat(issued.session().displayName()).isEqualTo("Owner");
    assertThat(issued.session().expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
    assertThat(issued.session().workspaces())
        .singleElement()
        .satisfies(
            (workspace) -> {
              assertThat(workspace.workspaceName()).isEqualTo("Personal");
              assertThat(workspace.role()).isEqualTo("owner");
            });

    assertThat(service.recover(issued.rawToken())).contains(issued.session());
    assertThat(issued.session().getName()).isEqualTo("Owner@Example.com");
    assertThat(service.recover(null)).isEmpty();
    assertThat(service.recover(" ")).isEmpty();
    assertThat(service.recover("unknown-token")).isEmpty();

    service.signOut(issued.session().sessionId());

    assertThat(service.recover(issued.rawToken())).isEmpty();
  }

  @Test
  void signsInByNormalizedEmailAndKeepsWorkspaceMembershipsIsolated() {
    IssuedSession owner = service.signUp("owner@example.com", "owner-password-123", "Owner");
    IssuedSession other = service.signUp("other@example.com", "other-password-123", "Other");

    AuthenticatedSession signedIn =
        service.signIn(" OWNER@example.com ", "owner-password-123").session();

    assertThat(signedIn.userId()).isEqualTo(owner.session().userId());
    assertThat(signedIn.workspaces())
        .extracting((workspace) -> workspace.workspaceId())
        .containsExactly(owner.session().workspaces().getFirst().workspaceId());
    assertThat(signedIn.workspaces()).doesNotContainAnyElementsOf(other.session().workspaces());

    assertThatThrownBy(() -> service.signIn("owner@example.com", "not-the-owner-password"))
        .isInstanceOf(AccountAuthenticationException.class)
        .hasMessage("Email or password was not accepted");
    assertThatThrownBy(() -> service.signIn("missing@example.com", "not-the-owner-password"))
        .isInstanceOf(AccountAuthenticationException.class);

    repository.disableUser("owner@example.com");
    assertThatThrownBy(() -> service.signIn("owner@example.com", "owner-password-123"))
        .isInstanceOf(AccountAuthenticationException.class);
  }

  @Test
  void rejectsInvalidOrDuplicateSignupInputAndOversizedSignInPasswords() {
    service.signUp("owner@example.com", "owner-password-123", "Owner");

    assertThatThrownBy(
            () -> service.signUp("OWNER@example.com", "duplicate-password-123", "Duplicate"))
        .isInstanceOf(AccountConflictException.class)
        .hasMessage("An account already exists for that email address");
    assertThatThrownBy(() -> service.signUp(" ", "valid-password-123", "Owner"))
        .isInstanceOf(AccountRequestException.class)
        .hasMessageContaining("Email");
    assertThatThrownBy(() -> service.signUp("new@example.com", "valid-password-123", " "))
        .isInstanceOf(AccountRequestException.class)
        .hasMessageContaining("Display name");
    assertThatThrownBy(() -> service.signUp("new@example.com", "short", "New"))
        .isInstanceOf(AccountRequestException.class)
        .hasMessageContaining("12 and 72 UTF-8 bytes");
    assertThatThrownBy(() -> service.signUp("new@example.com", "x".repeat(73), "New"))
        .isInstanceOf(AccountRequestException.class);
    assertThatThrownBy(() -> service.signIn("owner@example.com", "x".repeat(73)))
        .isInstanceOf(AccountAuthenticationException.class);
  }
}
