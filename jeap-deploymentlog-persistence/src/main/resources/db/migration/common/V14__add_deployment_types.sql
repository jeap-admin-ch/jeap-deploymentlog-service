create table deployment_types
(
    deployment_id uuid not null references deployment (id),
    type          text not null
);

ALTER TABLE deployment_types
    ADD CONSTRAINT pk_deployment_types PRIMARY KEY (deployment_id, type);
