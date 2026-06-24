package com.example.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "financials.persistence")
public record FinancialsPersistenceProperties(Mode mode) {

  public FinancialsPersistenceProperties {
    if (mode == null) {
      mode = Mode.JSON;
    }
  }

  public enum Mode {
    JSON,
    POSTGRES
  }
}
