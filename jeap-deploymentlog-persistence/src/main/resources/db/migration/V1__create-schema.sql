create table shedlock
(
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

create table deployment_list_page
(
    id              uuid    not null
        constraint deployment_list_page_pkey primary key,
    environment_id  uuid,
    last_updated_at timestamp,
    page_id         varchar(255),
    system_id       uuid,
    year            integer not null
);

create table deployment_page
(
    id                         uuid      not null
        constraint deployment_page_pkey primary key,
    deployment_id              uuid,
    last_updated_at            timestamp,
    deployment_state_timestamp timestamp not null,
    page_id                    varchar(255)
);

create table environment
(
    id            uuid    not null
        constraint environment_pkey primary key,
    name          varchar(255)
        constraint environment_name_uk unique,
    productive    boolean not null,
    staging_order integer not null
);

create index environment_name ON environment (name);

create table environment_history_page
(
    id              uuid not null
        constraint environment_history_page_pkey primary key,
    environment_id  uuid,
    last_updated_at timestamp,
    page_id         varchar(255),
    system_id       uuid
);

create table system
(
    id   uuid not null
        constraint system_pkey primary key,
    name varchar(255)
        constraint system_name_uk unique
);

create index system_name ON system (name);

create table component
(
    id        uuid    not null
        constraint component_pkey primary key,
    active    boolean not null,
    name      varchar(255),
    system_id uuid references system
);

create index component_name ON component (name);
create index component_system_id ON component (system_id);

create table component_version
(
    id                      uuid    not null
        constraint component_version_pkey primary key,
    commit_ref              varchar(255),
    committed_at            timestamp,
    artifact_repository_url varchar(255),
    coordinates             varchar(255),
    type                    varchar(255),
    major_version           decimal,
    minor_version           decimal,
    patch_version           decimal,
    build_version           decimal,
    postfix                 varchar(255),
    is_snapshot             boolean not null,
    published_version       boolean not null,
    tagged_at               timestamp,
    version_control_url     varchar(255),
    version_name            varchar(255),
    component_id            uuid references component
);

create index component_version_component_id ON component_version (component_id);

create table changelog
(
    id                  uuid
        constraint changelog_pkey primary key,
    comment             varchar(255),
    compared_to_version varchar(255)
);

create table changelog_jira_issue
(
    changelog uuid references changelog,
    issue_key varchar(32)
);

create index changelog_jira_issue_fkey on changelog_jira_issue (changelog);

create table deployment
(
    id                   uuid      not null
        constraint deployment_pkey primary key,
    ended_at             timestamp,
    external_id          varchar(255)
        constraint deployment_external_id_uk unique,
    started_at           timestamp,
    started_by           varchar(255),
    state                varchar(255),
    state_message        varchar(1024),
    component_version_id uuid references component_version,
    environment_id       uuid references environment,
    changelog_id         uuid references changelog,
    last_modified        timestamp not null
);

create index deployment_component_version_id ON deployment (component_version_id);
create index deployment_environment_id ON deployment (environment_id);

create table deployment_links
(
    deployment_id uuid not null references deployment,
    label         varchar(255),
    url           varchar(255)
);

create table environment_component_version_state
(
    id                   uuid not null
        constraint environment_component_version_state_pkey primary key,
    component_id         uuid references component,
    component_version_id uuid references component_version,
    environment_id       uuid references environment,
    deployment_id        uuid references deployment
);

create index environment_component_version_state_component_id ON environment_component_version_state (component_id);
create index environment_component_version_state_component_version_id ON environment_component_version_state (component_version_id);
create index environment_component_version_state_environment_id ON environment_component_version_state (environment_id);
create index environment_component_version_state_deployment_id ON environment_component_version_state (deployment_id);

create table system_page
(
    id              uuid not null
        constraint system_page_pkey primary key,
    last_updated_at timestamp,
    system_id       uuid,
    system_page_id  varchar(255)
);

