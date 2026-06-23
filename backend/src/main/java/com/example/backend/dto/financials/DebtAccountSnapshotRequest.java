package com.example.backend.dto.financials;

public record DebtAccountSnapshotRequest(Long id, String account, String company, double amount) {}
