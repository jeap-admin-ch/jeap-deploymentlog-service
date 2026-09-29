package ch.admin.bit.jeap.deploymentlog.domain;

import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidStagingConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StagingConfigurationValidatorTest {

    @Mock
    private StagingEnvironmentResolver stagingEnvironmentResolver;

    @Test
    void validatesStartAndDefaultFinalEnvironmentWhenEnabled() {
        StagingProperties properties = new StagingProperties();
        when(stagingEnvironmentResolver.resolveStartEnvironment()).thenReturn(new Environment("DEV"));
        when(stagingEnvironmentResolver.resolveDefaultFinalDeploymentEnvironment()).thenReturn(new Environment("PROD"));

        new StagingConfigurationValidator(properties, stagingEnvironmentResolver)
                .run(new DefaultApplicationArguments());

        verify(stagingEnvironmentResolver).resolveStartEnvironment();
        verify(stagingEnvironmentResolver).resolveDefaultFinalDeploymentEnvironment();
        verify(stagingEnvironmentResolver).relevantEnvironments();
    }

    @Test
    void propagatesInvalidConfigurationAndAbortsStartup() {
        StagingProperties properties = new StagingProperties();
        when(stagingEnvironmentResolver.resolveStartEnvironment())
                .thenThrow(new InvalidStagingConfigurationException("No start environment"));
        StagingConfigurationValidator validator =
                new StagingConfigurationValidator(properties, stagingEnvironmentResolver);
        DefaultApplicationArguments arguments = new DefaultApplicationArguments();

        assertThatThrownBy(() -> validator.run(arguments))
                .isInstanceOf(InvalidStagingConfigurationException.class)
                .hasMessage("No start environment");
    }

    @Test
    void abortsStartupWhenRelevantStageOrderingIsAmbiguous() {
        StagingProperties properties = new StagingProperties();
        when(stagingEnvironmentResolver.resolveStartEnvironment()).thenReturn(new Environment("DEV"));
        when(stagingEnvironmentResolver.resolveDefaultFinalDeploymentEnvironment()).thenReturn(new Environment("REF"));
        doThrow(new InvalidStagingConfigurationException("Ambiguous stage order"))
                .when(stagingEnvironmentResolver).relevantEnvironments();
        StagingConfigurationValidator validator =
                new StagingConfigurationValidator(properties, stagingEnvironmentResolver);
        DefaultApplicationArguments arguments = new DefaultApplicationArguments();

        assertThatThrownBy(() -> validator.run(arguments))
                .isInstanceOf(InvalidStagingConfigurationException.class)
                .hasMessage("Ambiguous stage order");
    }

    @Test
    void skipsValidationWhenDisabled() {
        StagingProperties properties = new StagingProperties();
        properties.setEnabled(false);

        new StagingConfigurationValidator(properties, stagingEnvironmentResolver)
                .run(new DefaultApplicationArguments());

        verify(stagingEnvironmentResolver, never()).resolveStartEnvironment();
        verify(stagingEnvironmentResolver, never()).resolveDefaultFinalDeploymentEnvironment();
        verify(stagingEnvironmentResolver, never()).relevantEnvironments();
    }
}
