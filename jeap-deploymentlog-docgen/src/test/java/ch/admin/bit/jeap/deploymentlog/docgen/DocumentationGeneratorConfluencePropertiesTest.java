package ch.admin.bit.jeap.deploymentlog.docgen;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentationGeneratorConfluencePropertiesTest {

    @Test
    void componentFlowMaxShowDefaultsToFifty() {
        assertThat(new DocumentationGeneratorConfluenceProperties().getComponentFlowMaxShow()).isEqualTo(50);
    }

    @Test
    void componentFlowMaxShowMustBeGreaterThanZero() {
        DocumentationGeneratorConfluenceProperties properties = new DocumentationGeneratorConfluenceProperties();
        properties.setComponentFlowMaxShow(0);

        assertThatThrownBy(properties::init)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("component-flow-max-show");
    }
}
