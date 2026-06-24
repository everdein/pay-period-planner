package com.example.backend.repository;

import com.example.backend.config.FinancialsPersistenceProperties;
import org.springframework.stereotype.Component;

@Component
public class FinancialsPersistenceMode {

  private final FinancialsPersistenceProperties properties;

  public FinancialsPersistenceMode(FinancialsPersistenceProperties properties) {
    this.properties = properties;
  }

  public boolean isPostgres() {
    return properties.mode() == FinancialsPersistenceProperties.Mode.POSTGRES;
  }
}
