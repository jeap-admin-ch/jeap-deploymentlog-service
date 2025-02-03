package ch.admin.bit.jeap.deploymentlog.jira.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraErrorResponse {
    List<String> errorMessages;
}
