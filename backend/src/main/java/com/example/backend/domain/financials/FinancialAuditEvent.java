package com.example.backend.domain.financials;

import java.time.Instant;

public record FinancialAuditEvent(
    long id,
    Instant occurredAt,
    String action,
    String resourceType,
    Long resourceId,
    long versionBefore,
    long versionAfter,
    String summary,
    FinancialProjectionSummary projectionSummary) {}
