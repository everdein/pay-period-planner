package com.example.backend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionConfigurationGuardTests {

  @Test
  void ignoresNonProductionProfiles() {
    assertThatCode(
            () ->
                ProductionConfigurationGuard.validate(
                    List.of("json"),
                    FinancialsSecurityDefaults.LOCAL_USERNAME,
                    FinancialsSecurityDefaults.LOCAL_PASSWORD,
                    List.of("*")))
        .doesNotThrowAnyException();
  }

  @Test
  void requiresPostgresProfileWhenProductionProfileIsActive() {
    assertThatThrownBy(
            () ->
                ProductionConfigurationGuard.validate(
                    List.of("prod"), "financial_owner", "not-local", List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("prod profile must be combined with the postgres profile");
  }

  @Test
  void rejectsLocalCredentialsWhenProductionProfileIsActive() {
    assertThatThrownBy(
            () ->
                ProductionConfigurationGuard.validate(
                    List.of("prod", "postgres"),
                    FinancialsSecurityDefaults.LOCAL_USERNAME,
                    FinancialsSecurityDefaults.LOCAL_PASSWORD,
                    List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must override local defaults");
  }

  @Test
  void rejectsWildcardCorsWhenProductionProfileIsActive() {
    assertThatThrownBy(
            () ->
                ProductionConfigurationGuard.validate(
                    List.of("prod", "postgres"), "financial_owner", "not-local", List.of("*")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot include '*'");
  }

  @Test
  void allowsHardenedProductionConfiguration() {
    assertThatCode(
            () ->
                ProductionConfigurationGuard.validate(
                    List.of("prod", "postgres"),
                    "financial_owner",
                    "not-local",
                    List.of("https://finance.example.com")))
        .doesNotThrowAnyException();
  }
}
