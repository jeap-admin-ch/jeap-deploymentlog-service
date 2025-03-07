package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class EnvironmentComponentVersionStateDto {

    private String systemName;

    private List<ComponentSnapshotDto> components;

}
