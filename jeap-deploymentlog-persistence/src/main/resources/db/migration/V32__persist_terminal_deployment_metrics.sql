create table deployment_metric_event
(
    deployment_id   uuid         not null,
    deployment_type varchar(255) not null,
    system_name     varchar(255) not null,
    component_name  varchar(255) not null,
    environment_name varchar(255) not null,
    deployment_state varchar(255) not null,
    ended_at        timestamp,
    constraint deployment_metric_event_pkey primary key (deployment_id, deployment_type)
);

create index deployment_metric_event_labels
    on deployment_metric_event (system_name, component_name, environment_name, deployment_type, deployment_state);

insert into deployment_metric_event
    (deployment_id, deployment_type, system_name, component_name, environment_name, deployment_state, ended_at)
select deployment.id,
       deployment_type.type,
       system.name,
       component.name,
       environment.name,
       deployment.state,
       deployment.ended_at
from deployment
join deployment_types deployment_type on deployment_type.deployment_id = deployment.id
join component_version on component_version.id = deployment.component_version_id
join component on component.id = component_version.component_id
join system on system.id = component.system_id
join environment on environment.id = deployment.environment_id
where deployment.state in ('SUCCESS', 'FAILURE', 'CANCELLED');
