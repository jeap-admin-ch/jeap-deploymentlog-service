package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import ch.admin.bit.jeap.deploymentlog.domain.Link;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LinkDtoTest {

    @Test
    void allOf_mapsLabelAndUrl() {
        Link link = Link.builder()
                .label("Build job")
                .url("https://example.com/job/42")
                .build();

        Set<LinkDto> linkDtos = LinkDto.allOf(Set.of(link));

        assertThat(linkDtos).singleElement().satisfies(linkDto -> {
            assertThat(linkDto.getLabel()).isEqualTo("Build job");
            assertThat(linkDto.getUrl()).isEqualTo("https://example.com/job/42");
        });
    }

    @Test
    void allOf_null() {
        assertThat(LinkDto.allOf(null)).isEmpty();
    }
}
