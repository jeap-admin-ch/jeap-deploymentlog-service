create table deployment_properties
(
    deployment_id  uuid,
    property_name  varchar not null,
    property_value varchar not null,

    constraint pk_deployment_properties
        primary key (deployment_id, property_name),

    constraint fk_deployment
        foreign key (deployment_id)
            references deployment (id)
);

create index deployment_properties_deployment_fk_index on deployment_properties (deployment_id);
