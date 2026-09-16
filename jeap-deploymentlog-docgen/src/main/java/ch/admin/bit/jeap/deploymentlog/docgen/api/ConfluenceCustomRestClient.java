package ch.admin.bit.jeap.deploymentlog.docgen.api;

import ch.admin.bit.jeap.deploymentlog.docgen.DocumentationGeneratorConfluenceProperties;
import ch.admin.bit.jeap.deploymentlog.docgen.api.dto.ConfluenceApiResponseDto;
import ch.admin.bit.jeap.deploymentlog.docgen.api.dto.ConfluenceBodyDto;
import ch.admin.bit.jeap.deploymentlog.docgen.api.dto.ConfluenceSpaceDto;
import ch.admin.bit.jeap.deploymentlog.docgen.api.dto.ConfluenceStorageDto;
import ch.admin.bit.jeap.deploymentlog.docgen.api.dto.CreateBlogpostDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
public class ConfluenceCustomRestClient {

    private final RestClient restClient;

    public ConfluenceCustomRestClient(DocumentationGeneratorConfluenceProperties props, RestClient.Builder restClientBuilder) {
        this(props, restClientBuilder, timeoutRequestFactory());
    }

    ConfluenceCustomRestClient(DocumentationGeneratorConfluenceProperties props,
                               RestClient.Builder restClientBuilder,
                               ClientHttpRequestFactory requestFactory) {
        RestClient.Builder configuredBuilder = requestFactory == null
                ? restClientBuilder
                : restClientBuilder.requestFactory(requestFactory);
        this.restClient = configuredBuilder
                .defaultHeaders(header -> header.setBasicAuth(props.getUsername(), props.getPassword()))
                .baseUrl(
                        UriComponentsBuilder
                                .fromUriString(props.getUrl())
                                .pathSegment("rest", "api", "content")
                                .build()
                                .toString())
                .build();
    }

    private static ClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory timeoutRequestFactory = new SimpleClientHttpRequestFactory();
        timeoutRequestFactory.setReadTimeout(Duration.ofSeconds(10));
        return timeoutRequestFactory;
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

    public Optional<String> findPageIdByTitle(String spaceKey, String title) {
        PageSearchResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("type", "page")
                        .queryParam("spaceKey", spaceKey)
                        .queryParam("title", title)
                        .build())
                .retrieve()
                .body(PageSearchResponse.class);
        if (response == null || response.results() == null || response.results().isEmpty()) {
            return Optional.empty();
        }
        if (response.results().size() > 1) {
            throw new IllegalStateException("Multiple Confluence pages found with title '" + title + "'");
        }
        return Optional.ofNullable(response.results().getFirst().id());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PageSearchResponse(List<PageSearchResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PageSearchResult(String id) {
    }
}
