package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("ConstantConditions")
@Slf4j
class VersionNumberTest {

    @Test
    void of_versionOk_versionMatches_majorMinorPatchBuild() {
        final VersionNumber versionNumber = VersionNumber.of("2.3.1-20220315170138");
        assertThat(versionNumber).isNotNull();
        assertThat(versionNumber.getMajorVersion()).isEqualTo(new BigDecimal(2));
        assertThat(versionNumber.getMinorVersion()).isEqualTo(new BigDecimal(3));
        assertThat(versionNumber.getPatchVersion()).isEqualTo(new BigDecimal(1));
        assertThat(versionNumber.getBuildVersion()).isEqualTo(new BigDecimal(20220315170138L));
    }

    @Test
    void of_versionOk_versionMatches_majorBuild() {
        final VersionNumber versionNumber = VersionNumber.of("12-123");
        assertThat(versionNumber).isNotNull();
        assertThat(versionNumber.getMajorVersion()).isEqualTo(new BigDecimal(12));
        assertThat(versionNumber.getMinorVersion()).isNull();
        assertThat(versionNumber.getPatchVersion()).isNull();
        assertThat(versionNumber.getBuildVersion()).isEqualTo(new BigDecimal(123));
    }

    @Test
    void of_versionOk_versionMatches_majorMinorBuild() {
        final VersionNumber versionNumber = VersionNumber.of("12.5-123");
        assertThat(versionNumber).isNotNull();
        assertThat(versionNumber.getMajorVersion()).isEqualTo(new BigDecimal(12));
        assertThat(versionNumber.getMinorVersion()).isEqualTo(new BigDecimal(5));
        assertThat(versionNumber.getPatchVersion()).isNull();
        assertThat(versionNumber.getBuildVersion()).isEqualTo(new BigDecimal(123));
    }

    @Test
    void of_versionOk_versionMatches_majorOnly() {
        final VersionNumber versionNumber = VersionNumber.of("12");
        assertThat(versionNumber).isNotNull();
        assertThat(versionNumber.getMajorVersion()).isEqualTo(new BigDecimal(12));
        assertThat(versionNumber.getMinorVersion()).isNull();
        assertThat(versionNumber.getPatchVersion()).isNull();
        assertThat(versionNumber.getBuildVersion()).isNull();
    }

    @Test
    void of_versionOk_versionMatches_unknownPostfix_shouldBeIgnored() {
        final VersionNumber versionNumber = VersionNumber.of("12.1-ALPHA25");
        assertThat(versionNumber).isNotNull();
        assertThat(versionNumber.getMajorVersion()).isEqualTo(new BigDecimal(12));
        assertThat(versionNumber.getMinorVersion()).isEqualTo(new BigDecimal(1));
        assertThat(versionNumber.getPatchVersion()).isNull();
        assertThat(versionNumber.getBuildVersion()).isNull();

        final VersionNumber versionNumber2 = VersionNumber.of("12.15.3_15-ALPHA25");
        assertThat(versionNumber2).isNotNull();
        assertThat(versionNumber2.getMajorVersion()).isEqualTo(new BigDecimal(12));
        assertThat(versionNumber2.getMinorVersion()).isEqualTo(new BigDecimal(15));
        assertThat(versionNumber2.getPatchVersion()).isEqualTo(new BigDecimal(3));
        assertThat(versionNumber2.getBuildVersion()).isEqualTo(new BigDecimal(15));
    }

    @Test
    void of_versionOk_versionMatches_majorMinor() {
        final VersionNumber versionNumber = VersionNumber.of("12.1");
        assertThat(versionNumber).isNotNull();
        assertThat(versionNumber.getMajorVersion()).isEqualTo(new BigDecimal(12));
        assertThat(versionNumber.getMinorVersion()).isEqualTo(new BigDecimal(1));
        assertThat(versionNumber.getPatchVersion()).isNull();
        assertThat(versionNumber.getBuildVersion()).isNull();
    }

    @Test
    void of_versionOk_versionMatches_majorMinorPatch() {
        final VersionNumber versionNumber = VersionNumber.of("12.55.88");
        assertThat(versionNumber).isNotNull();
        assertThat(versionNumber.getMajorVersion()).isEqualTo(new BigDecimal(12));
        assertThat(versionNumber.getMinorVersion()).isEqualTo(new BigDecimal(55));
        assertThat(versionNumber.getPatchVersion()).isEqualTo(new BigDecimal(88));
        assertThat(versionNumber.getBuildVersion()).isNull();
    }

