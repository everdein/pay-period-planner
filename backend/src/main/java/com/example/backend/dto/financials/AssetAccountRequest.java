package com.example.backend.dto.financials;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record AssetAccountRequest(
    @NotBlank(message = "Asset category key is required") String categoryKey,
    @NotBlank(message = "Asset category label is required") String categoryLabel,
    @NotBlank(message = "Asset account is required") String account,
    @NotBlank(message = "Asset company is required") String company,
    @NotNull(message = "Asset amount is required")
        @PositiveOrZero(message = "Asset amount must be positive")
        BigDecimal amount) {}
