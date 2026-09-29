package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VersionDeploymentRepositoryImpl implements VersionDeploymentRepository {
    private static final String COMPONENT_ID_PARAMETER = "componentId";
    private static final String STAGES_PARAMETER = "stages";

    private final EntityManager entityManager;
    private final JpaComponentRepository componentRepository;
    private final JdbcTemplate jdbc;
    private final StagingEnvironmentResolver stageResolver;
    private final StagingProperties stagingProperties;

    @Override
    public List<List<Deployment>> findLatestVersions(UUID componentId, int limit) {
        if (!stagingProperties.isEnabled() || limit <= 0) {
            return List.of();
        }
        List<String> versions = entityManager.createQuery("""
                select d.componentVersion.versionName from Deployment d
                where d.componentVersion.component.id = :componentId and d.environment.name in :stages
                and ch.admin.bit.jeap.deploymentlog.domain.DeploymentType.CODE member of d.deploymentTypes
                and d.sequence <> ch.admin.bit.jeap.deploymentlog.domain.DeploymentSequence.UNDEPLOYED
                group by d.componentVersion.versionName
                order by max(d.startedAt) desc, d.componentVersion.versionName
                """, String.class).setParameter(COMPONENT_ID_PARAMETER, componentId)
                .setParameter(STAGES_PARAMETER, relevantStageNames()).setMaxResults(limit).getResultList();
        if (versions.isEmpty()) {
            return List.of();
        }
        List<Deployment> deployments = entityManager.createQuery("""
                select d from Deployment d
                join fetch d.componentVersion cv
                join fetch d.environment
                where cv.component.id = :componentId and cv.versionName in :versions
                and d.environment.name in :stages
                and ch.admin.bit.jeap.deploymentlog.domain.DeploymentType.CODE member of d.deploymentTypes
                and d.sequence <> ch.admin.bit.jeap.deploymentlog.domain.DeploymentSequence.UNDEPLOYED
                order by d.startedAt, d.id
                """, Deployment.class).setParameter(COMPONENT_ID_PARAMETER, componentId)
                .setParameter(STAGES_PARAMETER, relevantStageNames())
                .setParameter("versions", versions).getResultList();
        return versions.stream().map(version -> deployments.stream()
                .filter(d -> d.getComponentVersion().getVersionName().equals(version)).toList()).toList();
    }

    @Override
    public boolean existsForComponent(UUID componentId) {
        if (!stagingProperties.isEnabled()) {
            return false;
        }
        return !entityManager.createQuery("""
                select d.id from Deployment d where d.componentVersion.component.id = :componentId
                and d.environment.name in :stages
                and ch.admin.bit.jeap.deploymentlog.domain.DeploymentType.CODE member of d.deploymentTypes
                and d.sequence <> ch.admin.bit.jeap.deploymentlog.domain.DeploymentSequence.UNDEPLOYED
                """, UUID.class).setParameter(COMPONENT_ID_PARAMETER, componentId)
                .setParameter(STAGES_PARAMETER, relevantStageNames()).setMaxResults(1).getResultList().isEmpty();
    }

    @Override
    @Transactional
    public void lockComponent(UUID componentId) {
        componentRepository.lockById(componentId).orElseThrow();
    }

    private List<String> relevantStageNames() {
        return stageResolver.relevantEnvironments().stream().map(Environment::getName).toList();
    }

    @Override
    public List<StagingHistoryEntry> history(UUID componentId) {
        entityManager.flush();
        return jdbc.query("""
                select d.id, cv.version_name, e.name, d.started_at, d.ended_at, d.state,
                       d.sequence = 'UNDEPLOYED'
                from deployment d
                join component_version cv on cv.id = d.component_version_id
                join environment e on e.id = d.environment_id
                where cv.component_id = ?
                  and (d.sequence = 'UNDEPLOYED' or exists (
                      select 1 from deployment_types dt where dt.deployment_id = d.id and dt.type = 'CODE'))
                """, (rs, row) -> new StagingHistoryEntry(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getTimestamp(4).toInstant().atZone(ZoneOffset.UTC),
                rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant().atZone(ZoneOffset.UTC),
                DeploymentState.valueOf(rs.getString(6)), rs.getBoolean(7)), componentId);
    }

    @Override
    public List<StagingMetricValue> metrics(String startEnvironment, String endEnvironment) {
        entityManager.flush();
        return jdbc.query("""
                with deployments as (
                    select cv.component_id, cv.version_name, e.name as environment_name,
                           d.id, d.state, d.started_at, d.staging_type,
                           exists (select 1 from deployment_final_environments f
                               where f.deployment_id = d.id and f.environment_name = ?) as auto_staging
                    from deployment d
                    join component_version cv on cv.id = d.component_version_id
                    join environment e on e.id = d.environment_id
                    where d.sequence <> 'UNDEPLOYED'
                      and exists (select 1 from deployment_types dt
                                  where dt.deployment_id = d.id and dt.type = 'CODE')
                ), versions as (
                    select component_id, version_name,
                        min(case when environment_name = ? and state = 'SUCCESS' then started_at end) as start_at,
                        min(case when environment_name = ? and state = 'SUCCESS' then started_at end) as end_at
                    from deployments
                    group by component_id, version_name
                ), latest_start as (
                    select component_id, auto_staging,
                           row_number() over (partition by component_id order by started_at desc, id desc) as deployment_rank
                    from deployments
                    where environment_name = ? and (staging_type is null or staging_type <> 'ROLLBACK')
                )
                select s.name, c.name, v.start_at, v.end_at, latest.auto_staging
                from versions v join component c on c.id = v.component_id join system s on s.id = c.system_id
                left join latest_start latest on latest.component_id = v.component_id and latest.deployment_rank = 1
                """, rs -> {
            java.util.Map<String, double[]> values = new java.util.LinkedHashMap<>();
            java.util.Map<String, String[]> labels = new java.util.HashMap<>();
            java.util.Map<String, Boolean> autoStaging = new java.util.HashMap<>();
            while (rs.next()) {
                String system = rs.getString(1);
                String component = rs.getString(2);
                String key = system + "\u0000" + component;
                labels.put(key, new String[]{system, component});
                double[] counts = values.computeIfAbsent(key, ignored -> new double[4]);
                Timestamp start = rs.getTimestamp(3);
                Timestamp end = rs.getTimestamp(4);
                if (start != null) counts[0]++;
                if (end != null) counts[1]++;
                autoStaging.put(key, rs.getObject(5, Boolean.class));
                if (start != null && end != null && !end.before(start)) {
                    counts[2]++;
                    counts[3] += java.time.Duration.between(start.toInstant(), end.toInstant()).toMillis() / 1000.0;
                }
            }
            return values.entrySet().stream().map(entry -> {
                double[] v = entry.getValue();
                String[] label = labels.get(entry.getKey());
                return new StagingMetricValue(label[0], label[1], (long) v[0], (long) v[1],
                        autoStaging.get(entry.getKey()), (long) v[2], v[3]);
            }).toList();
        }, endEnvironment, startEnvironment, endEnvironment, startEnvironment);
    }
}
