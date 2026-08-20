package ch.admin.bit.jeap.deploymentlog.domain;

import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidFlowStageConfigurationException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidFlowStageRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowStageResolverTest {

    private final EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
    private final FlowStageProperties properties = new FlowStageProperties();
    private final FlowStageResolver resolver = new FlowStageResolver(environmentRepository, properties);

    private Environment dev;
    private Environment ref;
    private Environment abn;
    private Environment prod;

    @BeforeEach
    void setUp() {
        dev = environment("DEV", 0, true, false);
        ref = environment("REF", 1, false, false);
        abn = environment("ABN", 2, false, false);
        prod = environment("PROD", 3, false, true);
        when(environmentRepository.findAll()).thenReturn(List.of(dev, ref, abn, prod));
        when(environmentRepository.findByName("DEV")).thenReturn(Optional.of(dev));
        when(environmentRepository.findByName("REF")).thenReturn(Optional.of(ref));
        when(environmentRepository.findByName("ABN")).thenReturn(Optional.of(abn));
        when(environmentRepository.findByName("PROD")).thenReturn(Optional.of(prod));
    }

    @Test
    void resolvesExplicitStagesBeforeFlags() {
        properties.setStartEnvironment(" ref ");
        properties.setDefaultFinalDeploymentEnvironment("abn");

        assertThat(resolver.resolveStartEnvironment()).isSameAs(ref);
        assertThat(resolver.resolveDefaultFinalDeploymentEnvironment()).isSameAs(abn);
    }

    @Test
    void resolvesFlaggedFallbackStages() {
        assertThat(resolver.resolveStartEnvironment()).isSameAs(dev);
        assertThat(resolver.resolveDefaultFinalDeploymentEnvironment()).isSameAs(prod);
    }

    @Test
    void rejectsMissingOrAmbiguousFallback() {
        dev.setDevelopment(false);

        assertThatThrownBy(resolver::resolveStartEnvironment)
                .isInstanceOf(InvalidFlowStageConfigurationException.class)
                .hasMessageContaining("exactly one");

        ref.setProductive(true);
        assertThatThrownBy(resolver::resolveDefaultFinalDeploymentEnvironment)
                .isInstanceOf(InvalidFlowStageConfigurationException.class)
                .hasMessageContaining("found 2");
    }

    @Test
    void rejectsBlankExplicitConfiguration() {
        properties.setStartEnvironment("  ");

        assertThatThrownBy(resolver::resolveStartEnvironment)
                .isInstanceOf(InvalidFlowStageConfigurationException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void choosesHighestRequestedStageRegardlessOfOrderAndDuplicates() {
        assertThat(resolver.resolveEffectiveFinalDeploymentEnvironment(List.of("ABN", " ref ", "ABN")))
                .isSameAs(abn);
        assertThat(resolver.resolveEffectiveFinalDeploymentEnvironment(List.of("REF", "ABN")))
                .isSameAs(abn);
    }

    @Test
    void emptyRequestUsesDefaultFinalEnvironment() {
        assertThat(resolver.resolveEffectiveFinalDeploymentEnvironment(List.of())).isSameAs(prod);
        assertThat(resolver.resolveEffectiveFinalDeploymentEnvironment(null)).isSameAs(prod);
    }

    @Test
    void rejectsAllRequestedStagesWhenOneIsUnknown() {
        List<String> requestedEnvironments = List.of("ABN", "UNKNOWN");

        assertThatThrownBy(() -> resolver.resolveEffectiveFinalDeploymentEnvironment(requestedEnvironments))
                .isInstanceOf(InvalidFlowStageRequestException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void rejectsDifferentHighestStagesWithSameOrder() {
        ref.setStagingOrder(2);
        List<String> requestedEnvironments = List.of("REF", "ABN");

        assertThatThrownBy(() -> resolver.resolveEffectiveFinalDeploymentEnvironment(requestedEnvironments))
                .isInstanceOf(InvalidFlowStageRequestException.class)
                .hasMessageContaining("Ambiguous");
    }

    private static Environment environment(String name, int order, boolean development, boolean productive) {
        Environment environment = new Environment(name);
        environment.setStagingOrder(order);
        environment.setDevelopment(development);
        environment.setProductive(productive);
        return environment;
    }
}
