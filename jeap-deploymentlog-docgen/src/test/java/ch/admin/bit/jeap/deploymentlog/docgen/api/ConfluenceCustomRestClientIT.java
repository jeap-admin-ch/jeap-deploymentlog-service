package ch.admin.bit.jeap.deploymentlog.docgen.api;

import ch.admin.bit.jeap.deploymentlog.docgen.DocumentationGeneratorConfluenceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@EnableConfigurationProperties(value = DocumentationGeneratorConfluenceProperties.class)
class ConfluenceCustomRestClientIT {

    private final DocumentationGeneratorConfluenceProperties properties = new DocumentationGeneratorConfluenceProperties();

    @BeforeEach
    void setup(){
        properties.setUsername("replace");
        properties.setPassword("replace");
        properties.setUrl("https://<<yourConfluenceUrl>>");
    }

    @Test
    @Disabled("Only use manually to test the confluence api")
    void createBlogpost() {

        String content = """
<p>New version is released.</p>
<p>See changelog for details:</p>
<p>
  <ac:structured-macro ac:macro-id="625dd0f4-2228-4cfe-b558-650c047f61c2" ac:name="stashincludebyfilepath" ac:schema-version="1">
    <ac:parameter ac:name="repoSlug">qd-ui</ac:parameter>
    <ac:parameter ac:name="branchId">refs/heads/develop</ac:parameter>
    <ac:parameter ac:name="projectKey">EZQD</ac:parameter>
    <ac:parameter ac:name="filepath">CHANGELOG.md</ac:parameter>
    <ac:parameter ac:name="lineStart">1</ac:parameter>
    <ac:parameter ac:name="lineEnd">30</ac:parameter>
  </ac:structured-macro>
</p>""";


        ConfluenceCustomRestClient confluenceCustomRestClient = new ConfluenceCustomRestClient(properties, RestClient.builder());
        String blogpost = confluenceCustomRestClient.createBlogpost("ARCDOCTEST", "My First Blogpost from api", content);
        assertThat(blogpost).isNotNull();
    }
}
