package ch.admin.bit.jeap.deploymentlog.docgen.model;

import ch.admin.bit.jeap.deploymentlog.domain.VersionNumber;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder
public class ComponentEnvDto {

    VersionNumber versionNumber;
    boolean developmentEnvironment;
    String versionName;
    String versionControlUrl;
    String deployedAt;
    String deploymentLetterUrl;
    @Setter
    Color color;

    public String getColorClass() {
        return "highlight-" + color.colorCode;
    }
}
