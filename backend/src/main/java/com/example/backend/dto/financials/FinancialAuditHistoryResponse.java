package com.example.backend.dto.financials;

import java.util.List;

public record FinancialAuditHistoryResponse(List<FinancialAuditEventResponse> events) {}
