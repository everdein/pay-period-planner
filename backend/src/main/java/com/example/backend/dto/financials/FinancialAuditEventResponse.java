package com.example.backend.dto.financials;

import java.time.Instant;

public record FinancialAuditEventResponse(
    long id,
    Instant occurredAt,
    String action,
    String resourceType,
    Long resourceId,
    long versionBefore,
    long versionAfter,
    String summary,
    FinancialProjectionSummaryResponse projectionSummary) {}
