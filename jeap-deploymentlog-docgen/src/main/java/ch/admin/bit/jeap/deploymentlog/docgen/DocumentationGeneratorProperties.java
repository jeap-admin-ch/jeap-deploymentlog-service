package ch.admin.bit.jeap.deploymentlog.docgen;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "jeap.deploymentlog.documentation-generator.config")
@Slf4j
public class DocumentationGeneratorProperties {

    private String remedyChangeLinkRootUrl;

    @PostConstruct
    void init() {
        log.info("Documentation generator configuration: {}", this);
    }

    public String getRemedyChangeLinkRootUrlWithTrailingSlash() {
        if (remedyChangeLinkRootUrl == null) {
            return "";
        }
        return remedyChangeLinkRootUrl.endsWith("/") ? remedyChangeLinkRootUrl :  remedyChangeLinkRootUrl + "/";
    }
}
