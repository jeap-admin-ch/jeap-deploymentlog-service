package ch.admin.bit.jeap.deploymentlog.domain;

import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidStagingConfigurationException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidStagingRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StagingEnvironmentResolverTest {

    private final EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
    private final StagingProperties properties = new StagingProperties();
    private final StagingEnvironmentResolver resolver = new StagingEnvironmentResolver(environmentRepository, properties);

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
                .isInstanceOf(InvalidStagingConfigurationException.class)
                .hasMessageContaining("exactly one");

        ref.setProductive(true);
        assertThatThrownBy(resolver::resolveDefaultFinalDeploymentEnvironment)
                .isInstanceOf(InvalidStagingConfigurationException.class)
                .hasMessageContaining("found 2");
    }

    @Test
    void rejectsBlankExplicitConfiguration() {
        properties.setStartEnvironment("  ");

        assertThatThrownBy(resolver::resolveStartEnvironment)
                .isInstanceOf(InvalidStagingConfigurationException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void validatesTargetsRegardlessOfOrderDuplicatesOrEmptyRequests() {
        resolver.validateFinalDeploymentEnvironments(List.of("ABN", " ref ", "ABN"));
        resolver.validateFinalDeploymentEnvironments(List.of());
        resolver.validateFinalDeploymentEnvironments(null);
    }

    @Test
    void rejectsAllRequestedStagesWhenOneIsUnknown() {
        List<String> requestedEnvironments = List.of("ABN", "UNKNOWN");

        assertThatThrownBy(() -> resolver.validateFinalDeploymentEnvironments(requestedEnvironments))
                .isInstanceOf(InvalidStagingRequestException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void acceptsKnownTargetsWithoutSelectingOneHighestStage() {
        ref.setStagingOrder(2);
        resolver.validateFinalDeploymentEnvironments(List.of("REF", "ABN"));
    }

    private static Environment environment(String name, int order, boolean development, boolean productive) {
        Environment environment = new Environment(name);
        environment.setStagingOrder(order);
        environment.setDevelopment(development);
        environment.setProductive(productive);
        return environment;
    }
}
