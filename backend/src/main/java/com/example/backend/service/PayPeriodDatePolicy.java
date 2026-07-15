package com.example.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

final class PayPeriodDatePolicy {

  private PayPeriodDatePolicy() {}

  static PayPeriod currentPayPeriod(LocalDate anchorStart, LocalDate anchorEnd, Clock clock) {
    return currentPayPeriod(anchorStart, anchorEnd, LocalDate.now(clock));
  }

  static PayPeriod currentPayPeriod(
      LocalDate anchorStart, LocalDate anchorEnd, Clock clock, ZoneId timeZone) {
    return currentPayPeriod(anchorStart, anchorEnd, currentDate(clock, timeZone));
  }

  static LocalDate currentDate(Clock clock, ZoneId timeZone) {
    return LocalDate.ofInstant(clock.instant(), timeZone);
  }

  static PayPeriod currentPayPeriod(LocalDate anchorStart, LocalDate anchorEnd, LocalDate today) {
    LocalDate startDate = anchorStart;
    LocalDate endDate = anchorEnd;
    long periodDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

    while (today.isAfter(endDate)) {
      startDate = startDate.plusDays(periodDays);
      endDate = endDate.plusDays(periodDays);
    }

    while (today.isBefore(startDate)) {
      startDate = startDate.minusDays(periodDays);
      endDate = endDate.minusDays(periodDays);
    }

    return new PayPeriod(startDate, endDate);
  }

  static LocalDate monthlyDueDate(int dueDay, PayPeriod payPeriod) {
    LocalDate dueDate =
        safeDate(payPeriod.startDate().getYear(), payPeriod.startDate().getMonthValue(), dueDay);

    if (dueDate.isBefore(payPeriod.startDate())
        && payPeriod.startDate().getMonthValue() != payPeriod.endDate().getMonthValue()) {
      return safeDate(payPeriod.endDate().getYear(), payPeriod.endDate().getMonthValue(), dueDay);
    }

    return dueDate;
  }

  static LocalDate annualDueDate(int month, int day, PayPeriod payPeriod) {
    LocalDate dueDate = safeDate(payPeriod.startDate().getYear(), month, day);

    if (dueDate.isBefore(payPeriod.startDate())
        && payPeriod.startDate().getYear() != payPeriod.endDate().getYear()) {
      return safeDate(payPeriod.endDate().getYear(), month, day);
    }

    return dueDate;
  }

  private static LocalDate safeDate(int year, int month, int day) {
    LocalDate firstOfMonth = LocalDate.of(year, month, 1);
    int safeDay = Math.min(day, firstOfMonth.lengthOfMonth());
    return LocalDate.of(year, month, safeDay);
  }

  record PayPeriod(LocalDate startDate, LocalDate endDate) {}
}
