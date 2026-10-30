-- Core schema. Enumerations are stored as varchar so new values need no DDL;
-- the allowed values are enforced in the application layer.

create table app_role (
    id          bigint generated always as identity primary key,
    code        varchar(64)  not null unique,
    name        varchar(255) not null,
    system_role boolean      not null default false
);

create table role_permission (
    role_id    bigint      not null references app_role (id) on delete cascade,
    permission varchar(64) not null,
    primary key (role_id, permission)
);

create table app_user (
    id              bigint generated always as identity primary key,
    username        varchar(64)  not null unique,
    full_name       varchar(255) not null,
    email           varchar(255),
    department      varchar(255),
    password_hash   varchar(255),
    oneid_subject   varchar(128) unique,
    preferred_lang  varchar(8)   not null default 'uz',
    active          boolean      not null default true,
    failed_logins   integer      not null default 0,
    locked_until    timestamptz,
    last_login_at   timestamptz,
    created_at      timestamptz  not null default now(),
    version         bigint       not null default 0
);

create table user_role (
    user_id bigint not null references app_user (id) on delete cascade,
    role_id bigint not null references app_role (id) on delete cascade,
    primary key (user_id, role_id)
);

-- Reference books (countries, partner organizations, agreement types, meeting formats)
-- share one table: same shape, same admin screens, one set of endpoints.
create table reference_item (
    id      bigint generated always as identity primary key,
    kind    varchar(32)  not null,
    code    varchar(64)  not null,
    name_uz varchar(255) not null,
    name_ru varchar(255) not null,
    name_en varchar(255) not null,
    active  boolean      not null default true,
    unique (kind, code)
);

create table meeting (
    id              bigint generated always as identity primary key,
    title           varchar(500)  not null,
    format_id       bigint        references reference_item (id),
    country_id      bigint        references reference_item (id),
    organization_id bigint        references reference_item (id),
    held_on         date          not null,
    location        varchar(255),
    status          varchar(32)   not null,
    summary         varchar(4000),
    responsible_id  bigint        references app_user (id),
    created_at      timestamptz   not null default now(),
    created_by      varchar(64)   not null,
    updated_at      timestamptz,
    updated_by      varchar(64),
    version         bigint        not null default 0
);
create index meeting_held_on_idx on meeting (held_on);
create index meeting_country_idx on meeting (country_id);

create table agreement (
    id                    bigint generated always as identity primary key,
    registration_number   varchar(64)   not null unique,
    title                 varchar(500)  not null,
    type_id               bigint        references reference_item (id),
    country_id            bigint        references reference_item (id),
    organization_id       bigint        references reference_item (id),
    meeting_id            bigint        references meeting (id),
    signed_on             date,
    valid_until           date,
    status                varchar(32)   not null,
    description           varchar(4000),
    responsible_id        bigint        references app_user (id),
    external_ref          varchar(128),
    external_verified_at  timestamptz,
    created_at            timestamptz   not null default now(),
    created_by            varchar(64)   not null,
    updated_at            timestamptz,
    updated_by            varchar(64),
    version               bigint        not null default 0
);
create index agreement_status_idx on agreement (status);
create index agreement_country_idx on agreement (country_id);
create index agreement_valid_until_idx on agreement (valid_until);

create table agreement_status_history (
    id           bigint generated always as identity primary key,
    agreement_id bigint       not null references agreement (id) on delete cascade,
    from_status  varchar(32),
    to_status    varchar(32)  not null,
    comment      varchar(1000),
    changed_by   varchar(64)  not null,
    changed_at   timestamptz  not null default now()
);
create index agreement_status_history_agreement_idx on agreement_status_history (agreement_id);

-- Obligations that implement an agreement: who does what by when.
create table implementation_item (
    id             bigint generated always as identity primary key,
    agreement_id   bigint        not null references agreement (id) on delete cascade,
    description    varchar(2000) not null,
    responsible_id bigint        not null references app_user (id),
    due_date       date          not null,
    status         varchar(32)   not null,
    progress_note  varchar(2000),
    completed_on   date,
    created_at     timestamptz   not null default now(),
    updated_at     timestamptz,
    version        bigint        not null default 0
);
create index implementation_item_due_idx on implementation_item (due_date) where status <> 'DONE';
create index implementation_item_responsible_idx on implementation_item (responsible_id);

create table notification (
    id           bigint generated always as identity primary key,
    user_id      bigint       not null references app_user (id) on delete cascade,
    type         varchar(32)  not null,
    entity_type  varchar(32)  not null,
    entity_id    bigint       not null,
    params       jsonb        not null default '{}'::jsonb,
    dedupe_key   varchar(64)  not null,
    created_at   timestamptz  not null default now(),
    read_at      timestamptz,
    unique (user_id, type, entity_type, entity_id, dedupe_key)
);
create index notification_user_unread_idx on notification (user_id) where read_at is null;

create table audit_log (
    id          bigint generated always as identity primary key,
    occurred_at timestamptz  not null default now(),
    username    varchar(64)  not null,
    action      varchar(32)  not null,
    entity_type varchar(32)  not null,
    entity_id   bigint,
    details     jsonb        not null default '{}'::jsonb,
    client_ip   varchar(64)
);
create index audit_log_entity_idx on audit_log (entity_type, entity_id);
create index audit_log_occurred_idx on audit_log (occurred_at);

-- The audit trail is append-only, enforced by the database itself so that
-- not even the application account can rewrite history.
create function audit_log_immutable() returns trigger as $$
begin
    raise exception 'audit_log is append-only';
end;
$$ language plpgsql;

create trigger audit_log_no_update
    before update or delete on audit_log
    for each row execute function audit_log_immutable();
