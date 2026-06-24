create table financial_snapshot (
    id bigint generated always as identity primary key,
    active boolean not null default true,
    version bigint not null default 1,
    pay_period_start date not null,
    pay_period_end date not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index uq_financial_snapshot_active
    on financial_snapshot (active)
    where active;

create table monthly_withdrawal (
    id bigint generated always as identity primary key,
    snapshot_id bigint not null references financial_snapshot (id) on delete cascade,
    bill text not null,
    due_day integer not null check (due_day between 1 and 31),
    amount numeric(14, 2) not null check (amount >= 0),
    account text not null,
    paid boolean not null default false
);

create table annual_withdrawal (
    id bigint generated always as identity primary key,
    snapshot_id bigint not null references financial_snapshot (id) on delete cascade,
    bill text not null,
    month integer not null check (month between 1 and 12),
    day integer not null check (day between 1 and 31),
    amount numeric(14, 2) not null check (amount >= 0),
    account text not null,
    paid boolean not null default false
);

create table asset_account (
    id bigint generated always as identity primary key,
    snapshot_id bigint not null references financial_snapshot (id) on delete cascade,
    category_key text not null,
    category_label text not null,
    account text not null,
    company text not null,
    amount numeric(14, 2) not null check (amount >= 0)
);

create table debt_account (
    id bigint generated always as identity primary key,
    snapshot_id bigint not null references financial_snapshot (id) on delete cascade,
    account text not null,
    company text not null,
    amount numeric(14, 2) not null check (amount >= 0)
);

create table income_summary_item (
    id bigint generated always as identity primary key,
    snapshot_id bigint not null references financial_snapshot (id) on delete cascade,
    category text not null,
    interval text not null,
    amount numeric(14, 2) not null check (amount >= 0)
);

create table income_event (
    id bigint generated always as identity primary key,
    snapshot_id bigint not null references financial_snapshot (id) on delete cascade,
    date date not null,
    label text not null,
    type text not null,
    check_number integer check (check_number is null or check_number > 0)
);

create table important_date (
    id bigint generated always as identity primary key,
    snapshot_id bigint not null references financial_snapshot (id) on delete cascade,
    date date not null,
    event text not null,
    type text not null
);

create index ix_monthly_withdrawal_snapshot on monthly_withdrawal (snapshot_id, due_day);
create index ix_annual_withdrawal_snapshot on annual_withdrawal (snapshot_id, month, day);
create index ix_asset_account_snapshot on asset_account (snapshot_id, category_key);
create index ix_debt_account_snapshot on debt_account (snapshot_id, account);
create index ix_income_event_snapshot on income_event (snapshot_id, date);
create index ix_important_date_snapshot on important_date (snapshot_id, date);
