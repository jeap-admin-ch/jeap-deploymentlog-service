package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SystemPageDto {
    String name;
    List<String> environmentNamesArrayList;
    List<ComponentDto> componentList;
}
