package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.SystemRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
@Slf4j
class SystemRepositoryImplTest {

    @Autowired
    private SystemRepository systemRepository;

    @Test
    void findByNameIgnoreCase() {
        systemRepository.save(new System("TEST"));

        Optional<System> system = systemRepository.findByNameIgnoreCase("test");
        assertThat(system).isPresent();
    }

}
