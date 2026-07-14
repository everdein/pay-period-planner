create table application_user (
    id bigint generated always as identity primary key,
    email varchar(320) not null,
    normalized_email varchar(320) generated always as (lower(btrim(email))) stored,
    password_hash text not null,
    display_name text not null,
    status text not null default 'active',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_application_user_email
        check (email = btrim(email) and email <> ''),
    constraint ck_application_user_password_hash
        check (password_hash = btrim(password_hash) and password_hash <> ''),
    constraint ck_application_user_display_name
        check (display_name = btrim(display_name) and display_name <> ''),
    constraint ck_application_user_status
        check (status in ('active', 'disabled'))
);

create unique index uq_application_user_normalized_email
    on application_user (normalized_email);

create table workspace (
    id bigint generated always as identity primary key,
    name text not null,
    created_by_user_id bigint not null
        references application_user (id) on delete restrict,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_workspace_name
        check (name = btrim(name) and name <> '')
);

create index ix_workspace_created_by_user
    on workspace (created_by_user_id);

create table workspace_membership (
    workspace_id bigint not null
        references workspace (id) on delete cascade,
    user_id bigint not null
        references application_user (id) on delete cascade,
    role text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (workspace_id, user_id),
    constraint ck_workspace_membership_role
        check (role in ('owner', 'admin', 'member'))
);

create unique index uq_workspace_membership_owner
    on workspace_membership (workspace_id)
    where role = 'owner';

create index ix_workspace_membership_user
    on workspace_membership (user_id, workspace_id);

create table application_session (
    id uuid primary key,
    user_id bigint not null
        references application_user (id) on delete cascade,
    token_hash text not null,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    last_seen_at timestamptz not null default now(),
    revoked_at timestamptz,
    constraint ck_application_session_token_hash
        check (
            token_hash = btrim(token_hash)
            and char_length(token_hash) between 32 and 128
        ),
    constraint ck_application_session_expiry
        check (expires_at > created_at),
    constraint ck_application_session_last_seen
        check (last_seen_at >= created_at),
    constraint ck_application_session_revoked
        check (revoked_at is null or revoked_at >= created_at)
);

create unique index uq_application_session_token_hash
    on application_session (token_hash);

create index ix_application_session_user_active
    on application_session (user_id, expires_at)
    where revoked_at is null;

create index ix_application_session_active_expiry
    on application_session (expires_at)
    where revoked_at is null;
