package com.example.backend.dto.financials;

public record IncomeSummaryItemResponse(long id, String category, String interval, double amount) {}
