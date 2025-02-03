package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.Reference;
import ch.admin.bit.jeap.deploymentlog.domain.ReferenceRepository;
import ch.admin.bit.jeap.deploymentlog.domain.ReferenceType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
@Slf4j
class ReferenceRepositoryImplTest {

    @Autowired
    private ReferenceRepository referenceRepository;

    @Autowired
    private JpaReferenceRepository jpaReferenceRepository;

    @Test
    void existsById_shouldReturnTrue_whenReferenceExists() {
        UUID id = UUID.randomUUID();
        Reference reference = Reference.builder()
                .id(id)
                .referenceIdentifier("test-identifier")
                .type(ReferenceType.BUILD_JOB_LINK_BY_GIT_URL_AND_VERSION)
                .uri("http://test-url")
                .build();
        jpaReferenceRepository.save(reference);

        boolean exists = referenceRepository.existsById(id);

        assertThat(exists).isTrue();
    }

    @Test
    void existsById_shouldReturnFalse_whenReferenceDoesNotExist() {
        UUID id = UUID.randomUUID();

        boolean exists = referenceRepository.existsById(id);

        assertThat(exists).isFalse();
    }

    @Test
    void findByReferenceIdentifier_shouldReturnReference_whenReferenceExists() {
        String referenceIdentifier = "test-identifier-" + UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Reference reference = Reference.builder()
                .id(id)
                .referenceIdentifier(referenceIdentifier)
                .type(ReferenceType.BUILD_JOB_LINK_BY_GIT_URL_AND_VERSION)
                .uri("http://test-url")
                .build();

        jpaReferenceRepository.save(reference);

        List<Reference> foundReference = referenceRepository.findAllByReferenceIdentifier(referenceIdentifier);

        assertThat(foundReference).isNotEmpty();
        assertThat(foundReference.getFirst()).isEqualTo(reference);
    }

    @Test
    void findByReferenceIdentifier_shouldReturnEmpty_whenReferenceDoesNotExist() {
        String referenceIdentifier = "non-existent-identifier";

        List<Reference> foundReference = referenceRepository.findAllByReferenceIdentifier(referenceIdentifier);

        assertThat(foundReference).isEmpty();
    }
}
