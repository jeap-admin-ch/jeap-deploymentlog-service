package ch.admin.bit.jeap.deploymentlog.docgen.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HousekeepingConfigPropertiesTest {

    @Test
    void defaultsKeepExistingPageCleanupAndDisableDataRetention() {
        HousekeepingConfigProperties properties = new HousekeepingConfigProperties();

        properties.validate();

        assertThat(properties.getConfluencePages().isEnabled()).isTrue();
        assertThat(properties.getConfluencePages().getMinAge()).isEqualTo(Duration.ofDays(7));
        assertThat(properties.getConfluencePages().effectiveKeepPerEnvironment(200)).isEqualTo(200);
        assertThat(properties.getDataRetention().isEnabled()).isFalse();
        assertThat(properties.getDataRetention().getDuration()).isNull();
        assertThat(properties.getComponentPages().isEnabled()).isTrue();
        assertThat(properties.getComponentPages().getBatchSize()).isEqualTo(100);
    }

    @Test
    void enabledDataRetentionRequiresPositiveDuration() {
        HousekeepingConfigProperties properties = new HousekeepingConfigProperties();
        properties.getDataRetention().setEnabled(true);

        assertThatIllegalArgumentException().isThrownBy(properties::validate)
                .withMessageContaining("data-retention.duration");

        properties.getDataRetention().setDuration(Duration.ZERO);
        assertThatIllegalArgumentException().isThrownBy(properties::validate)
                .withMessageContaining("data-retention.duration");

        properties.getDataRetention().setDuration(Duration.ofDays(30));
        properties.validate();
    }

    @Test
    void componentPageCleanupRequiresPositiveBatchSize() {
        HousekeepingConfigProperties properties = new HousekeepingConfigProperties();

        properties.getComponentPages().setBatchSize(0);
        assertThatIllegalArgumentException().isThrownBy(properties::validate)
                .withMessageContaining("component-pages.batch-size");

        properties.getComponentPages().setBatchSize(-1);
        assertThatIllegalArgumentException().isThrownBy(properties::validate)
                .withMessageContaining("component-pages.batch-size");
    }
}
