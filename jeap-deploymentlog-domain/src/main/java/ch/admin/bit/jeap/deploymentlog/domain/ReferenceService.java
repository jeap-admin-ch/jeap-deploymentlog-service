package ch.admin.bit.jeap.deploymentlog.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReferenceService {

    private final ReferenceRepository referenceRepository;

    public boolean referenceExistsById(UUID referenceId) {
        return referenceRepository.existsById(referenceId);
    }

    public void saveReference(UUID id, ReferenceType type, String referenceIdentifier, String uri) {
        referenceRepository.save(Reference.builder()
                .id(id)
                .type(type)
                .referenceIdentifier(referenceIdentifier)
                .uri(uri)
                .build());
    }
}
