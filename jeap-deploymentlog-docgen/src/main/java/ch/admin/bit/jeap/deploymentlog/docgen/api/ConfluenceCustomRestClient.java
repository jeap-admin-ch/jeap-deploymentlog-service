package ch.admin.bit.jeap.deploymentlog.docgen.api;

import ch.admin.bit.jeap.deploymentlog.docgen.DocumentationGeneratorConfluenceProperties;
import ch.admin.bit.jeap.deploymentlog.docgen.api.dto.ConfluenceApiResponseDto;
import ch.admin.bit.jeap.deploymentlog.docgen.api.dto.ConfluenceBodyDto;
import ch.admin.bit.jeap.deploymentlog.docgen.api.dto.ConfluenceSpaceDto;
import ch.admin.bit.jeap.deploymentlog.docgen.api.dto.ConfluenceStorageDto;
import ch.admin.bit.jeap.deploymentlog.docgen.api.dto.CreateBlogpostDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

@Slf4j
public class ConfluenceCustomRestClient {

    private final RestClient restClient;

    public ConfluenceCustomRestClient(DocumentationGeneratorConfluenceProperties props, RestClient.Builder restClientBuilder) {
        ClientHttpRequestFactory timeoutRequestFactory = ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                .withReadTimeout(Duration.ofSeconds(10)));
        this.restClient = restClientBuilder
                .requestFactory(timeoutRequestFactory)
                .defaultHeaders(header -> header.setBasicAuth(props.getUsername(), props.getPassword()))
                .baseUrl(
                        UriComponentsBuilder
                                .fromHttpUrl(props.getUrl())
                                .pathSegment("rest", "api", "content")
                                .build()
                                .toString())
                .build();
    }

    public String createBlogpost(String spaceKey, String title, String content) {
        log.debug("Call confluence api to create blogpost with title '{}' in space '{}'", title, spaceKey);

        CreateBlogpostDto createBlogpostDto = CreateBlogpostDto.builder()
                .type("blogpost")
                .space(ConfluenceSpaceDto.builder().key(spaceKey).build())
                .title(title)
                .body(ConfluenceBodyDto.builder().storage(
                        ConfluenceStorageDto.builder()
                                .value(content)
                                .representation("storage")
                                .build()
                ).build())
                .build();

        final ConfluenceApiResponseDto response = restClient
                .post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(createBlogpostDto)
                .retrieve()
                .body(ConfluenceApiResponseDto.class);

        if (response != null) {
            var links = response.getConfluenceApiLinks();
            var blogpostLink = String.join("", links.getBase(), links.getTinyui());
            log.info("Blogpost created with link '{}'", blogpostLink);
            return blogpostLink;
        }

        throw new IllegalStateException("Response is null");
    }
}
