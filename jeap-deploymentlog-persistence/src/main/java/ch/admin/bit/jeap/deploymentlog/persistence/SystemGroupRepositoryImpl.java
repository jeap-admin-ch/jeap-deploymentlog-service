package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.SystemGroup;
import ch.admin.bit.jeap.deploymentlog.domain.SystemGroupRepository;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemGroupNameAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SystemGroupRepositoryImpl implements SystemGroupRepository {

    private static final String NORMALIZED_NAME_UNIQUE_CONSTRAINT = "system_group_normalized_name_uk";

    private final JpaSystemGroupRepository jpaSystemGroupRepository;

    @Override
    public SystemGroup save(SystemGroup systemGroup) {
        try {
            return jpaSystemGroupRepository.saveAndFlush(systemGroup);
        } catch (DataIntegrityViolationException ex) {
            if (isNormalizedNameUniqueConstraintViolation(ex)) {
                throw new SystemGroupNameAlreadyExistsException(systemGroup.getName(), ex);
            }
            throw ex;
        }
    }

    @Override
    public Optional<SystemGroup> findById(UUID id) {
        return jpaSystemGroupRepository.findById(id);
    }

    @Override
    public Optional<SystemGroup> findByNormalizedName(String normalizedName) {
        return jpaSystemGroupRepository.findByNormalizedName(normalizedName);
    }

    @Override
    public List<SystemGroup> findAllSorted() {
        return jpaSystemGroupRepository.findAllByOrderByNormalizedNameAscIdAsc();
    }

    @Override
    public void delete(SystemGroup systemGroup) {
        jpaSystemGroupRepository.delete(systemGroup);
    }

    private static boolean isNormalizedNameUniqueConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && containsNormalizedNameConstraint(constraintViolation.getConstraintName())) {
                return true;
            }
            if (current instanceof SQLException sqlException
                    && containsNormalizedNameConstraint(sqlException.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean containsNormalizedNameConstraint(String value) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(NORMALIZED_NAME_UNIQUE_CONSTRAINT);
    }
}
