package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class LinkDto {
    @NonNull
    String linkLabel;
    @NonNull
    String linkUrl;
}
