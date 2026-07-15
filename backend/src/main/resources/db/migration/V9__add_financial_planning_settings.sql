alter table financial_record_snapshot
    add column if not exists pay_cadence text not null default 'BIWEEKLY',
    add column if not exists planning_time_zone text not null default 'UTC';

alter table financial_record_snapshot
    add constraint financial_record_snapshot_pay_cadence_check
    check (pay_cadence in ('WEEKLY', 'BIWEEKLY', 'SEMIMONTHLY', 'MONTHLY'));
