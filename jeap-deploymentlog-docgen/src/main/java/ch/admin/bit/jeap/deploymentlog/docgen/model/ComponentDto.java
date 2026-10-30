package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ComponentDto {
    String componentName;
    String componentPageUrl;
    List<ComponentEnvDto> componentEnvDtoList;
}
