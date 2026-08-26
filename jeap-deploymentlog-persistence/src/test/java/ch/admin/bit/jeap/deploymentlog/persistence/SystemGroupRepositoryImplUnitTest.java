package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.SystemGroup;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemGroupNameAlreadyExistsException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemGroupRepositoryImplUnitTest {

    @Mock
    private JpaSystemGroupRepository jpaSystemGroupRepository;
    @InjectMocks
    private SystemGroupRepositoryImpl systemGroupRepository;

    @Test
    void translatesNormalizedNameConstraintViolation() {
        SystemGroup group = new SystemGroup("Border Control");
        ConstraintViolationException constraintViolation = new ConstraintViolationException(
                "Duplicate system group name",
                new SQLException("duplicate key"),
                "system_group_normalized_name_uk");
        DataIntegrityViolationException dataIntegrityViolation =
                new DataIntegrityViolationException("Could not save system group", constraintViolation);
        when(jpaSystemGroupRepository.saveAndFlush(group)).thenThrow(dataIntegrityViolation);

        assertThatThrownBy(() -> systemGroupRepository.save(group))
                .isInstanceOf(SystemGroupNameAlreadyExistsException.class)
                .hasCause(dataIntegrityViolation);
    }

    @Test
    void preservesUnrelatedDataIntegrityViolation() {
        SystemGroup group = new SystemGroup("Border Control");
        DataIntegrityViolationException dataIntegrityViolation =
                new DataIntegrityViolationException("Unrelated integrity violation");
        when(jpaSystemGroupRepository.saveAndFlush(group)).thenThrow(dataIntegrityViolation);

        assertThatThrownBy(() -> systemGroupRepository.save(group))
                .isSameAs(dataIntegrityViolation);
    }
}
