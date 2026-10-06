package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.ComponentPageDto;
import ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenLocks;
import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPage;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.VersionDeploymentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.System;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComponentPageGeneratorTest {

    @Mock
    private ConfluenceAdapter confluenceAdapter;
    @Mock
    private TemplateRenderer templateRenderer;
    @Mock
    private ComponentPageDtoFactory dtoFactory;
    @Mock
    private ComponentPageRepository componentPageRepository;
    @Mock
    private ComponentRepository componentRepository;
    @Mock
    private VersionDeploymentRepository versionDeploymentRepository;

    @Mock
    private DocgenLocks docgenLocks;
    @Mock
    private DocumentationTransactionRunner transactionRunner;

    private ComponentPageGenerator generator;
    private Component component;

    @BeforeEach
    void setUp() {
        component = new Component("my-component", new System("my-system"));
        generator = new ComponentPageGenerator(confluenceAdapter, templateRenderer, dtoFactory,
                componentPageRepository, componentRepository, versionDeploymentRepository, docgenLocks, transactionRunner);
        lenient().when(docgenLocks.runWithComponentLock(any(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        lenient().when(transactionRunner.run(any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        lenient().when(versionDeploymentRepository.existsForComponent(component.getId())).thenReturn(true);
        lenient().when(dtoFactory.create(component)).thenReturn(ComponentPageDto.builder()
                .componentName(component.getName()).flowMaxShow(50).flows(List.of()).build());
        lenient().when(templateRenderer.renderComponentPage(any())).thenReturn("content");
    }

    @Test
    void skipsPageGenerationWhenComponentHasNoRelevantDeployment() {
        when(versionDeploymentRepository.existsForComponent(component.getId())).thenReturn(false);

        assertThat(generator.generatePage("components-page", component)).isNull();

        verifyNoInteractions(confluenceAdapter, dtoFactory, templateRenderer);
        verify(componentRepository, never()).lockById(any());
        verify(componentPageRepository, never()).save(any());
    }

    @Test
    void generatesPageNormallyWhenRelevantDeploymentAppearsLater() {
        when(versionDeploymentRepository.existsForComponent(component.getId())).thenReturn(false, true);
        when(componentPageRepository.findByComponentId(component.getId())).thenReturn(Optional.empty());
        when(confluenceAdapter.findPageByTitle("components-page", "my-component (my-system)"))
                .thenReturn(Optional.empty());
        when(confluenceAdapter.addOrUpdatePageUnderAncestor(
                eq("components-page"), eq("my-component (my-system)"), any())).thenReturn("component-page");

        assertThat(generator.generatePage("components-page", component)).isNull();
        assertThat(generator.generatePage("components-page", component)).isEqualTo("component-page");

        verify(componentRepository, never()).lockById(any());
        verify(componentPageRepository).save(any(ComponentPage.class));
    }

    @Test
    void createsAndTracksPageAfterSuccessfulConfluenceOperation() {
        String pageTitle = "my-component (my-system)";
        when(componentPageRepository.findByComponentId(component.getId())).thenReturn(Optional.empty());
        when(confluenceAdapter.findPageByTitle("components-page", pageTitle)).thenReturn(Optional.empty());
        when(confluenceAdapter.addOrUpdatePageUnderAncestor(eq("components-page"), eq(pageTitle), any()))
                .thenReturn("component-page");

        generator.generatePage("components-page", component);

        verify(componentRepository, never()).lockById(any());
        verify(docgenLocks).runWithComponentLock(eq(component.getId()), any());
        ArgumentCaptor<ComponentPage> pageCaptor = ArgumentCaptor.forClass(ComponentPage.class);
        verify(componentPageRepository).save(pageCaptor.capture());
        assertThat(pageCaptor.getValue().getComponentId()).isEqualTo(component.getId());
        assertThat(pageCaptor.getValue().getPageId()).isEqualTo("component-page");
        assertThat(pageCaptor.getValue().getParentPageId()).isEqualTo("components-page");
        assertThat(pageCaptor.getValue().getCleanupAttemptedAt()).isNull();
    }

    @Test
    void reusesTrackedPageIdAndMovesPageWhenComponentChangesSystem() {
        ComponentPage trackedPage = ComponentPage.create(component.getId(), "component-page", "old-components-page");
        ReflectionTestUtils.setField(trackedPage, "cleanupAttemptedAt", ZonedDateTime.now().minusDays(1));
        when(componentPageRepository.findByComponentId(component.getId())).thenReturn(Optional.of(trackedPage));
        when(confluenceAdapter.updatePageById(eq("component-page"), eq("new-components-page"),
                eq("my-component (my-system)"), any(), eq(true))).thenReturn(true);

        generator.generatePage("new-components-page", component);

        verify(confluenceAdapter, never()).addOrUpdatePageUnderAncestor(any(), any(), any());
        verify(componentPageRepository).save(trackedPage);
        assertThat(trackedPage.getPageId()).isEqualTo("component-page");
        assertThat(trackedPage.getParentPageId()).isEqualTo("new-components-page");
        assertThat(trackedPage.getCleanupAttemptedAt()).isNull();
    }

    @Test
    void replacesTrackingOnlyAfterTrackedPageWasConfirmedMissingAndRecreated() {
        ComponentPage trackedPage = ComponentPage.create(component.getId(), "missing-page", "components-page");
        when(componentPageRepository.findByComponentId(component.getId())).thenReturn(Optional.of(trackedPage));
        when(confluenceAdapter.updatePageById(eq("missing-page"), any(), any(), any(), eq(false))).thenReturn(false);
        when(confluenceAdapter.addOrUpdatePageUnderAncestor(
                eq("components-page"), eq("my-component (my-system)"), any()))
                .thenReturn("replacement-page");

        generator.generatePage("components-page", component);

        assertThat(trackedPage.getPageId()).isEqualTo("replacement-page");
        verify(componentPageRepository).save(trackedPage);
    }

    @Test
    void keepsTrackingUntouchedWhenConfluenceOperationFails() {
        ComponentPage trackedPage = ComponentPage.create(component.getId(), "component-page", "components-page");
        when(componentPageRepository.findByComponentId(component.getId())).thenReturn(Optional.of(trackedPage));
        when(confluenceAdapter.updatePageById(any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new IllegalStateException("Confluence unavailable"));

        assertThatThrownBy(() -> generator.generatePage("components-page", component))
                .isInstanceOf(IllegalStateException.class);

        verify(componentPageRepository, never()).save(any());
        assertThat(trackedPage.getPageId()).isEqualTo("component-page");
        assertThat(trackedPage.getParentPageId()).isEqualTo("components-page");
    }

    @Test
    void movesTrackedPagesAfterTheirComponentsChangedSystem() {
        ComponentPage trackedPage = ComponentPage.create(component.getId(), "component-page", "old-components-page");
        when(componentPageRepository.findByParentPageId("old-components-page")).thenReturn(List.of(trackedPage));
        when(componentRepository.findById(component.getId())).thenReturn(Optional.of(component));
        when(componentPageRepository.findByComponentId(component.getId())).thenReturn(Optional.of(trackedPage));
        when(confluenceAdapter.updatePageById(eq("component-page"), eq("new-components-page"),
                eq("my-component (my-system)"), any(), eq(true))).thenReturn(true);

        generator.moveTrackedPages("old-components-page", "new-components-page");

        verify(confluenceAdapter).updatePageById(eq("component-page"), eq("new-components-page"),
                eq("my-component (my-system)"), any(), eq(true));
        assertThat(trackedPage.getParentPageId()).isEqualTo("new-components-page");
    }

    @Test
    void movesTrackedPagesUsingExplicitTargetSystemNameBeforeDatabaseMergeCommits() {
        ComponentPage trackedPage = ComponentPage.create(component.getId(), "component-page", "old-components-page");
        when(componentPageRepository.findByParentPageId("old-components-page")).thenReturn(List.of(trackedPage));
        when(componentRepository.findById(component.getId())).thenReturn(Optional.of(component));
        when(componentPageRepository.findByComponentId(component.getId())).thenReturn(Optional.of(trackedPage));
        when(confluenceAdapter.updatePageById(eq("component-page"), eq("new-components-page"),
                eq("my-component (target-system)"), any(), eq(true))).thenReturn(true);

        generator.moveTrackedPages("old-components-page", "new-components-page", "target-system");

        verify(confluenceAdapter).updatePageById(eq("component-page"), eq("new-components-page"),
                eq("my-component (target-system)"), any(), eq(true));
    }

    @Test
    void qualifiesPageTitleWithCurrentSystemName() {
        assertThat(ComponentPageGenerator.pageTitle(component)).isEqualTo("my-component (my-system)");

        component.updateSystem(new System("new-system"));

        assertThat(ComponentPageGenerator.pageTitle(component)).isEqualTo("my-component (new-system)");
    }
}
