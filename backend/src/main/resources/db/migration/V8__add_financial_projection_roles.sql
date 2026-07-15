create table if not exists financial_record_projection_role (
    snapshot_id bigint not null references financial_record_snapshot (id) on delete cascade,
    role_key text not null check (
        role_key in (
            'rent-bill',
            'rent-reserve-asset-account',
            'primary-paycheck-income-summary-item'
        )
    ),
    app_record_id bigint not null,
    primary key (snapshot_id, role_key)
);

insert into financial_record_projection_role (snapshot_id, role_key, app_record_id)
select snapshot.id, 'rent-bill', candidate.app_record_id
from financial_record_snapshot snapshot
cross join lateral (
    select bill.app_record_id
    from financial_record_monthly_bill bill
    where bill.snapshot_id = snapshot.id
      and lower(trim(bill.bill)) = 'rent'
    order by bill.id
    limit 1
) candidate
on conflict do nothing;

insert into financial_record_projection_role (snapshot_id, role_key, app_record_id)
select snapshot.id, 'rent-reserve-asset-account', candidate.app_record_id
from financial_record_snapshot snapshot
cross join lateral (
    select account.app_record_id
    from financial_record_asset_account account
    where account.snapshot_id = snapshot.id
      and lower(trim(account.account)) = 'rent reserve'
    order by account.id
    limit 1
) candidate
on conflict do nothing;

insert into financial_record_projection_role (snapshot_id, role_key, app_record_id)
select snapshot.id, 'primary-paycheck-income-summary-item', candidate.app_record_id
from financial_record_snapshot snapshot
cross join lateral (
    select income.app_record_id
    from financial_record_income_summary_item income
    where income.snapshot_id = snapshot.id
      and lower(trim(income.category)) = 'net income'
      and lower(trim(income.interval)) = 'bi-weekly'
    order by income.id
    limit 1
) candidate
on conflict do nothing;
