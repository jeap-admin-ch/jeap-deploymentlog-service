package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Reference;
import ch.admin.bit.jeap.deploymentlog.domain.ReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReferenceRepositoryImpl implements ReferenceRepository {

    private final JpaReferenceRepository jpaReferenceRepository;

    @Override
    public boolean existsById(UUID id) {
        return jpaReferenceRepository.existsById(id);
    }

    @Override
    public List<Reference> findAllByReferenceIdentifier(String referenceIdentifier) {
        return jpaReferenceRepository.findAllByReferenceIdentifier(referenceIdentifier);
    }

    @Override
    public void save(Reference reference) {
        jpaReferenceRepository.save(reference);
    }
}
