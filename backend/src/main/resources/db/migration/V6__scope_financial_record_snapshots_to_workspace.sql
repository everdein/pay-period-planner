alter table financial_record_snapshot
    add column workspace_id bigint
        references workspace (id) on delete restrict;

alter table financial_record_snapshot
    add constraint ck_financial_record_snapshot_workspace_required
        check (workspace_id is not null) not valid;

drop index uq_financial_record_snapshot_active;

create unique index uq_financial_record_snapshot_active_workspace
    on financial_record_snapshot (workspace_id)
    where active and workspace_id is not null;

create unique index uq_financial_record_snapshot_active_unowned
    on financial_record_snapshot (active)
    where active and workspace_id is null;

create index ix_financial_record_snapshot_workspace
    on financial_record_snapshot (workspace_id, id);
