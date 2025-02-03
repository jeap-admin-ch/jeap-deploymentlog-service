package ch.admin.bit.jeap.deploymentlog.domain;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.util.Objects;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@Builder
@AllArgsConstructor(access = PRIVATE)
@NoArgsConstructor(access = PROTECTED) // for JPA
@ToString
@Getter
@Embeddable
public class Link {

    @NonNull
    private String label;

    @NonNull
    private String url;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Link link = (Link) o;
        return Objects.equals(label, link.label);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(label);
    }
}
