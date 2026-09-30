package ch.admin.bit.jeap.deploymentlog.docgen.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ComponentPageDto {
    public static String pageTitle(String componentName, String systemName) {
        return componentName + " (" + systemName + ")";
    }

    String componentName;
    int flowMaxShow;
    List<ComponentFlowDto> flows;
}
