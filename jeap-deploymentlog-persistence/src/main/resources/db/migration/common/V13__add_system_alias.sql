create table system_alias
(
    id        uuid not null
        constraint system_alias_pkey primary key,
    name      text
        constraint system_alias_name_uk unique,
    system_id uuid references system
);

create index system_alias_name ON system_alias (name);
create index system_alias_system_id ON system_alias (system_id);
