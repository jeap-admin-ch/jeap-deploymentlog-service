create table reference
(
    id uuid              primary key,
    reference_identifier varchar(255) not null,
    type                 varchar(255) not null,
    uri                  varchar(255) not null
);

create table deployment_references
(
    deployment_id        uuid         references deployment (id) primary key,
    reference_identifier varchar(255) not null
);

