create table financial_record_audit_event (
    id bigint generated always as identity primary key,
    snapshot_id bigint not null
        references financial_record_snapshot (id) on delete cascade,
    app_event_id bigint not null,
    occurred_at timestamptz not null,
    action text not null,
    resource_type text not null,
    resource_id bigint,
    version_before bigint not null,
    version_after bigint not null,
    summary text not null,
    projection_pay_period_start date not null,
    projection_pay_period_end date not null,
    projection_monthly_bill_count integer not null,
    projection_annual_withdrawal_count integer not null,
    projection_asset_account_count integer not null,
    projection_debt_account_count integer not null,
    projection_income_summary_item_count integer not null,
    projection_income_event_count integer not null,
    projection_important_date_count integer not null,
    projection_total_monthly_expenses numeric(20, 2) not null,
    projection_total_annual_withdrawals numeric(20, 2) not null,
    projection_total_tracked_assets numeric(20, 2) not null,
    projection_total_debt numeric(20, 2) not null,
    projection_net_worth numeric(20, 2) not null,
    constraint ck_financial_record_audit_event_id
        check (app_event_id > 0),
    constraint ck_financial_record_audit_event_versions
        check (version_before >= 1 and version_after > version_before),
    constraint ck_financial_record_audit_event_text
        check (
            action = btrim(action)
            and action <> ''
            and resource_type = btrim(resource_type)
            and resource_type <> ''
            and summary = btrim(summary)
            and summary <> ''
        ),
    constraint ck_financial_record_audit_event_projection_counts
        check (
            projection_monthly_bill_count >= 0
            and projection_annual_withdrawal_count >= 0
            and projection_asset_account_count >= 0
            and projection_debt_account_count >= 0
            and projection_income_summary_item_count >= 0
            and projection_income_event_count >= 0
            and projection_important_date_count >= 0
        ),
    constraint ck_financial_record_audit_event_projection_period
        check (projection_pay_period_end >= projection_pay_period_start)
);

create unique index uq_financial_record_audit_event_snapshot_app_event
    on financial_record_audit_event (snapshot_id, app_event_id);

create index ix_financial_record_audit_event_snapshot_occurred
    on financial_record_audit_event (snapshot_id, occurred_at desc, id desc);

create table financial_snapshot_workspace_migration (
    id uuid primary key,
    source_kind text not null,
    source_fingerprint varchar(64) not null,
    source_version bigint not null,
    source_document_id bigint
        references financial_snapshot_document (id) on delete set null,
    destination_user_id bigint not null,
    workspace_id bigint not null,
    migrated_snapshot_id bigint not null unique
        references financial_record_snapshot (id) on delete restrict,
    monthly_bill_count bigint not null,
    annual_withdrawal_count bigint not null,
    asset_account_count bigint not null,
    debt_account_count bigint not null,
    income_summary_item_count bigint not null,
    income_event_count bigint not null,
    important_date_count bigint not null,
    audit_event_count bigint not null,
    status text not null default 'applied',
    applied_at timestamptz not null default now(),
    rolled_back_at timestamptz,
    constraint fk_financial_snapshot_workspace_migration_membership
        foreign key (workspace_id, destination_user_id)
        references workspace_membership (workspace_id, user_id) on delete restrict,
    constraint ck_financial_snapshot_workspace_migration_source_kind
        check (source_kind in ('json_file', 'jsonb_document')),
    constraint ck_financial_snapshot_workspace_migration_fingerprint
        check (source_fingerprint ~ '^[0-9a-f]{64}$'),
    constraint ck_financial_snapshot_workspace_migration_version
        check (source_version >= 1),
    constraint ck_financial_snapshot_workspace_migration_counts
        check (
            monthly_bill_count >= 0
            and annual_withdrawal_count >= 0
            and asset_account_count >= 0
            and debt_account_count >= 0
            and income_summary_item_count >= 0
            and income_event_count >= 0
            and important_date_count >= 0
            and audit_event_count >= 0
        ),
    constraint ck_financial_snapshot_workspace_migration_status
        check (status in ('applied', 'rolled_back')),
    constraint ck_financial_snapshot_workspace_migration_rollback
        check (
            (status = 'applied' and rolled_back_at is null)
            or (status = 'rolled_back' and rolled_back_at is not null)
        )
);

create unique index uq_financial_snapshot_workspace_migration_applied_workspace
    on financial_snapshot_workspace_migration (workspace_id)
    where status = 'applied';

create index ix_financial_snapshot_workspace_migration_destination
    on financial_snapshot_workspace_migration (destination_user_id, workspace_id, applied_at desc);
