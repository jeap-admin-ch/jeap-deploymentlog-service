package ch.admin.bit.jeap.deploymentlog.persistence;

import ch.admin.bit.jeap.deploymentlog.domain.JiraChangeViewRequest;
import ch.admin.bit.jeap.deploymentlog.domain.JiraChangeViewRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = PersistenceConfiguration.class)
class JiraChangeViewRequestRepositoryImplTest {

    @Autowired
    private JiraChangeViewRequestRepository repository;

    @Test
    void deletesOnlyTheProcessedRequestsOfAProject() {
        ZonedDateTime now = ZonedDateTime.now();
        JiraChangeViewRequest processed = JiraChangeViewRequest.create("JEAP-1", "JEAP", now.minusMinutes(2));
        JiraChangeViewRequest sameIssueRecordedLater = JiraChangeViewRequest.create("JEAP-1", "JEAP", now);
        JiraChangeViewRequest otherProject = JiraChangeViewRequest.create("ABC-1", "ABC", now);
        repository.saveAll(List.of(processed, sameIssueRecordedLater, otherProject));

        repository.deleteAllById(List.of(processed.getId()));

        assertThat(repository.findByProjectKey("JEAP"))
                .extracting(JiraChangeViewRequest::getId)
                .containsExactly(sameIssueRecordedLater.getId());
        assertThat(repository.findByProjectKey("ABC")).hasSize(1);
    }

    @Test
    void findsProjectsWithRequestsOlderThanTheGivenTimeOldestFirst() {
        ZonedDateTime now = ZonedDateTime.now();
        repository.saveAll(List.of(
                JiraChangeViewRequest.create("NEW-1", "NEW", now),
                JiraChangeViewRequest.create("LATER-1", "LATER", now.minusMinutes(20)),
                JiraChangeViewRequest.create("LATER-2", "LATER", now.minusMinutes(20)),
                JiraChangeViewRequest.create("OLDEST-1", "OLDEST", now.minusMinutes(30)),
                JiraChangeViewRequest.create("OLDEST-1", "OLDEST", now.minusMinutes(25))));

        ZonedDateTime requestedBefore = now.minusMinutes(10);

        assertThat(repository.findProjectKeysRequestedBefore(requestedBefore, 10))
                .containsExactly("OLDEST", "LATER");
        assertThat(repository.findProjectKeysRequestedBefore(requestedBefore, 1))
                .containsExactly("OLDEST");
        assertThat(repository.countIssueKeysRequestedBefore(requestedBefore)).isEqualTo(3);
    }
}
