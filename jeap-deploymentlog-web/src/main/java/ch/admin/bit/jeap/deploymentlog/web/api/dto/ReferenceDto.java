package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import ch.admin.bit.jeap.deploymentlog.domain.ReferenceType;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.UUID;

@Builder
@Data
public class ReferenceDto {

    @NonNull
    private UUID id;

    @NonNull
    String referenceIdentifier;

    @NonNull
    ReferenceType type;

    @NonNull
    String uri;
}
