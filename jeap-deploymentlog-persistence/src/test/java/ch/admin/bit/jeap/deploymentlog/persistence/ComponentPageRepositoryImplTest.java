package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPage;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPageCleanupCandidate;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentType;
import ch.admin.bit.jeap.deploymentlog.domain.Environment;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.SystemRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
class ComponentPageRepositoryImplTest {

    @Autowired
    private ComponentPageRepository componentPageRepository;
    @Autowired
    private ComponentRepository componentRepository;
    @Autowired
    private SystemRepository systemRepository;
    @Autowired
    private DeploymentRepository deploymentRepository;
    @Autowired
    private EnvironmentRepository environmentRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ch.admin.bit.jeap.deploymentlog.domain.VersionDeploymentRepository versions;

    @BeforeEach
    void stages() {
        Environment start = new Environment("REF");
        start.setDevelopment(true);
        start.setStagingOrder(1);
        environmentRepository.save(start);
        Environment end = new Environment("PROD");
        end.setStagingOrder(3);
        environmentRepository.save(end);
    }

    @Test
    void persistsTrackingByTechnicalComponentIdAndUpdatesItsLocation() {
        Component component = component("component", "system");
        ComponentPage page = componentPageRepository.save(
                ComponentPage.create(component.getId(), "page-id", "components-old"));

        page.updateLocation("page-id", "components-new");
        componentPageRepository.save(page);
        entityManager.flush();
        entityManager.clear();

        assertThat(componentPageRepository.findByComponentId(component.getId()))
                .get()
                .satisfies(persisted -> {
                    assertThat(persisted.getPageId()).isEqualTo("page-id");
                    assertThat(persisted.getParentPageId()).isEqualTo("components-new");
                });
    }

