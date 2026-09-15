package ch.admin.bit.jeap.deploymentlog.docgen.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SchedulingConfigPropertiesTest {

    @Test
    void defaultsToOneWeek() {
        SchedulingConfigProperties properties = new SchedulingConfigProperties();
        assertThat(properties.getMaxAgeMinutes()).isEqualTo(10_080);
        assertThatCode(properties::init).doesNotThrowAnyException();
    }

    @Test
    void acceptsConfiguredAgeWindow() {
        SchedulingConfigProperties properties = new SchedulingConfigProperties();
        properties.setMinAgeMinutes(0);
        properties.setMaxAgeMinutes(2_880);
        assertThatCode(properties::init).doesNotThrowAnyException();
        assertThat(properties.getMaxAgeMinutes()).isEqualTo(2_880);
    }

    @Test
    void rejectsInvalidAgeWindow() {
        SchedulingConfigProperties properties = new SchedulingConfigProperties();
        properties.setMaxAgeMinutes(properties.getMinAgeMinutes());
        assertThatIllegalArgumentException().isThrownBy(properties::init);
        properties.setMaxAgeMinutes(0);
        assertThatIllegalArgumentException().isThrownBy(properties::init);
        properties.setMaxAgeMinutes(10_080);
        properties.setMinAgeMinutes(-1);
        assertThatIllegalArgumentException().isThrownBy(properties::init);
    }
}
