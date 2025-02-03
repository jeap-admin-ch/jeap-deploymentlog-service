package ch.admin.bit.jeap.deploymentlog.docgen.service;

import ch.admin.bit.jeap.deploymentlog.docgen.DocumentationGenerator;
import ch.admin.bit.jeap.deploymentlog.docgen.model.*;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeneratorServiceTest {

    @InjectMocks
    private GeneratorService generatorService;

    @Mock
    private EnvironmentRepository environmentRepositoryMock;

    @Mock
    private EnvironmentComponentVersionStateRepository environmentComponentVersionStateRepositoryMock;

    @Mock
    private DeploymentRepository deploymentRepositoryMock;

    @Mock
    private ArtifactVersionRepository artifactVersionRepository;

    @Mock
    private ReferenceRepository referenceRepository;

    @Mock
    private DeploymentPageRepository deploymentPageRepository;

    @Test
    void createSystemPageDto_allSame() {
        String versionDEV = "0.0.1";
        String versionREF = "0.0.1";
        String versionPROD = "0.0.1";

        List<ComponentEnvDto> comEnvDtoList = createDtosForVersions(versionDEV, versionREF, versionPROD);

        assertEquals(Color.NONE, comEnvDtoList.get(0).getColor());
        assertEquals(Color.ALL_IDENTICAL, comEnvDtoList.get(1).getColor());
        assertEquals(Color.ALL_IDENTICAL, comEnvDtoList.get(2).getColor());
    }

    @Test
    void createSystemPageDto_nextStageHigher() {
        String versionDEV = "0.0.1";
        String versionREF = "0.0.3";
        String versionPROD = "0.0.2";

        List<ComponentEnvDto> comEnvDtoList = createDtosForVersions(versionDEV, versionREF, versionPROD);

        assertEquals(Color.NONE, comEnvDtoList.get(0).getColor());
        assertEquals(Color.HIGHER_THAN_NEXT_STAGE, comEnvDtoList.get(1).getColor());
        assertEquals(Color.NONE, comEnvDtoList.get(2).getColor());
    }

    @Test
    void createSystemPageDto_missingOnNextStage() {
        String versionDEV = "0.0.1-SNAPSHOT";
        String versionREF = "0.0.1";
        String versionPROD = null;

        List<ComponentEnvDto> comEnvDtoList = createDtosForVersions(versionDEV, versionREF, versionPROD);

        assertEquals(Color.NONE, comEnvDtoList.get(0).getColor());
        assertEquals(Color.MISSES_NEXT_STAGE, comEnvDtoList.get(1).getColor());
        assertEquals(Color.NONE, comEnvDtoList.get(2).getColor());
    }

    @Test
    void createSystemPageDto_onlyOnFirstStage() {
        String versionDEV = "0.0.1-SNAPSHOT";
        String versionREF = null;
        String versionPROD = null;

        List<ComponentEnvDto> comEnvDtoList = createDtosForVersions(versionDEV, versionREF, versionPROD);

        assertEquals(Color.NONE, comEnvDtoList.get(0).getColor());
        assertEquals(Color.NONE, comEnvDtoList.get(1).getColor());
        assertEquals(Color.NONE, comEnvDtoList.get(2).getColor());
    }

    @Test
    void createSystemPageDto_snapshotVersion_thenAscending() {
        String versionDEV = "0.0.1-SNAPSHOT";
        String versionREF = "0.0.1";
        String versionPROD = "0.0.2";

        List<ComponentEnvDto> comEnvDtoList = createDtosForVersions(versionDEV, versionREF, versionPROD);

        assertEquals(Color.NONE, comEnvDtoList.get(0).getColor());
        assertEquals(Color.NONE, comEnvDtoList.get(1).getColor());
        assertEquals(Color.NONE, comEnvDtoList.get(2).getColor());
    }

    @Test
    void createSystemPageDto_snapshotVersion_thenSame() {
        String versionDEV = "0.0.1-SNAPSHOT";
        String versionREF = "0.0.1";
        String versionPROD = "0.0.1";

        List<ComponentEnvDto> comEnvDtoList = createDtosForVersions(versionDEV, versionREF, versionPROD);

        assertEquals(Color.NONE, comEnvDtoList.get(0).getColor());
        assertEquals(Color.ALL_IDENTICAL, comEnvDtoList.get(1).getColor());
        assertEquals(Color.ALL_IDENTICAL, comEnvDtoList.get(2).getColor());
    }

    private List<ComponentEnvDto> createDtosForVersions(String versionDEV, String versionREF, String versionPROD) {
        String systemName = "Some System Name";
        System systemA = new System(systemName);
        Environment environmentDEV = new Environment("DEV");
        Environment environmentREF = new Environment("REF");
        Environment environmentPROD = new Environment("PROD");

        List<Environment> environmentListSystemA = List.of(environmentDEV, environmentREF, environmentPROD);

        Component componentA = new Component("Component A", systemA);
        List<Component> componentListSystemA = List.of(componentA);

        Optional<EnvironmentComponentVersionState> optStateDEVComponentA = getEnvironmentComponentVersionState(versionDEV, environmentDEV, componentA);
        Optional<EnvironmentComponentVersionState> optStateREFComponentA = getEnvironmentComponentVersionState(versionREF, environmentREF, componentA);
        Optional<EnvironmentComponentVersionState> optStatePRODComponentA = getEnvironmentComponentVersionState(versionPROD, environmentPROD, componentA);

        doReturn(environmentListSystemA).when(environmentRepositoryMock).findEnvironmentsForSystem(systemA);
        doReturn(componentListSystemA).when(environmentComponentVersionStateRepositoryMock).findComponentsBySystem(systemA);
        doReturn(optStateDEVComponentA).when(environmentComponentVersionStateRepositoryMock).findByEnvironmentAndComponent(environmentDEV, componentA);
        doReturn(optStateREFComponentA).when(environmentComponentVersionStateRepositoryMock).findByEnvironmentAndComponent(environmentREF, componentA);
        doReturn(optStatePRODComponentA).when(environmentComponentVersionStateRepositoryMock).findByEnvironmentAndComponent(environmentPROD, componentA);

        SystemPageDto systemPageDto = generatorService.createSystemPageDto(systemA);
        assertEquals(systemName, systemPageDto.getName());

        ComponentDto componentDto = systemPageDto.getComponentList().get(0);
        return componentDto.getComponentEnvDtoList();
    }

    private Optional<EnvironmentComponentVersionState> getEnvironmentComponentVersionState(String version, Environment environment, Component component) {
        if (version == null) {
            return Optional.empty();
        }

        ComponentVersion componentAVersionDEV = ComponentVersion.builder()
                .versionName(version)
                .versionControlUrl("https://linktobitbucket.ch")
                .commitRef("commit")
                .component(component)
                .committedAt(ZonedDateTime.now())
                .deploymentUnit(DeploymentUnit.builder()
                        .coordinates("xy")
                        .artifactRepositoryUrl("")
                        .type(DeploymentUnitType.MAVEN_JAR)
                        .build())
                .build();

        Deployment deployment = Deployment.builder()
                .componentVersion(componentAVersionDEV)
                .environment(environment)
                .startedAt(ZonedDateTime.now())
                .startedBy("TestDeploy")
                .externalId("1")
                .sequence(DeploymentSequence.NEW)
                .build();
        EnvironmentComponentVersionState environmentComponentVersionState
                = EnvironmentComponentVersionState.fromDeployment(deployment);
        deployment.success(ZonedDateTime.now(), null);
        environmentComponentVersionState.updateVersion(componentAVersionDEV, deployment);
        return Optional.of(environmentComponentVersionState);
    }

    @Test
    void durationTest() {

        int maxShow = 5;

        System systemA = new System("System A");
        Environment environmentDEV = new Environment("DEV");

        //Case 1: Check Duration 2 Minutes 2 Seconds
        Deployment deployment1 = createDeployment(systemA, environmentDEV);
        ZonedDateTime deploy1StartedAt = deployment1.getStartedAt();
        ZonedDateTime deploy1EndedAt = deploy1StartedAt.plusMinutes(2).plusSeconds(2);
        deployment1.success(deploy1EndedAt, null);

        // Case 2: Duration 11Hours 59 Minutes 59 Seconds
        Deployment deployment2 = createDeployment(systemA, environmentDEV);
        ZonedDateTime deploy2StartedAt = deployment2.getStartedAt();
        ZonedDateTime deploy2EndedAt = deploy2StartedAt.plusHours(11).plusMinutes(59).plusSeconds(59);
        deployment2.success(deploy2EndedAt, null);

        List<Deployment> deploymentList = List.of(deployment1, deployment2);

        doReturn(deploymentList).when(deploymentRepositoryMock).findDeploymentForSystemAndEnvLimited(systemA, environmentDEV, maxShow);


        List<DeploymentDto> deploymentDtoList = generatorService.getDeploymentsForSystemAndEnv(systemA, environmentDEV, 5);

        assertEquals(2, deploymentDtoList.size());
        assertEquals("00:02:02", deploymentDtoList.get(0).getDuration());
        assertEquals("11:59:59", deploymentDtoList.get(1).getDuration());

    }

    private Deployment createDeployment(System system, Environment environment) {
        Component component = new Component("Microserivce A", system);

        ComponentVersion componentAVersionDEV = ComponentVersion.builder()
                .versionName("0.1.1")
                .versionControlUrl("https://linktobitbucket.ch")
                .commitRef("commit")
                .component(component)
                .committedAt(ZonedDateTime.now())
                .deploymentUnit(DeploymentUnit.builder()
                        .coordinates("xy")
                        .artifactRepositoryUrl("")
                        .type(DeploymentUnitType.MAVEN_JAR)
                        .build())
                .build();

        return Deployment.builder()
                .componentVersion(componentAVersionDEV)
                .environment(environment)
                .startedAt(ZonedDateTime.now())
                .startedBy("TestDeploy")
                .externalId("1")
                .links(Set.of(Link.builder()
                        .label("label1")
                        .url("https://localhost")
                        .build(), Link.builder()
                        .label("label2")
                        .url("https://localhost")
                        .build()))
                .sequence(DeploymentSequence.NEW)
                .build();
    }

    @Test
    void createDeploymentLetterPageDto_doesNotThrow() {
        System systemA = new System("System A");
        Environment environmentDEV = new Environment("DEV");

        assertDoesNotThrow(() ->
                generatorService.createDeploymentLetterPageDto(createDeployment(systemA, environmentDEV)));
    }

    @Test
    void createDeploymentLetterPageDto_noBuildJobLinks() {
        final DeploymentLetterPageDto deploymentLetterPageDto = generatorService.createDeploymentLetterPageDto(createDeployment(new System("System A"), new Environment("DEV")));
        assertThat(deploymentLetterPageDto.getBuildJobLinks()).isEmpty();
    }

    @Test
    void createDeploymentLetterPageDto_withBuildJobLinks() {
        List<ArtifactVersion> artifactsVersions = List.of(ArtifactVersion.builder()
                .id(UUID.randomUUID())
                .coordinates("test")
                .buildJobLink("https://mock.ch")
                .build());
        when(artifactVersionRepository.findAllByCoordinates(anyString())).thenReturn(artifactsVersions);

        final DeploymentLetterPageDto deploymentLetterPageDto = generatorService.createDeploymentLetterPageDto(createDeployment(new System("System A"), new Environment("DEV")));
        assertThat(deploymentLetterPageDto.getBuildJobLinks()).hasSize(1);
        assertThat(deploymentLetterPageDto.getBuildJobLinks().iterator().next()).isEqualTo("https://mock.ch");
    }

    @Test
    void createDeploymentLetterPageDto_withBuildJobLinkFromReference() {
        String refId = "https://build@1.2.3";

        String link = "https://build/job";
        Reference reference = Reference.builder()
                .id(UUID.randomUUID())
                .referenceIdentifier(refId)
                .type(ReferenceType.BUILD_JOB_LINK_BY_GIT_URL_AND_VERSION)
                .uri(link)
                .build();
        when(referenceRepository.findAllByReferenceIdentifier(refId)).thenReturn(List.of(reference));

        Deployment deployment = createDeployment(new System("System A"), new Environment("DEV"));
        deployment.getReferenceIdentifiers().add(refId);

        final DeploymentLetterPageDto deploymentLetterPageDto = generatorService.createDeploymentLetterPageDto(
                deployment);
        assertThat(deploymentLetterPageDto.getBuildJobLinks()).hasSize(1);
        assertThat(deploymentLetterPageDto.getBuildJobLinks().iterator().next()).isEqualTo(link);
    }

    @Test
    void createDeploymentLetterPageDto_sourceBuild_noBuildJobLinks() {
        final Deployment deployment = createDeployment(new System("System A"), new Environment("DEV"));
        ReflectionTestUtils.setField(deployment.getComponentVersion().getDeploymentUnit(), "type", DeploymentUnitType.SOURCE_BUILD);
        final DeploymentLetterPageDto deploymentLetterPageDto = generatorService.createDeploymentLetterPageDto(deployment);
        assertThat(deploymentLetterPageDto.getBuildJobLinks()).isEmpty();
    }

    @Test
    void createDeploymentLetterPageDto_withTarget_targetInDto() {
        final Deployment deployment = createDeployment(new System("System A"), new Environment("DEV"));
        ReflectionTestUtils.setField(deployment, "target", new DeploymentTarget("CF", "http://localhost/cf", "details"));
        final DeploymentLetterPageDto deploymentLetterPageDto = generatorService.createDeploymentLetterPageDto(deployment);
        assertThat(deploymentLetterPageDto.getTargetType()).isNotBlank();
        assertThat(deploymentLetterPageDto.getTargetUrl()).isNotBlank();
        assertThat(deploymentLetterPageDto.getTargetDetails()).isNotBlank();
    }

    @Test
    void createDeploymentLetterPageDto_withoutTarget_targetNotInDto() {
        final Deployment deployment = createDeployment(new System("System A"), new Environment("DEV"));
        final DeploymentLetterPageDto deploymentLetterPageDto = generatorService.createDeploymentLetterPageDto(deployment);
        assertThat(deploymentLetterPageDto.getTargetType()).isNull();
        assertThat(deploymentLetterPageDto.getTargetUrl()).isNull();
        assertThat(deploymentLetterPageDto.getTargetDetails()).isNull();
    }

    @Test
    void getDeploymentsForEnv_withUndeployment_correctLetterLink() {
        System systemA = new System("System A");
        Environment environmentDEV = new Environment("DEV");
        Deployment deployment = createUndeployment(systemA, environmentDEV);
        doReturn(List.of(deployment))
                .when(deploymentRepositoryMock)
                .findDeploymentForEnvLimited(eq(environmentDEV), any(), anyInt());

        List<DeploymentDto> deploymentsForEnv = generatorService.getDeploymentsForEnv(environmentDEV, ZonedDateTime.now(), 5);
        assertThat(deploymentsForEnv.size()).isEqualTo(1);
        assertThat(deploymentsForEnv.get(0).getDeploymentLetterLink()).endsWith(DocumentationGenerator.UNDEPLOY_PAGE_SUFFIX);
    }

    @Test
    void getDeploymentsForSystemAndEnv_withUndeployment_correctLetterLink() {
        System systemA = new System("System A");
        Environment environmentDEV = new Environment("DEV");
        Deployment deployment = createUndeployment(systemA, environmentDEV);
        doReturn(List.of(deployment))
                .when(deploymentRepositoryMock)
                .findDeploymentForSystemAndEnvLimited(eq(systemA), eq(environmentDEV), anyInt());

        List<DeploymentDto> deploymentsForEnv = generatorService.getDeploymentsForSystemAndEnv(systemA, environmentDEV, 5);
        assertThat(deploymentsForEnv.size()).isEqualTo(1);
        assertThat(deploymentsForEnv.get(0).getDeploymentLetterLink()).endsWith(DocumentationGenerator.UNDEPLOY_PAGE_SUFFIX);
    }

    @Test
    void persistDeploymentPage_pageAlreadyExists_pageUpdated(){
        UUID deploymentId = UUID.randomUUID();
        String pageId = UUID.randomUUID().toString();
        ZonedDateTime deploymentStateTimestamp = ZonedDateTime.now();
        DeploymentPage deploymentPage = DeploymentPage.builder()
                .deploymentId(deploymentId)
                .lastUpdatedAt(ZonedDateTime.now().minusDays(10))
                .deploymentStateTimestamp(ZonedDateTime.now().minusDays(10))
                .pageId(UUID.randomUUID().toString()).build();

        when(deploymentPageRepository.findDeploymentPageByDeploymentId(deploymentId))
                .thenReturn(Optional.of(deploymentPage));

        generatorService.persistDeploymentPage(deploymentId, pageId, deploymentStateTimestamp);

        assertThat(deploymentPage.getPageId()).isEqualTo(pageId);
        assertThat(deploymentPage.getDeploymentStateTimestamp()).isEqualTo(deploymentStateTimestamp);
    }

    private Deployment createUndeployment(System system, Environment environment) {
        Component component = new Component("Microserivce A", system);

        ComponentVersion componentAVersionDEV = ComponentVersion.builder()
                .versionName("0.1.1")
                .versionControlUrl("https://linktobitbucket.ch")
                .commitRef("commit")
                .component(component)
                .committedAt(ZonedDateTime.now())
                .deploymentUnit(DeploymentUnit.builder()
                        .coordinates("xy")
                        .artifactRepositoryUrl("")
                        .type(DeploymentUnitType.MAVEN_JAR)
                        .build())
                .build();

        return Deployment.builder()
                .componentVersion(componentAVersionDEV)
                .environment(environment)
                .startedAt(ZonedDateTime.now())
                .startedBy("TestDeploy")
                .externalId("1")
                .links(Set.of(Link.builder()
                        .label("label1")
                        .url("https://localhost")
                        .build(), Link.builder()
                        .label("label2")
                        .url("https://localhost")
                        .build()))
                .sequence(DeploymentSequence.UNDEPLOYED)
                .build();
    }

}
