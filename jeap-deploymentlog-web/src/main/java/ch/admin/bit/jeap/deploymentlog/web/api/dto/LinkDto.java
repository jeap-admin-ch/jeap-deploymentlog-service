package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import ch.admin.bit.jeap.deploymentlog.domain.Link;
import lombok.Value;

import java.util.Set;

import static java.util.stream.Collectors.toSet;

@Value
public class LinkDto {
    private String label;
    private String url;

    static Set<LinkDto> allOf(Set<Link> links) {
        if (links == null) {
            return Set.of();
        }
        return links.stream()
                .map(link -> new LinkDto(link.getLabel(), link.getLabel()))
                .collect(toSet());
    }
}
