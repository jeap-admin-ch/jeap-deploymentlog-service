package ch.admin.bit.jeap.deploymentlog.docgen.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfluenceApiLinks {

    private String tinyui;

    private String base;

}
