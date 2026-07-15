package com.example.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.backend.domain.identity.AuthenticatedSession;
import com.example.backend.domain.identity.WorkspaceAccess;
import com.example.backend.service.WorkspaceAccessDeniedException;
import com.example.backend.service.WorkspaceSelectionException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthenticatedRequestWorkspaceTests {

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void selectsTheOnlyWorkspaceWithoutAHeader() {
    authenticate(List.of(new WorkspaceAccess(41, "Personal", "owner")));

    assertThat(currentWorkspace(new MockHttpServletRequest()).requireWorkspaceId()).isEqualTo(41);
  }

  @Test
  void requiresASelectionForMultipleMemberships() {
    authenticate(
        List.of(
            new WorkspaceAccess(41, "Personal", "owner"),
            new WorkspaceAccess(42, "Household", "member")));

    assertThatThrownBy(() -> currentWorkspace(new MockHttpServletRequest()).requireWorkspaceId())
        .isInstanceOf(WorkspaceSelectionException.class)
        .hasMessageContaining(AuthenticatedRequestWorkspace.WORKSPACE_ID_HEADER);
  }

  @Test
  void acceptsAnExplicitMembership() {
    authenticate(
        List.of(
            new WorkspaceAccess(41, "Personal", "owner"),
            new WorkspaceAccess(42, "Household", "member")));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(AuthenticatedRequestWorkspace.WORKSPACE_ID_HEADER, "42");

    assertThat(currentWorkspace(request).requireWorkspaceId()).isEqualTo(42);
  }

  @Test
  void rejectsMalformedAndUnauthorizedSelections() {
    authenticate(List.of(new WorkspaceAccess(41, "Personal", "owner")));
    MockHttpServletRequest malformed = new MockHttpServletRequest();
    malformed.addHeader(AuthenticatedRequestWorkspace.WORKSPACE_ID_HEADER, "invalid");
    MockHttpServletRequest unauthorized = new MockHttpServletRequest();
    unauthorized.addHeader(AuthenticatedRequestWorkspace.WORKSPACE_ID_HEADER, "99");

    assertThatThrownBy(() -> currentWorkspace(malformed).requireWorkspaceId())
        .isInstanceOf(WorkspaceSelectionException.class);
    assertThatThrownBy(() -> currentWorkspace(unauthorized).requireWorkspaceId())
        .isInstanceOf(WorkspaceAccessDeniedException.class);
  }

  @Test
  void requiresAnAccountSessionPrincipal() {
    assertThatThrownBy(() -> currentWorkspace(new MockHttpServletRequest()).requireWorkspaceId())
        .isInstanceOf(WorkspaceAccessDeniedException.class);
  }

  private AuthenticatedRequestWorkspace currentWorkspace(MockHttpServletRequest request) {
    return new AuthenticatedRequestWorkspace(request);
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
