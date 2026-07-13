package com.example.backend.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "financials.security")
public class FinancialsSecurityProperties {

  private String username = FinancialsSecurityDefaults.LOCAL_USERNAME;
  private String password = FinancialsSecurityDefaults.LOCAL_PASSWORD;
  private List<String> allowedOrigins = new ArrayList<>();
  private long maxRequestBytes = 1_048_576L;

  public String username() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String password() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public List<String> allowedOrigins() {
    return allowedOrigins.stream().map(String::trim).filter((origin) -> !origin.isEmpty()).toList();
  }

  public void setAllowedOrigins(List<String> allowedOrigins) {
    this.allowedOrigins =
        allowedOrigins == null ? new ArrayList<>() : new ArrayList<>(allowedOrigins);
  }

  public long maxRequestBytes() {
    return maxRequestBytes;
  }

  public void setMaxRequestBytes(long maxRequestBytes) {
    this.maxRequestBytes = maxRequestBytes;
  }
}
