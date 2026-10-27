package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.example.backend.config.FinancialsSecurityProperties;
import com.example.backend.config.RegistrationMode;
import com.example.backend.domain.identity.AuthenticatedSession;
import com.example.backend.repository.PostgresAccountSessionRepository;
import com.example.backend.service.AccountSessionService.IssuedSession;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION_TESTS", matches = "true")
class AccountSessionServiceIT {

  private static final String TEST_SCHEMA = "account_session_service_test";
  private JdbcTemplate jdbcTemplate;
  private PasswordEncoder passwordEncoder;
  private AccountSessionService service;
  private Instant now;
  private FinancialsSecurityProperties securityProperties;
  private TransactionTemplate transaction;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setUrl(withCurrentSchema(databaseUrl(), TEST_SCHEMA));
    dataSource.setUsername(requiredEnvironment("DATABASE_USERNAME"));
    dataSource.setPassword(requiredEnvironment("DATABASE_PASSWORD"));

    Flyway.configure()
        .dataSource(dataSource)
        .defaultSchema(TEST_SCHEMA)
        .schemas(TEST_SCHEMA)
        .locations("classpath:db/migration")
        .validateMigrationNaming(true)
        .load()
        .migrate();

    jdbcTemplate = new JdbcTemplate(dataSource);
    transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    securityProperties = new FinancialsSecurityProperties();
    securityProperties.setSessionDuration(Duration.ofHours(2));
    now = Instant.now().plusSeconds(60);
    service =
        new AccountSessionService(
            new PostgresAccountSessionRepository(jdbcTemplate),
            passwordEncoder,
            securityProperties,
            Clock.fixed(now, ZoneOffset.UTC),
            new SecureRandom());
  }

  @Test
  void concurrentBootstrapTransactionsCreateExactlyOneCompleteOwner() throws Exception {
    String token = "synthetic-bootstrap-token-at-least-32-characters";
    securityProperties.setRegistrationMode(RegistrationMode.OWNER_BOOTSTRAP);
    securityProperties.setOwnerBootstrapTokenHash(
        java.util.HexFormat.of()
            .formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var attempts =
          java.util.stream.IntStream.range(0, 2)
              .mapToObj(
                  index ->
                      executor.submit(
                          () -> {
                            if (!start.await(10, TimeUnit.SECONDS))
                              throw new IllegalStateException("Bootstrap test did not start");
                            try {
                              transaction.execute(
                                  status ->
                                      service.signUp(
                                          "owner-" + index + "@example.test",
                                          "owner-password-123",
                                          "Owner",
                                          token));
                              return true;
                            } catch (RegistrationClosedException exception) {
                              return false;
                            }
                          }))
              .toList();
      start.countDown();
      assertThat(
              java.util.List.of(
                  attempts.get(0).get(20, TimeUnit.SECONDS),
                  attempts.get(1).get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    for (String table :
        java.util.List.of(
            "application_user", "workspace", "workspace_membership", "application_session")) {
      assertThat(jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class))
          .isEqualTo(1);
    }
  }

  @Test
  void failedBootstrapRollsBackOwnerWorkspaceMembershipAndAllowsRetry() throws Exception {
    String token = "synthetic-bootstrap-token-at-least-32-characters";
    securityProperties.setRegistrationMode(RegistrationMode.OWNER_BOOTSTRAP);
    securityProperties.setOwnerBootstrapTokenHash(
        java.util.HexFormat.of()
            .formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    var failingRepository =
        new PostgresAccountSessionRepository(jdbcTemplate) {
          @Override
          public void createSession(java.util.UUID id, long userId, String hash, Instant expiry) {
            throw new IllegalStateException("Synthetic session failure");
          }
        };
    var failingService =
        new AccountSessionService(failingRepository, passwordEncoder, securityProperties);
    assertThatThrownBy(
            () ->
                transaction.execute(
                    status ->
                        failingService.signUp(
                            "owner@example.test", "owner-password-123", "Owner", token)))
        .isInstanceOf(IllegalStateException.class);
    for (String table :
        java.util.List.of(
            "application_user", "workspace", "workspace_membership", "application_session")) {
      assertThat(jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class))
          .isZero();
    }
    IssuedSession retried =
        transaction.execute(
            status -> service.signUp("owner@example.test", "owner-password-123", "Owner", token));
    assertThat(retried).isNotNull();
  }

  @AfterEach
  void tearDown() {
    if (jdbcTemplate != null) {
      jdbcTemplate.execute("drop schema if exists " + TEST_SCHEMA + " cascade");
    }
  }

  @Test
  void createsHashedCredentialsPersonalWorkspaceAndRecoverableSession() {
    IssuedSession issued =
        service.signUp(" Owner@Example.com ", "correct-horse-battery", " Owner ");

    String passwordHash =
        jdbcTemplate.queryForObject(
            "select password_hash from application_user where id = ?",
            String.class,
            issued.session().userId());
    String tokenHash =
        jdbcTemplate.queryForObject(
            "select token_hash from application_session where id = ?",
            String.class,
            issued.session().sessionId());

    assertThat(passwordHash).isNotEqualTo("correct-horse-battery");
    assertThat(passwordEncoder.matches("correct-horse-battery", passwordHash)).isTrue();
    assertThat(tokenHash).hasSize(64).isNotEqualTo(issued.rawToken());
    assertThat(issued.session().email()).isEqualTo("Owner@Example.com");
    assertThat(issued.session().displayName()).isEqualTo("Owner");
    assertThat(issued.session().expiresAt())
        .isCloseTo(now.plus(Duration.ofHours(2)), within(1, ChronoUnit.MILLIS));
    assertThat(issued.session().workspaces())
        .singleElement()
        .satisfies(
            (workspace) -> {
              assertThat(workspace.workspaceName()).isEqualTo("Personal");
              assertThat(workspace.role()).isEqualTo("owner");
            });

    assertThat(service.recover(issued.rawToken())).contains(issued.session());

    service.signOut(issued.session().sessionId());

    assertThat(service.recover(issued.rawToken())).isEmpty();
  }

  @Test
  void normalizesSignInAndKeepsSessionsInsideTheirUsersWorkspaceMemberships() {
    IssuedSession owner = service.signUp("owner@example.com", "owner-password-123", "Owner");
    IssuedSession other = service.signUp("other@example.com", "other-password-123", "Other");

    IssuedSession signedIn = service.signIn(" OWNER@example.com ", "owner-password-123");
    AuthenticatedSession recoveredOwner = service.recover(signedIn.rawToken()).orElseThrow();
    AuthenticatedSession recoveredOther = service.recover(other.rawToken()).orElseThrow();

    assertThat(recoveredOwner.userId()).isEqualTo(owner.session().userId());
    assertThat(recoveredOwner.workspaces())
        .extracting((workspace) -> workspace.workspaceId())
        .containsExactly(owner.session().workspaces().getFirst().workspaceId());
    assertThat(recoveredOther.workspaces())
        .extracting((workspace) -> workspace.workspaceId())
        .containsExactly(other.session().workspaces().getFirst().workspaceId());
    assertThat(recoveredOwner.workspaces())
        .doesNotContainAnyElementsOf(recoveredOther.workspaces());

    assertThatThrownBy(() -> service.signIn("owner@example.com", "not-the-owner-password"))
        .isInstanceOf(AccountAuthenticationException.class)
        .hasMessage("Email or password was not accepted");
    assertThatThrownBy(
            () -> service.signUp("OWNER@example.com", "another-password-123", "Duplicate"))
        .isInstanceOf(AccountConflictException.class);
  }

  private String databaseUrl() {
    return environmentOrDefault("DATABASE_URL", "jdbc:postgresql://localhost:5432/financial_app");
  }

  private String withCurrentSchema(String url, String schema) {
    String separator = url.contains("?") ? "&" : "?";
    return url + separator + "currentSchema=" + schema;
  }

  private String environmentOrDefault(String name, String defaultValue) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must be set for PostgreSQL integration tests");
    }
    return value;
  }
}
