package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.DocumentationStructurePage;
import ch.admin.bit.jeap.deploymentlog.domain.DocumentationStructurePageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Component
@RequiredArgsConstructor
public class DocumentationStructurePageRepositoryImpl implements DocumentationStructurePageRepository {

    private final JpaDocumentationStructurePageRepository repository;

    @Override
    public DocumentationStructurePage save(DocumentationStructurePage page) {
        return repository.save(page);
    }

    @Override
    public Optional<DocumentationStructurePage> findByStructureKey(String structureKey) {
        return repository.findById(structureKey);
    }

    @Override
    public void lockByStructureKey(String structureKey) {
        repository.findForUpdateByStructureKey(structureKey).orElseThrow();
    }

    @Override
    public List<DocumentationStructurePage> findAll() {
        return StreamSupport.stream(repository.findAll().spliterator(), false).toList();
    }

    @Override
    public void delete(DocumentationStructurePage page) {
        repository.delete(page);
    }
}
