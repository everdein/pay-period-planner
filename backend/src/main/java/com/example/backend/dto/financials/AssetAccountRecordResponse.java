package com.example.backend.dto.financials;

import java.math.BigDecimal;

public record AssetAccountRecordResponse(
    long id,
    String categoryKey,
    String categoryLabel,
    String account,
    String company,
    BigDecimal amount) {}
