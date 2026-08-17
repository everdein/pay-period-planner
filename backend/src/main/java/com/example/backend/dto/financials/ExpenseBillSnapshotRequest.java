package com.example.backend.dto.financials;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ExpenseBillSnapshotRequest(
    Long id,
    @NotBlank(message = "Bill name is required") String bill,
    @Min(value = 1, message = "Due day must be between 1 and 31")
        @Max(value = 31, message = "Due day must be between 1 and 31")
        int dueDay,
    @NotNull(message = "Amount is required")
        @PositiveOrZero(message = "Amount must be positive")
        @Digits(
            integer = 12,
            fraction = 2,
            message = "Amount must have at most 12 integer and 2 fractional digits")
        BigDecimal amount,
    @NotBlank(message = "Account is required") String account,
    boolean paid) {}
