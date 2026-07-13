package com.example.backend.dto.financials;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record AnnualWithdrawalRequest(
    @NotBlank(message = "Annual withdrawal name is required") String bill,
    @Min(value = 1, message = "Month must be between 1 and 12")
        @Max(value = 12, message = "Month must be between 1 and 12")
        int month,
    @Min(value = 1, message = "Day must be between 1 and 31")
        @Max(value = 31, message = "Day must be between 1 and 31")
        int day,
    @NotNull(message = "Annual withdrawal amount is required")
        @PositiveOrZero(message = "Annual withdrawal amount must be positive")
        BigDecimal amount,
    @NotBlank(message = "Annual withdrawal account is required") String account,
    boolean paid) {}
