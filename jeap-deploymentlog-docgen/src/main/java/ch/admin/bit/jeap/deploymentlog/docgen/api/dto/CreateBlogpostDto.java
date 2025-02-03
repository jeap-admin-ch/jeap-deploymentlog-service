package ch.admin.bit.jeap.deploymentlog.docgen.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateBlogpostDto {


    String type;
    String title;

    ConfluenceSpaceDto space;

    ConfluenceBodyDto body;

}
