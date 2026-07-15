package com.example.backend.dto.financials;

import jakarta.validation.constraints.NotNull;

public record FinancialProjectionRolesRequest(
    @NotNull(message = "Rent bill projection role is required") Long rentBillId,
    @NotNull(message = "Rent reserve projection role is required") Long rentReserveAssetAccountId,
    @NotNull(message = "Primary paycheck projection role is required")
        Long primaryPaycheckIncomeSummaryItemId) {}
