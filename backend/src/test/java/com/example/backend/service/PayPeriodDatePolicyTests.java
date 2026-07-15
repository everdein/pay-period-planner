package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PayPeriodDatePolicyTests {

  @Test
  void rollsAnchoredPayPeriodForwardToCurrentDate() {
    Clock clock = Clock.fixed(Instant.parse("2026-06-22T12:00:00Z"), ZoneOffset.UTC);

    PayPeriodDatePolicy.PayPeriod payPeriod =
        PayPeriodDatePolicy.currentPayPeriod(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15), clock);

    assertThat(payPeriod.startDate()).isEqualTo(LocalDate.of(2026, 6, 15));
    assertThat(payPeriod.endDate()).isEqualTo(LocalDate.of(2026, 6, 29));
  }

  @Test
  void rollsAnchoredPayPeriodBackwardToCurrentDate() {
    Clock clock = Clock.fixed(Instant.parse("2025-12-28T12:00:00Z"), ZoneOffset.UTC);

    PayPeriodDatePolicy.PayPeriod payPeriod =
        PayPeriodDatePolicy.currentPayPeriod(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15), clock);

    assertThat(payPeriod.startDate()).isEqualTo(LocalDate.of(2025, 12, 17));
    assertThat(payPeriod.endDate()).isEqualTo(LocalDate.of(2025, 12, 31));
  }

  @Test
  void derivesThePlanningDateFromTheWorkspaceTimeZone() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:30:00Z"), ZoneOffset.UTC);

    assertThat(PayPeriodDatePolicy.currentDate(clock, ZoneId.of("UTC")))
        .isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(PayPeriodDatePolicy.currentDate(clock, ZoneId.of("America/Los_Angeles")))
        .isEqualTo(LocalDate.of(2025, 12, 31));

    PayPeriodDatePolicy.PayPeriod westCoastPeriod =
        PayPeriodDatePolicy.currentPayPeriod(
            LocalDate.of(2025, 12, 18),
            LocalDate.of(2025, 12, 31),
            clock,
            ZoneId.of("America/Los_Angeles"));

    assertThat(westCoastPeriod.startDate()).isEqualTo(LocalDate.of(2025, 12, 18));
    assertThat(westCoastPeriod.endDate()).isEqualTo(LocalDate.of(2025, 12, 31));
  }

  @Test
  void choosesEndMonthForMonthlyDueDateWhenPeriodCrossesMonth() {
    PayPeriodDatePolicy.PayPeriod payPeriod =
        new PayPeriodDatePolicy.PayPeriod(LocalDate.of(2026, 6, 26), LocalDate.of(2026, 7, 9));

    LocalDate dueDate = PayPeriodDatePolicy.monthlyDueDate(1, payPeriod);

    assertThat(dueDate).isEqualTo(LocalDate.of(2026, 7, 1));
  }

  @Test
  void clampsMonthlyDueDateToLastDayOfMonth() {
    PayPeriodDatePolicy.PayPeriod payPeriod =
        new PayPeriodDatePolicy.PayPeriod(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 15));

    LocalDate dueDate = PayPeriodDatePolicy.monthlyDueDate(31, payPeriod);

    assertThat(dueDate).isEqualTo(LocalDate.of(2026, 2, 28));
  }

  @Test
  void choosesEndYearForAnnualDueDateWhenPeriodCrossesYear() {
    PayPeriodDatePolicy.PayPeriod payPeriod =
        new PayPeriodDatePolicy.PayPeriod(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 12));

    LocalDate dueDate = PayPeriodDatePolicy.annualDueDate(1, 1, payPeriod);

    assertThat(dueDate).isEqualTo(LocalDate.of(2027, 1, 1));
  }
}
