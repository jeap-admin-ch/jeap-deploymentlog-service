package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import lombok.Data;

@Data
public class BlogpostCreateDto {

    String spaceKey;

    String title;

    String content;
}
