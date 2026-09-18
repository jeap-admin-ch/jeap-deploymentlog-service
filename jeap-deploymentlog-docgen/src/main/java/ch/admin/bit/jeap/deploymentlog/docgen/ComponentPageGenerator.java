package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.domain.Component;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPage;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentPageRepository;
import ch.admin.bit.jeap.deploymentlog.domain.ComponentRepository;
import ch.admin.bit.jeap.deploymentlog.domain.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@org.springframework.stereotype.Component
@RequiredArgsConstructor
@Slf4j
public class ComponentPageGenerator {

    private final ConfluenceAdapter confluenceAdapter;
    private final TemplateRenderer templateRenderer;
    private final ComponentPageDtoFactory dtoFactory;
    private final ComponentPageRepository componentPageRepository;
    private final ComponentRepository componentRepository;
    private final DeploymentRepository deploymentRepository;

    @Transactional
    public String generatePage(String componentsParentPageId, Component component) {
        return generatePage(componentsParentPageId, component, component.getSystem().getName());
    }

    private String generatePage(String componentsParentPageId, Component component, String systemName) {
        if (!deploymentRepository.existsCodeDeploymentForComponent(component.getId())) {
            log.info("Skipping component page generation for system '{}' and component '{}': no CODE deployment exists",
                    systemName, component.getName());
            return null;
        }

        componentRepository.lockById(component.getId());
        String pageTitle = pageTitle(component, systemName);
        Optional<ComponentPage> trackedPage = componentPageRepository.findByComponentId(component.getId());
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
        return component.getName() + " (" + systemName + ")";
    }

    @Transactional
    public void generatePages(String componentsParentPageId, Collection<Component> components) {
        generatePages(componentsParentPageId, components, null);
    }

    @Transactional
    void generatePages(String componentsParentPageId, Collection<Component> components, String targetSystemName) {
        components.stream()
                .sorted(Comparator.comparing(Component::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Component::getId))
                .forEach(component -> generatePage(componentsParentPageId, component,
                        targetSystemName == null ? component.getSystem().getName() : targetSystemName));
    }

    @Transactional
    public void moveTrackedPages(String oldComponentsParentPageId, String newComponentsParentPageId) {
        moveTrackedPages(oldComponentsParentPageId, newComponentsParentPageId, null);
    }

    @Transactional
    void moveTrackedPages(String oldComponentsParentPageId, String newComponentsParentPageId,
                          String targetSystemName) {
        componentPageRepository.findByParentPageId(oldComponentsParentPageId).stream()
                .map(ComponentPage::getComponentId)
                .map(componentRepository::findById)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(Component::getId))
                .forEach(component -> generatePage(newComponentsParentPageId, component,
                        targetSystemName == null ? component.getSystem().getName() : targetSystemName));
    }
}
