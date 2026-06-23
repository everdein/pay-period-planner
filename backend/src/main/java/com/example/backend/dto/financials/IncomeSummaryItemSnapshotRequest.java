package com.example.backend.dto.financials;

public record IncomeSummaryItemSnapshotRequest(
    Long id, String category, String interval, double amount) {}
