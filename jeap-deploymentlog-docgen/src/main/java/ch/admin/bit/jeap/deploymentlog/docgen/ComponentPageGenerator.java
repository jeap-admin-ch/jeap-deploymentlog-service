package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.model.ComponentPageDto;
import ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenLocks;
import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPage;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.VersionDeploymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@org.springframework.stereotype.Component
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ComponentPageGenerator {

    private final ConfluenceAdapter confluenceAdapter;
    private final TemplateRenderer templateRenderer;
    private final ComponentPageDtoFactory dtoFactory;
    private final ComponentPageRepository componentPageRepository;
    private final ComponentRepository componentRepository;
    private final VersionDeploymentRepository versionDeploymentRepository;
    private final DocgenLocks docgenLocks;
    private final DocumentationTransactionRunner transactionRunner;

    public String generatePage(String componentsParentPageId, Component component) {
        return generatePage(componentsParentPageId, component, component.getSystem().getName());
    }

    private String generatePage(String componentsParentPageId, Component component, String systemName) {
        return docgenLocks.runWithComponentLock(component.getId(),
                () -> generatePageLocked(componentsParentPageId, component, systemName));
    }

    private String generatePageLocked(String componentsParentPageId, Component component, String systemName) {
        boolean hasVersionDeployment = transactionRunner.run(
                () -> versionDeploymentRepository.existsForComponent(component.getId()));
        if (!hasVersionDeployment) {
            log.info("Skipping component page generation for system '{}' and component '{}': no relevant version deployment exists",
                    systemName, component.getName());
            return null;
        }

        String pageTitle = pageTitle(component, systemName);
        Optional<ComponentPage> trackedPage = transactionRunner.run(() -> componentPageRepository.findByComponentId(component.getId()));
        String pageId = trackedPage.map(ComponentPage::getPageId)
                .or(() -> confluenceAdapter.findPageByTitle(componentsParentPageId, pageTitle))
                .orElse(null);
        boolean moveRequired = trackedPage.map(ComponentPage::getParentPageId)
                .map(parent -> !Objects.equals(parent, componentsParentPageId))
                .orElse(false);
        Supplier<String> content = () -> templateRenderer.renderComponentPage(dtoFactory.create(component));

        try {
            if (pageId == null || !confluenceAdapter.updatePageById(
                    pageId, componentsParentPageId, pageTitle, content, moveRequired)) {
                pageId = confluenceAdapter.addOrUpdatePageUnderAncestor(
                        componentsParentPageId, pageTitle, content);
            }

            ComponentPage componentPage = trackedPage.orElse(null);
            if (componentPage == null) {
                componentPage = ComponentPage.create(component.getId(), pageId, componentsParentPageId);
            }
            componentPage.updateLocation(pageId, componentsParentPageId);
            componentPageRepository.save(componentPage);
            return pageId;
        } catch (RuntimeException ex) {
            log.warn("Failed to generate component page for system '{}' and component '{}'",
                    systemName, component.getName(), ex);
            throw ex;
        }
    }

    static String pageTitle(Component component) {
        return pageTitle(component, component.getSystem().getName());
    }

    private static String pageTitle(Component component, String systemName) {
        return ComponentPageDto.pageTitle(component.getName(), systemName);
    }

    public void generatePages(String componentsParentPageId, Collection<Component> components) {
        generatePages(componentsParentPageId, components, null);
    }

    void generatePages(String componentsParentPageId, Collection<Component> components, String targetSystemName) {
        components.stream()
                .sorted(Comparator.comparing(Component::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Component::getId))
                .forEach(component -> generatePage(componentsParentPageId, component,
                        targetSystemName == null ? component.getSystem().getName() : targetSystemName));
    }

    public void moveTrackedPages(String oldComponentsParentPageId, String newComponentsParentPageId) {
        moveTrackedPages(oldComponentsParentPageId, newComponentsParentPageId, null);
    }

    void moveTrackedPages(String oldComponentsParentPageId, String newComponentsParentPageId,
                          String targetSystemName) {
        transactionRunner.run(() -> componentPageRepository.findByParentPageId(oldComponentsParentPageId)).stream()
                .map(ComponentPage::getComponentId)
                .map(id -> transactionRunner.run(() -> componentRepository.findById(id).map(component -> {
                    Hibernate.initialize(component.getSystem());
                    return component;
                })))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(Component::getId))
                .forEach(component -> generatePage(newComponentsParentPageId, component,
                        targetSystemName == null ? component.getSystem().getName() : targetSystemName));
    }
}
