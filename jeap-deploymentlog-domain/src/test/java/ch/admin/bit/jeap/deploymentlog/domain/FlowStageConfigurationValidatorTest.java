package ch.admin.bit.jeap.deploymentlog.domain;

import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidFlowStageConfigurationException;
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
class FlowStageConfigurationValidatorTest {

    @Mock
    private FlowStageResolver flowStageResolver;

    @Test
    void validatesStartAndDefaultFinalEnvironmentWhenEnabled() {
        FlowStageProperties properties = new FlowStageProperties();
        when(flowStageResolver.resolveStartEnvironment()).thenReturn(new Environment("DEV"));
        when(flowStageResolver.resolveDefaultFinalDeploymentEnvironment()).thenReturn(new Environment("PROD"));

        new FlowStageConfigurationValidator(properties, flowStageResolver)
                .run(new DefaultApplicationArguments());

        verify(flowStageResolver).resolveStartEnvironment();
        verify(flowStageResolver).resolveDefaultFinalDeploymentEnvironment();
        verify(flowStageResolver).validateProductiveEnvironmentExists();
    }

    @Test
    void propagatesInvalidConfigurationAndAbortsStartup() {
        FlowStageProperties properties = new FlowStageProperties();
        when(flowStageResolver.resolveStartEnvironment())
                .thenThrow(new InvalidFlowStageConfigurationException("No start environment"));
        FlowStageConfigurationValidator validator =
                new FlowStageConfigurationValidator(properties, flowStageResolver);
        DefaultApplicationArguments arguments = new DefaultApplicationArguments();

        assertThatThrownBy(() -> validator.run(arguments))
                .isInstanceOf(InvalidFlowStageConfigurationException.class)
                .hasMessage("No start environment");
    }

    @Test
    void abortsStartupWhenNoProductiveEnvironmentExists() {
        FlowStageProperties properties = new FlowStageProperties();
        when(flowStageResolver.resolveStartEnvironment()).thenReturn(new Environment("DEV"));
        when(flowStageResolver.resolveDefaultFinalDeploymentEnvironment()).thenReturn(new Environment("REF"));
        doThrow(new InvalidFlowStageConfigurationException("No productive environment"))
                .when(flowStageResolver).validateProductiveEnvironmentExists();
        FlowStageConfigurationValidator validator =
                new FlowStageConfigurationValidator(properties, flowStageResolver);
        DefaultApplicationArguments arguments = new DefaultApplicationArguments();

        assertThatThrownBy(() -> validator.run(arguments))
                .isInstanceOf(InvalidFlowStageConfigurationException.class)
                .hasMessage("No productive environment");
    }

    @Test
    void skipsValidationWhenDisabled() {
        FlowStageProperties properties = new FlowStageProperties();
        properties.setEnabled(false);

        new FlowStageConfigurationValidator(properties, flowStageResolver)
                .run(new DefaultApplicationArguments());

        verify(flowStageResolver, never()).resolveStartEnvironment();
        verify(flowStageResolver, never()).resolveDefaultFinalDeploymentEnvironment();
        verify(flowStageResolver, never()).validateProductiveEnvironmentExists();
    }
}
