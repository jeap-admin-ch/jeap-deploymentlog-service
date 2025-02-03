package ch.admin.bit.jeap.deploymentlog.docgen.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConfluenceStorageDto {

    String value;

    String representation;

}
