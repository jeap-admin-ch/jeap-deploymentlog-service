package ch.admin.bit.jeap.deploymentlog.web.metrics;

import ch.admin.bit.jeap.deploymentlog.docgen.service.SchedulingService;
import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentVersion;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentSequence;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentService;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentTarget;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentType;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentUnit;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentUnitType;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.Flow;
import ch.admin.bit.jeap.deploymentlog.domain.FlowRepository;
import ch.admin.bit.jeap.deploymentlog.domain.FlowType;
import ch.admin.bit.jeap.deploymentlog.domain.SystemRepository;
import ch.admin.bit.jeap.deploymentlog.web.DeploymentLogApplication;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SpringBootTest(classes = DeploymentLogApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:metrics-integration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "management.endpoints.web.exposure.include=health,prometheus",
        "management.endpoints.web.base-path=/actuator",
        "management.prometheus.metrics.export.enabled=true",
        "jeap.deploymentlog.metrics.deployment-baseline-refresh-interval=PT24H",
        "jeap.deploymentlog.metrics.flow-open-refresh-interval=PT24H"
})
@AutoConfigureMockMvc(addFilters = false)
abstract class MetricsIntegrationTestBase {
    protected static final ZonedDateTime START = ZonedDateTime.parse("2026-09-14T10:00:00+02:00");

    @Autowired protected DeploymentService deploymentService;
    @Autowired protected DeploymentRepository deploymentRepository;
    @Autowired protected FlowRepository flowRepository;
    @Autowired protected EnvironmentRepository environmentRepository;
    @Autowired protected SystemRepository systemRepository;
    @Autowired protected ComponentRepository componentRepository;
    @Autowired protected PlatformTransactionManager transactionManager;
    @Autowired protected DeploymentFlowMetrics metrics;
    @Autowired protected PrometheusMeterRegistry registry;
    @Autowired protected MockMvc mockMvc;
    @MockitoBean private SchedulingService schedulingService;

    protected Fixture createFixture(FlowType type) {
        Fixture fixture = transaction().execute(status -> {
            String systemName = "metrics-" + UUID.randomUUID();
            var system = systemRepository.save(new ch.admin.bit.jeap.deploymentlog.domain.System(systemName));
            Component component = componentRepository.save(new Component("service", system));
            Environment environment = environmentRepository.findByName("DEV").orElseThrow();
            ComponentVersion version = ComponentVersion.builder()
                    .component(component).versionName("1.0.0").commitRef("commit")
                    .committedAt(START.minusHours(1)).versionControlUrl("https://git.example/repo")
                    .deploymentUnit(DeploymentUnit.builder().type(DeploymentUnitType.DOCKER_IMAGE)
                            .coordinates("service:1.0.0").artifactRepositoryUrl("https://registry.example").build())
                    .build();
            Deployment deployment = deploymentRepository.save(Deployment.builder()
                    .externalId(UUID.randomUUID().toString()).componentVersion(version).environment(environment)
                    .startedAt(START).startedBy("test").sequence(DeploymentSequence.NEW)
                    .target(new DeploymentTarget("test", "https://target.example", "test"))
                    .deploymentTypes(Set.of(DeploymentType.CODE)).build());
            flowRepository.save(Flow.start(type, deployment, environment));
            return new Fixture(deployment.getExternalId(), systemName, type);
        });
        metrics.refreshOpenFlowGauges();
        return fixture;
    }

    protected TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    protected void update(Fixture fixture, DeploymentState state) {
        try {
            deploymentService.updateState(fixture.externalId(), state, "test", START.plusSeconds(90), Map.of());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected double openFlows(Fixture fixture) {
        return registry.get(DeploymentFlowMetrics.FLOW_OPEN).tag("system", fixture.system()).gauge().value();
    }

    protected record Fixture(String externalId, String system, FlowType type) { }

}
