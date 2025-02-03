create table artifact_version
(
    id                      uuid    not null
        constraint artifact_version_pkey primary key,
    coordinates             varchar not null,
    build_job_link          varchar not null,
    created_at              timestamp  not null
);
