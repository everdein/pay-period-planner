create table if not exists financial_record_snapshot (
    id bigint generated always as identity primary key,
    active boolean not null default true,
    version bigint not null check (version >= 1),
    pay_period_start date not null,
    pay_period_end date not null,
    source_document_id bigint references financial_snapshot_document (id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uq_financial_record_snapshot_active
    on financial_record_snapshot (active)
    where active;

create table if not exists financial_record_monthly_bill (
    id bigint generated always as identity primary key,
    snapshot_id bigint not null references financial_record_snapshot (id) on delete cascade,
    app_record_id bigint not null,
    bill text not null,
    due_day integer not null check (due_day between 1 and 31),
    amount numeric(14, 2) not null check (amount >= 0),
    account text not null,
    paid boolean not null default false
);

create table if not exists financial_record_annual_withdrawal (
    id bigint generated always as identity primary key,
    snapshot_id bigint not null references financial_record_snapshot (id) on delete cascade,
    app_record_id bigint not null,
    bill text not null,
    month integer not null check (month between 1 and 12),
    day integer not null check (day between 1 and 31),
    amount numeric(14, 2) not null check (amount >= 0),
    account text not null,
    paid boolean not null default false
);

create table if not exists financial_record_asset_account (
    id bigint generated always as identity primary key,
    snapshot_id bigint not null references financial_record_snapshot (id) on delete cascade,
    app_record_id bigint not null,
    category_key text not null,
    category_label text not null,
    account text not null,
    company text not null,
    amount numeric(14, 2) not null check (amount >= 0)
);

create table if not exists financial_record_debt_account (
    id bigint generated always as identity primary key,
    snapshot_id bigint not null references financial_record_snapshot (id) on delete cascade,
    app_record_id bigint not null,
    account text not null,
    company text not null,
    amount numeric(14, 2) not null check (amount >= 0)
);

create table if not exists financial_record_income_summary_item (
    id bigint generated always as identity primary key,
    snapshot_id bigint not null references financial_record_snapshot (id) on delete cascade,
    app_record_id bigint not null,
    category text not null,
    interval text not null,
    amount numeric(14, 2) not null check (amount >= 0)
);

create table if not exists financial_record_income_event (
    id bigint generated always as identity primary key,
    snapshot_id bigint not null references financial_record_snapshot (id) on delete cascade,
    app_record_id bigint not null,
    date date not null,
    label text not null,
    type text not null,
    check_number integer check (check_number is null or check_number > 0)
);

create table if not exists financial_record_important_date (
    id bigint generated always as identity primary key,
    snapshot_id bigint not null references financial_record_snapshot (id) on delete cascade,
    app_record_id bigint not null,
    date date not null,
    event text not null,
    type text not null
);

create index if not exists ix_financial_record_monthly_bill_snapshot
    on financial_record_monthly_bill (snapshot_id, due_day);
create index if not exists ix_financial_record_annual_withdrawal_snapshot
    on financial_record_annual_withdrawal (snapshot_id, month, day);
create index if not exists ix_financial_record_asset_account_snapshot
    on financial_record_asset_account (snapshot_id, category_key);
create index if not exists ix_financial_record_debt_account_snapshot
    on financial_record_debt_account (snapshot_id, account);
create index if not exists ix_financial_record_income_event_snapshot
    on financial_record_income_event (snapshot_id, date);
create index if not exists ix_financial_record_important_date_snapshot
    on financial_record_important_date (snapshot_id, date);
