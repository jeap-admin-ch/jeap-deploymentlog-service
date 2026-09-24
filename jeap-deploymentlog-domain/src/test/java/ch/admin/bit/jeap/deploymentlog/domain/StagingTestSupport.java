package ch.admin.bit.jeap.deploymentlog.domain;

import org.junit.jupiter.api.BeforeEach;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.mockito.Mockito.*;

abstract class StagingTestSupport {
    final VersionDeploymentRepository repository = mock(VersionDeploymentRepository.class);
    final FlowStageResolver resolver = mock(FlowStageResolver.class);
    final FlowStageProperties properties = new FlowStageProperties();
    final DeploymentStagingService service = new DeploymentStagingService(repository, properties, resolver);
    final Environment ref = new Environment("REF");
    final Environment abn = new Environment("ABN");
    final Environment prod = new Environment("PROD");
    final Component component = new Component("service", new System("SYSTEM"));
    final ZonedDateTime now = ZonedDateTime.parse("2026-09-25T10:00:00Z");

    @BeforeEach
    void stages() {
        ref.setStagingOrder(1);
        abn.setStagingOrder(2);
        prod.setStagingOrder(3);
        when(resolver.relevantEnvironments()).thenReturn(List.of(ref, abn, prod));
        when(resolver.resolveDefaultFinalDeploymentEnvironment()).thenReturn(prod);
        when(resolver.resolveEffectiveFinalDeploymentEnvironment(any())).thenAnswer(invocation -> {
            java.util.Collection<String> names = invocation.getArgument(0);
            return names.contains("PROD") ? prod : names.contains("ABN") ? abn : null;
        });
    }

    Deployment deployment(Environment environment) {
        return Deployment.builder().externalId(UUID.randomUUID().toString()).startedAt(now).startedBy("test")
                .environment(environment).sequence(DeploymentSequence.NEW).deploymentTypes(Set.of(DeploymentType.CODE))
                .componentVersion(ComponentVersion.builder().component(component).versionName("1.0")
                        .versionControlUrl("https://git").commitRef("abc").committedAt(now.minusDays(1))
                        .deploymentUnit(DeploymentUnit.builder().type(DeploymentUnitType.DOCKER_IMAGE)
                                .coordinates("image:1.0").artifactRepositoryUrl("https://registry").build()).build()).build();
    }

    StagingHistoryEntry entry(String version, Environment stage, DeploymentState state, int hoursAgo) {
        return new StagingHistoryEntry(UUID.randomUUID(), version, stage.getName(), now.minusHours(hoursAgo),
                now.minusHours(hoursAgo).plusMinutes(1), state, false);
    }

    void history(StagingHistoryEntry... entries) {
        when(repository.history(component.getId())).thenReturn(List.of(entries));
    }
}
