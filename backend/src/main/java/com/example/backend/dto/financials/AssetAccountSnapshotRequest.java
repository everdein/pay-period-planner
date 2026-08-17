package com.example.backend.dto.financials;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record AssetAccountSnapshotRequest(
    Long id,
    @NotBlank(message = "Asset account is required") String account,
    @NotBlank(message = "Asset company is required") String company,
    @NotNull(message = "Asset amount is required")
        @PositiveOrZero(message = "Asset amount must be positive")
        @Digits(
            integer = 12,
            fraction = 2,
            message = "Asset amount must have at most 12 integer and 2 fractional digits")
        BigDecimal amount) {}
