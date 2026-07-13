create unique index if not exists uq_financial_record_monthly_bill_snapshot_app_record
    on financial_record_monthly_bill (snapshot_id, app_record_id);

create unique index if not exists uq_financial_record_annual_withdrawal_snapshot_app_record
    on financial_record_annual_withdrawal (snapshot_id, app_record_id);

create unique index if not exists uq_financial_record_asset_account_snapshot_app_record
    on financial_record_asset_account (snapshot_id, app_record_id);

create unique index if not exists uq_financial_record_debt_account_snapshot_app_record
    on financial_record_debt_account (snapshot_id, app_record_id);

create unique index if not exists uq_financial_record_income_summary_item_snapshot_app_record
    on financial_record_income_summary_item (snapshot_id, app_record_id);

create unique index if not exists uq_financial_record_income_event_snapshot_app_record
    on financial_record_income_event (snapshot_id, app_record_id);

create unique index if not exists uq_financial_record_important_date_snapshot_app_record
    on financial_record_important_date (snapshot_id, app_record_id);
