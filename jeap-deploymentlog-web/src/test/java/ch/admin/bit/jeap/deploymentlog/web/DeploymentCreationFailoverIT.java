package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenAsyncService;
import ch.admin.bit.jeap.deploymentlog.domain.Deployment;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentStagingService;
import ch.admin.bit.jeap.deploymentlog.web.api.DeploymentCheckService;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCheckResult;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.DeploymentCheckResultDto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

class DeploymentCreationFailoverIT extends IntegrationTestBase {
    @MockitoSpyBean
    private DeploymentStagingService stagingService;
    @MockitoBean
    private DocgenAsyncService docgen;
    @MockitoBean
    private DeploymentCheckService checkService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void predeploymentRetriesAfterRollbackWithoutRepeatingJiraCheck() throws Exception {
        List<UUID> attemptedVersions = new ArrayList<>();
        List<Object> sessions = new ArrayList<>();
        doAnswer(invocation -> {
            Deployment deployment = invocation.getArgument(0);
            sessions.add(entityManager.getDelegate());
            entityManager.flush();
            attemptedVersions.add(deployment.getComponentVersion().getId());
            if (attemptedVersions.size() == 1) {
                throw failover();
            }
            return invocation.callRealMethod();
        }).when(stagingService).prepare(any(), any());
        when(checkService.checkIssuesReadyForDeploy(any())).thenReturn(DeploymentCheckResultDto.builder()
                .result(DeploymentCheckResult.OK).build());
        String body = objectMapper.writeValueAsString(createDeploymentDto());

        mockMvc.perform(put("/api/deployment/failover-create")
                        .param("readyForDeployCheck", "true")
                        .with(httpBasic("write", "secret"))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        assertThat(attemptedVersions).hasSize(2);
        assertThat(sessions.get(0)).isNotSameAs(sessions.get(1));
        assertThat(versionCount(attemptedVersions.getFirst())).isZero();
        assertThat(versionCount(attemptedVersions.getLast())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from deployment where external_id = ?",
                Integer.class, "failover-create")).isEqualTo(1);
        verify(checkService).checkIssuesReadyForDeploy(any());

        // A repeated pipeline request must not create another version or run the Jira check again.
        mockMvc.perform(put("/api/deployment/failover-create")
                        .param("readyForDeployCheck", "true")
                        .with(httpBasic("write", "secret"))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verifyNoMoreInteractions(checkService);
        verify(stagingService, times(2)).prepare(any(), any());
    }

    @Test
    void persistentFailoverStopsAfterTwoAttemptsAndRollsBackBoth() {
        List<UUID> attemptedVersions = new ArrayList<>();
        doAnswer(invocation -> {
            Deployment deployment = invocation.getArgument(0);
            entityManager.flush();
            attemptedVersions.add(deployment.getComponentVersion().getId());
            throw failover();
        }).when(stagingService).prepare(any(), any());
        var dto = createDeploymentDto();

        assertThatThrownBy(() -> postDeployment(dto, "persistent-failover"))
                .hasRootCauseInstanceOf(FailoverSuccessSQLException.class);

        assertThat(attemptedVersions).hasSize(2);
        attemptedVersions.forEach(id -> assertThat(versionCount(id)).isZero());
        assertThat(deploymentRepository.findByExternalId("persistent-failover")).isEmpty();
        verifyNoInteractions(docgen);
    }

    @Test
    void unrelatedDatabaseFailureIsNotRetried() {
        doThrow(new DataAccessResourceFailureException("Other database failure",
                new SQLException("Unknown transaction outcome", "08007")))
                .when(stagingService).prepare(any(), any());
        var dto = createDeploymentDto();

        assertThatThrownBy(() -> postDeployment(dto, "non-retryable-create"))
                .hasRootCauseInstanceOf(SQLException.class);

        verify(stagingService).prepare(any(), any());
        assertThat(deploymentRepository.findByExternalId("non-retryable-create")).isEmpty();
        verifyNoInteractions(docgen);
    }

    private int versionCount(UUID id) {
        return jdbc.queryForObject("select count(*) from component_version where id = ?", Integer.class, id);
    }

    private static DataAccessResourceFailureException failover() {
        return new DataAccessResourceFailureException("Connection replaced", new FailoverSuccessSQLException());
    }

    // Match the AWS wrapper's exception contract without requiring an Aurora database.
    private static class FailoverSuccessSQLException extends SQLException {
        FailoverSuccessSQLException() {
            super("Connection replaced", "08S02");
        }
    }
}