    @Test
    void of_versionOk_versionMatches_snapshots() {
        final VersionNumber versionNumber = VersionNumber.of("12.1.5-SNAPSHOT");
        assertThat(versionNumber).isNotNull();
        assertThat(versionNumber.getMajorVersion()).isEqualTo(new BigDecimal(12));
        assertThat(versionNumber.getMinorVersion()).isEqualTo(new BigDecimal(1));
        assertThat(versionNumber.getPatchVersion()).isEqualTo(new BigDecimal(5));
        assertThat(versionNumber.getBuildVersion()).isNull();
    }

    @Test
    void of_versionNok_versionNull() {
        final VersionNumber versionNumber = VersionNumber.of("abc");
        assertThat(versionNumber).isNull();
    }

    @SuppressWarnings("EqualsWithItself")
    @Test
    void compareTo() {
        VersionNumber v11_SNAPSHOT = VersionNumber.of("11-SNAPSHOT");
        VersionNumber v11 = VersionNumber.of("11");
        VersionNumber v12_25_SNAPSHOT = VersionNumber.of("12.2.5-SNAPSHOT");
        VersionNumber v12_2_5 = VersionNumber.of("12.2.5");
        VersionNumber v13_1 = VersionNumber.of("13.1");
        VersionNumber v13_1_2_5 = VersionNumber.of("13.1.2.5");
        VersionNumber v14 = VersionNumber.of("14");

        VersionNumber v1 = VersionNumber.of("1");
        VersionNumber v1_build5 = VersionNumber.of("1-5");
        VersionNumber v1_build5_alpha = VersionNumber.of("1-5-ALPHA");

        VersionNumber v0_0_1 = VersionNumber.of("0.0.1");
        VersionNumber v0_0_2 = VersionNumber.of("0.0.2");

        assertTrue(v0_0_1.compareTo(v0_0_2) < 0);
        assertTrue(v0_0_2.compareTo(v0_0_1) > 0);
        assertEquals(0, v0_0_1.compareTo(v0_0_1));
        assertTrue(v11_SNAPSHOT.compareTo(v11) < 0);
        assertTrue(v11.compareTo(v11_SNAPSHOT) > 0);

        assertTrue(v13_1_2_5.compareTo(v13_1) > 0);

        assertTrue(v12_25_SNAPSHOT.compareTo(v12_2_5) < 0);

        assertTrue(v14.compareTo(v11) > 0);
        assertTrue(v14.compareTo(v12_2_5) > 0);

        assertTrue(v1_build5.compareTo(v1) > 0);
        assertTrue(v1_build5.compareTo(v1_build5_alpha) > 0);
    }

    @Test
    void compare_sortAsExpected() {
        VersionNumber v11_SNAPSHOT = VersionNumber.of("11-SNAPSHOT");
        VersionNumber v11 = VersionNumber.of("11");
        VersionNumber v12_2_5_SNAPSHOT = VersionNumber.of("12.2.5-SNAPSHOT");
        VersionNumber v12_2_5 = VersionNumber.of("12.2.5");
        VersionNumber v13_1 = VersionNumber.of("13.1");
        VersionNumber v13_1_2_5 = VersionNumber.of("13.1.2.5");
        VersionNumber v13_1_2_6 = VersionNumber.of("13.1.2.6");
        VersionNumber v13_1_3 = VersionNumber.of("13.1.3");
        VersionNumber v13_2_3 = VersionNumber.of("13.2.3");
        VersionNumber v14 = VersionNumber.of("14");

        List<VersionNumber> numbers = List.of(
                v14, v13_2_3, v13_1_3, v11_SNAPSHOT, v11, v12_2_5, v13_1, v13_1_2_6, v13_1_2_5, v12_2_5_SNAPSHOT
        );

        List<VersionNumber> sortedList = numbers.stream().sorted().collect(toList());

        assertEquals(List.of(
                v11_SNAPSHOT,
                v11,
                v12_2_5_SNAPSHOT,
                v12_2_5,
                v13_1,
                v13_1_2_5,
                v13_1_2_6,
                v13_1_3,
                v13_2_3,
                v14), sortedList);
    }

    @Test
    void compare_nullSafe() {
        VersionNumber v = VersionNumber.of("11");

        assertTrue(v.compareTo(null) > 0);
    }
}
