package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.backend.domain.identity.AuthenticatedSession;
import com.example.backend.domain.identity.WorkspaceAccess;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthenticatedWorkspaceResolverTests {

  private final AuthenticatedWorkspaceResolver resolver = new AuthenticatedWorkspaceResolver();

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void selectsTheOnlyWorkspaceWithoutAHeader() {
    authenticate(List.of(new WorkspaceAccess(41, "Personal", "owner")));

    assertThat(resolver.requireWorkspaceId(new MockHttpServletRequest())).isEqualTo(41);
  }

  @Test
  void requiresASelectionForMultipleMemberships() {
    authenticate(
        List.of(
            new WorkspaceAccess(41, "Personal", "owner"),
            new WorkspaceAccess(42, "Household", "member")));

    assertThatThrownBy(() -> resolver.requireWorkspaceId(new MockHttpServletRequest()))
        .isInstanceOf(WorkspaceSelectionException.class)
        .hasMessageContaining(AuthenticatedWorkspaceResolver.WORKSPACE_ID_HEADER);
  }

  @Test
  void acceptsAnExplicitMembership() {
    authenticate(
        List.of(
            new WorkspaceAccess(41, "Personal", "owner"),
            new WorkspaceAccess(42, "Household", "member")));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(AuthenticatedWorkspaceResolver.WORKSPACE_ID_HEADER, "42");

    assertThat(resolver.requireWorkspaceId(request)).isEqualTo(42);
  }

  @Test
  void rejectsMalformedAndUnauthorizedSelections() {
    authenticate(List.of(new WorkspaceAccess(41, "Personal", "owner")));
    MockHttpServletRequest malformed = new MockHttpServletRequest();
    malformed.addHeader(AuthenticatedWorkspaceResolver.WORKSPACE_ID_HEADER, "invalid");
    MockHttpServletRequest unauthorized = new MockHttpServletRequest();
    unauthorized.addHeader(AuthenticatedWorkspaceResolver.WORKSPACE_ID_HEADER, "99");

    assertThatThrownBy(() -> resolver.requireWorkspaceId(malformed))
        .isInstanceOf(WorkspaceSelectionException.class);
    assertThatThrownBy(() -> resolver.requireWorkspaceId(unauthorized))
        .isInstanceOf(WorkspaceAccessDeniedException.class);
  }

  @Test
  void requiresAnAccountSessionPrincipal() {
    assertThatThrownBy(() -> resolver.requireWorkspaceId(new MockHttpServletRequest()))
        .isInstanceOf(WorkspaceAccessDeniedException.class);
  }

  private void authenticate(List<WorkspaceAccess> workspaces) {
    AuthenticatedSession session =
        new AuthenticatedSession(
            UUID.randomUUID(),
            7,
            "owner@example.com",
            "Owner",
            Instant.parse("2026-08-14T12:00:00Z"),
            workspaces);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                session, null, List.of(new SimpleGrantedAuthority("ROLE_WORKSPACE"))));
  }
}
