package com.example.backend.domain.financials;

import java.math.BigDecimal;

public record AssetAccount(
    long id,
    String categoryKey,
    String categoryLabel,
    String account,
    String company,
    BigDecimal amount) {

  public AssetAccount withId(long id) {
    return new AssetAccount(id, categoryKey, categoryLabel, account, company, amount);
  }
}