    @Test
    void pageIdIsUniqueAcrossComponents() {
        Component first = component("first", "first-system");
        Component second = component("second", "second-system");
        componentPageRepository.save(ComponentPage.create(first.getId(), "same-page", "components-first"));
        componentPageRepository.save(ComponentPage.create(second.getId(), "same-page", "components-second"));

        assertThatThrownBy(entityManager::flush)
                .isInstanceOfAny(DataIntegrityViolationException.class, ConstraintViolationException.class);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void findsCleanupCandidatesWithNeverAttemptedFirstThenOldestAttemptsAndStableTieBreakers() {
        Component neverAttemptedBeta = component("never-attempted-beta", "beta-system");
        Component neverAttemptedAlpha = component("never-attempted-alpha", "alpha-system");
        Component oldestAttempt = component("oldest-attempt", "gamma-system");
        System tiedSystem = systemRepository.save(new System("tied-system"));
        Component tiedAttemptA = componentWithId(
                "00000000-0000-0000-0000-000000000001", "tied-a", tiedSystem);
        Component tiedAttemptB = componentWithId(
                "00000000-0000-0000-0000-000000000002", "tied-b", tiedSystem);
        Component codeOutsideStages = component("code-outside-stages", "epsilon-system");
        Component withRelevantDeployment = component("with-relevant-deployment", "delta-system");
        track(neverAttemptedBeta);
        track(neverAttemptedAlpha);
        track(oldestAttempt);
        track(tiedAttemptA);
        track(tiedAttemptB);
        track(codeOutsideStages);
        track(withRelevantDeployment);
        deploy(codeOutsideStages, Set.of(DeploymentType.CODE));
        createRelevantDeployment(withRelevantDeployment);
        entityManager.flush();
        assertThat(versions.existsForComponent(codeOutsideStages.getId())).isFalse();
        assertThat(versions.existsForComponent(withRelevantDeployment.getId())).isTrue();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        ZonedDateTime now = ZonedDateTime.now();
        assertThat(componentPageRepository.markCleanupAttemptedIfNoRelevantDeployment(
                oldestAttempt.getId(), now.minusHours(2))).isEqualTo(1);
        assertThat(componentPageRepository.markCleanupAttemptedIfNoRelevantDeployment(
                tiedAttemptA.getId(), now.minusHours(1))).isEqualTo(1);
        assertThat(componentPageRepository.markCleanupAttemptedIfNoRelevantDeployment(
                tiedAttemptB.getId(), now.minusHours(1))).isEqualTo(1);

        List<ComponentPageCleanupCandidate> neverAttempted = List.of(
                        candidate(neverAttemptedBeta), candidate(neverAttemptedAlpha), candidate(codeOutsideStages))
                .stream()
                .sorted(Comparator.comparing(ComponentPageCleanupCandidate::systemName)
                        .thenComparing(ComponentPageCleanupCandidate::componentId))
                .toList();
        List<ComponentPageCleanupCandidate> tiedAttempts = List.of(candidate(tiedAttemptA), candidate(tiedAttemptB))
                .stream()
                .sorted(Comparator.comparing(ComponentPageCleanupCandidate::systemName)
                        .thenComparing(ComponentPageCleanupCandidate::componentId))
                .toList();
        List<ComponentPageCleanupCandidate> expected = List.of(
                neverAttempted.get(0),
                neverAttempted.get(1),
                neverAttempted.get(2),
                candidate(oldestAttempt),
                tiedAttempts.get(0),
                tiedAttempts.get(1));

        assertThat(componentPageRepository.findCleanupCandidates(10)).containsExactlyElementsOf(expected);
        assertThat(componentPageRepository.findCleanupCandidates(3)).containsExactlyElementsOf(expected.subList(0, 3));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void atomicallyMarksCleanupAttemptOnlyWhileComponentHasNoRelevantDeployment() {
        Component withoutDeployment = component("without-deployment", "system-a");
        Component codeOutsideStages = component("code-outside-stages", "system-b");
        Component withRelevantDeployment = component("with-relevant-deployment", "system-c");
        track(withoutDeployment);
        track(codeOutsideStages);
        track(withRelevantDeployment);
        deploy(codeOutsideStages, Set.of(DeploymentType.CODE));
        createRelevantDeployment(withRelevantDeployment);
        entityManager.flush();
        assertThat(versions.existsForComponent(codeOutsideStages.getId())).isFalse();
        assertThat(versions.existsForComponent(withRelevantDeployment.getId())).isTrue();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        ZonedDateTime attemptedAt = ZonedDateTime.now().minusMinutes(5).truncatedTo(ChronoUnit.MICROS);
        assertThat(componentPageRepository.markCleanupAttemptedIfNoRelevantDeployment(
                withoutDeployment.getId(), attemptedAt)).isEqualTo(1);
        assertThat(componentPageRepository.markCleanupAttemptedIfNoRelevantDeployment(
                codeOutsideStages.getId(), attemptedAt)).isEqualTo(1);
        assertThat(componentPageRepository.markCleanupAttemptedIfNoRelevantDeployment(
                withRelevantDeployment.getId(), attemptedAt)).isZero();

        assertThat(componentPageRepository.findByComponentId(withoutDeployment.getId()))
                .get()
                .satisfies(page -> assertThat(page.getCleanupAttemptedAt().toInstant())
                        .isEqualTo(attemptedAt.toInstant()));
        assertThat(componentPageRepository.findByComponentId(codeOutsideStages.getId()))
                .get()
                .satisfies(page -> assertThat(page.getCleanupAttemptedAt().toInstant())
                        .isEqualTo(attemptedAt.toInstant()));
        assertThat(componentPageRepository.findByComponentId(withRelevantDeployment.getId()))
                .get()
                .extracting(ComponentPage::getCleanupAttemptedAt)
                .isNull();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void updatingTrackedLocationResetsCleanupAttemptTimestamp() {
        Component component = component("component", "system");
        componentPageRepository.save(ComponentPage.create(component.getId(), "page-id", "components-old"));
        entityManager.flush();
        TestTransaction.flagForCommit();
        TestTransaction.end();
        assertThat(componentPageRepository.markCleanupAttemptedIfNoRelevantDeployment(
                component.getId(), ZonedDateTime.now().minusMinutes(5))).isEqualTo(1);

        ComponentPage trackedPage = componentPageRepository.findByComponentId(component.getId()).orElseThrow();
        trackedPage.updateLocation("new-page-id", "components-new");
        componentPageRepository.save(trackedPage);

        assertThat(componentPageRepository.findByComponentId(component.getId()))
                .get()
                .satisfies(updated -> {
                    assertThat(updated.getPageId()).isEqualTo("new-page-id");
                    assertThat(updated.getParentPageId()).isEqualTo("components-new");
                    assertThat(updated.getCleanupAttemptedAt()).isNull();
                });
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void deletesTrackingWithoutRelevantDeploymentButKeepsTrackingWithRelevantDeployment() {
        Component withoutDeployment = component("without-deployment", "system-a");
        Component codeOutsideStages = component("code-outside-stages", "system-b");
        Component withRelevantDeployment = component("with-relevant-deployment", "system-c");
        track(withoutDeployment);
        track(codeOutsideStages);
        track(withRelevantDeployment);
        deploy(codeOutsideStages, Set.of(DeploymentType.CODE));
        createRelevantDeployment(withRelevantDeployment);
        entityManager.flush();
        assertThat(versions.existsForComponent(codeOutsideStages.getId())).isFalse();
        assertThat(versions.existsForComponent(withRelevantDeployment.getId())).isTrue();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        assertThat(componentPageRepository.deleteIfNoRelevantDeployment(withoutDeployment.getId())).isEqualTo(1);
        assertThat(componentPageRepository.deleteIfNoRelevantDeployment(codeOutsideStages.getId())).isEqualTo(1);
        assertThat(componentPageRepository.deleteIfNoRelevantDeployment(withRelevantDeployment.getId())).isZero();

        assertThat(componentPageRepository.findByComponentId(withoutDeployment.getId())).isEmpty();
        assertThat(componentPageRepository.findByComponentId(codeOutsideStages.getId())).isEmpty();
        assertThat(componentPageRepository.findByComponentId(withRelevantDeployment.getId())).isPresent();
    }

    private ComponentPageCleanupCandidate candidate(Component component) {
        return new ComponentPageCleanupCandidate(
                component.getId(), "page-" + component.getName(), component.getSystem().getName());
    }

    private void track(Component component) {
        componentPageRepository.save(ComponentPage.create(
                component.getId(), "page-" + component.getName(), "components"));
    }

    private Deployment deploy(Component component, Set<DeploymentType> types) {
        Environment environment = environmentRepository.save(new Environment("env-" + component.getName()));
        Deployment deployment = TestDataFactory.createDeployment(
                environment, component, ZonedDateTime.now(), TestDataFactory.createDeploymentTarget());
        deployment.getDeploymentTypes().addAll(types);
        return deploymentRepository.save(deployment);
    }

    private void createRelevantDeployment(Component component) {
        Deployment deployment = deploy(component, Set.of(DeploymentType.CODE));
        deployment.getEnvironment().setStagingOrder(2);
    }

    private Component component(String componentName, String systemName) {
        System system = systemRepository.save(new System(systemName));
        return componentRepository.save(new Component(componentName, system));
    }

    private Component componentWithId(String id, String componentName, System system) {
        Component component = new Component(componentName, system);
        ReflectionTestUtils.setField(component, "id", UUID.fromString(id));
        return componentRepository.save(component);
    }
}
