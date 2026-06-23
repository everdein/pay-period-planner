package com.example.backend.dto.financials;

public record AnnualWithdrawalSnapshotRequest(
    Long id, String bill, int month, int day, double amount, String account, boolean paid) {}
