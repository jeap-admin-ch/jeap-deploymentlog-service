package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.docgen.ConfluenceAdapter;
import ch.admin.bit.jeap.deploymentlog.docgen.DocumentationGenerator;
import ch.admin.bit.jeap.deploymentlog.docgen.JiraAdapter;
import ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenAsyncService;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentState;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentService;
import ch.admin.bit.jeap.deploymentlog.domain.SystemRepository;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.EnvironmentHistoryPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentListPageRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.hibernate.engine.spi.SessionImplementor;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DocumentationTransactionIT extends IntegrationTestBase {

    @MockitoSpyBean
    private ConfluenceAdapter confluence;
    @MockitoSpyBean
    private JiraAdapter jira;
    @Autowired
    private DocumentationGenerator generator;
    @Autowired
    private DocgenAsyncService asyncService;
    @Autowired
    private DeploymentPageRepository pageRepository;
    @Autowired
    private DeploymentService deploymentService;
    @Autowired
    private DataSource dataSource;

    @Autowired
    private SystemRepository systemRepository;
    @Autowired
    private EnvironmentRepository environmentRepository;
    @Autowired
    private EnvironmentHistoryPageRepository historyPageRepository;
    @Autowired
    private DeploymentListPageRepository listPageRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final AtomicReference<AssertionError> connectionFailure = new AtomicReference<>();

    @AfterEach
    void noAsyncConnectionAssertionFailed() {
        awaitUntilAsyncTasksCompleted();
        assertThat(connectionFailure.get()).isNull();
    }

    private final AtomicInteger remoteCalls = new AtomicInteger();
    private final AtomicInteger jiraCalls = new AtomicInteger();
    private final AtomicBoolean failProjectPage = new AtomicBoolean();

    @BeforeEach
    void checkRemoteCallBoundaries() {
        doAnswer(invocation -> {
            assertNoDatabaseTransaction();
            return invocation.callRealMethod();
        }).when(confluence).findPageByTitle(anyString(), anyString());
        doAnswer(invocation -> {
            assertNoDatabaseTransaction();
            if (failProjectPage.get() && "PROJ (Jira)".equals(invocation.getArgument(1))) {
                throw new IllegalStateException("Confluence temporarily unavailable");
            }
            Object result = invocation.callRealMethod();
            assertNoDatabaseTransaction();
            return result;
        }).when(confluence).addOrUpdatePageUnderAncestor(anyString(), anyString(), any());
        doAnswer(invocation -> {
            assertNoDatabaseTransaction();
            if (failProjectPage.get() && "PROJ (Jira)".equals(invocation.getArgument(2))) {
                throw new IllegalStateException("Confluence temporarily unavailable");
            }
            Object result = invocation.callRealMethod();
            assertNoDatabaseTransaction();
            return result;
        }).when(confluence).updatePageById(anyString(), anyString(), anyString(), any(), anyBoolean());
        doAnswer(invocation -> {
            assertNoDatabaseTransaction();
            jiraCalls.incrementAndGet();
            return invocation.callRealMethod();
        }).when(jira).updateIssuePageRemoteLink(anyString(), anyString());
    }

    @Test
    void rendersDeploymentComponentAndJiraPagesWithoutHoldingDatabaseTransaction() {
        // A successful CODE deployment also exercises the component flow and its lazy associations.
        putDeploymentState("transaction-boundaries", DeploymentState.SUCCESS,
                Map.of("detail", "render me"));
        awaitUntilAsyncTasksCompleted();
        var deployment = deploymentRepository.findByExternalId("transaction-boundaries").orElseThrow();
        assertThat(deploymentRepository.getPageGenerationRequestId(deployment.getId())).isEmpty();

        generator.generateAllPagesForSystem("TestSystem", null);
        generator.generateJiraLinksForSystem("TestSystem", ZonedDateTime.now().minusYears(30), ZonedDateTime.now());
        assertThat(confluenceAdapterMock.getModifiedPages()).contains("test (TestSystem)");
        assertThat(remoteCalls.get()).isGreaterThan(10);
    }

    @Test
    void failureAfterSavingDeploymentPageKeepsRequestPendingAndCanBeRepaired() {
        failProjectPage.set(true);
        var dto = createDeploymentDto();
        dto.setStartedAt(ZonedDateTime.now());
        dto.setProperties(Map.of("detail", "render me"));
        postDeployment(dto, "pending-after-remote-failure");
        awaitUntilAsyncTasksCompleted();
        var deployment = deploymentRepository.findByExternalId("pending-after-remote-failure").orElseThrow();
        assertThat(pageRepository.findDeploymentPageByDeploymentId(deployment.getId())).isPresent();
        assertThat(deploymentRepository.getPageGenerationRequestId(deployment.getId())).isPresent();

        failProjectPage.set(false);
        asyncService.triggerDocgenForDeployment(deployment.getId());
        awaitUntilAsyncTasksCompleted();
        assertThat(deploymentRepository.getPageGenerationRequestId(deployment.getId())).isEmpty();
        assertThat(jiraCalls.get()).isPositive();
        int previousJiraCalls = jiraCalls.get();
        generator.generateJiraLinksForSystem("TestSystem", ZonedDateTime.now().minusDays(1), ZonedDateTime.now());
        assertThat(jiraCalls.get()).isGreaterThan(previousJiraCalls);
        assertThat(confluenceAdapterMock.getModifiedPages()).contains("PROJ (Jira)", "PROJ-123");
    }

    @Test
    void tokenlessRepairRemainsEligibleAfterPartialFailure() {
        failProjectPage.set(true);
        var dto = createDeploymentDto();
        dto.setStartedAt(ZonedDateTime.now().minusMinutes(10));
        postDeployment(dto, "tokenless-repair");
        awaitUntilAsyncTasksCompleted();
        var id = deploymentRepository.findByExternalId("tokenless-repair").orElseThrow().getId();

        // Establish the state of a released legacy deployment: no page and no request token.
        var originalRequest = deploymentRepository.getPageGenerationRequestId(id).orElseThrow();
        deploymentService.requestPageGenerationIfAbsent(id);
        assertThat(deploymentRepository.getPageGenerationRequestId(id)).contains(originalRequest);
        deploymentService.completePageGenerationRequest(id, originalRequest);
        pageRepository.delete(pageRepository.findDeploymentPageByDeploymentId(id).orElseThrow());
        assertThat(deploymentRepository.getPageGenerationRequestId(id)).isEmpty();
        assertThat(deploymentRepository.isPageGenerationRepairRequired(id)).isTrue();

        asyncService.triggerRepairDocgenForDeployment(id);
        awaitUntilAsyncTasksCompleted();

        // The letter was committed, but the later Jira failure must not remove repair eligibility.
        assertThat(pageRepository.findDeploymentPageByDeploymentId(id)).isPresent();
        assertThat(deploymentRepository.getPageGenerationRequestId(id)).isPresent();
        assertThat(deploymentRepository.isPageGenerationRepairRequired(id)).isTrue();
        assertThat(deploymentService.getMissingDeploymentPages(100, 0, 60)).contains(id);

        failProjectPage.set(false);
        asyncService.triggerRepairDocgenForDeployment(id);
        awaitUntilAsyncTasksCompleted();

        assertThat(deploymentRepository.getPageGenerationRequestId(id)).isEmpty();
        assertThat(deploymentRepository.isPageGenerationRepairRequired(id)).isFalse();
        assertThat(deploymentService.getMissingDeploymentPages(100, 0, 60)).doesNotContain(id);

        deploymentService.suppressPageGeneration(pageRepository.findDeploymentPageByDeploymentId(id).orElseThrow());
        deploymentService.requestPageGenerationIfAbsent(id);
        assertThat(deploymentRepository.getPageGenerationRequestId(id)).isEmpty();
        assertThat(deploymentRepository.isPageGenerationRepairRequired(id)).isFalse();
    }

    @Test
    void mergeDeletesTrackedHistoryAndListPagesInShortTransaction() {
        postDeployment(createDeploymentDto("MergeSource", "source-component", "1.0.0"), "merge-source");
        postDeployment(createDeploymentDto("MergeTarget", "target-component", "1.0.0"), "merge-target");
        awaitUntilAsyncTasksCompleted();

        var systems = new TransactionTemplate(transactionManager).execute(status -> {
            var source = systemRepository.findByNameIgnoreCase("MergeSource").orElseThrow();
            var target = systemRepository.findByNameIgnoreCase("MergeTarget").orElseThrow();
            source.getComponents().size();
            target.getComponents().size();
            return List.of(source, target);
        });
        var source = systems.get(0);
        var target = systems.get(1);
        var environmentId = environmentRepository.findByName("DEV").orElseThrow().getId();
        assertThat(historyPageRepository.findEnvironmentHistoryPageBySystemIdAndEnvironmentId(
                source.getId(), environmentId)).isPresent();
        assertThat(listPageRepository.findDeploymentListPageBySystemIdAndEnvironmentIdAndYear(
                source.getId(), environmentId, 2007)).isPresent();

        doAnswer(invocation -> {
            assertNoDatabaseTransaction();
            return invocation.callRealMethod();
        }).when(confluence).deletePageAndChildPages(anyString());
        doAnswer(invocation -> {
            assertNoDatabaseTransaction();
            return invocation.callRealMethod();
        }).when(confluence).movePage(anyString(), anyString());

        generator.mergeSystems(target, source);

        assertThat(historyPageRepository.findEnvironmentHistoryPageBySystemIdAndEnvironmentId(
                source.getId(), environmentId)).isEmpty();
        assertThat(listPageRepository.findDeploymentListPageBySystemIdAndEnvironmentIdAndYear(
                source.getId(), environmentId, 2007)).isEmpty();
        assertThat(historyPageRepository.findEnvironmentHistoryPageBySystemIdAndEnvironmentId(
                target.getId(), environmentId)).isPresent();
        assertThat(listPageRepository.findDeploymentListPageBySystemIdAndEnvironmentIdAndYear(
                target.getId(), environmentId, 2007)).isPresent();
    }

    private void assertNoDatabaseTransaction() {
        try {
            assertConnectionReleased();
        } catch (AssertionError failure) {
            connectionFailure.compareAndSet(null, failure);
            throw failure;
        }
    }

    private void assertConnectionReleased() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
        TransactionSynchronizationManager.getResourceMap().values().stream()
                .filter(EntityManagerHolder.class::isInstance)
                .map(EntityManagerHolder.class::cast)
                .forEach(holder -> assertThat(holder.getEntityManager().unwrap(SessionImplementor.class)
                        .getJdbcCoordinator().getLogicalConnection().isPhysicallyConnected())
                        .as("Thread-bound EntityManager must not retain a JDBC connection during HTTP calls")
                        .isFalse());
        remoteCalls.incrementAndGet();
    }
}
