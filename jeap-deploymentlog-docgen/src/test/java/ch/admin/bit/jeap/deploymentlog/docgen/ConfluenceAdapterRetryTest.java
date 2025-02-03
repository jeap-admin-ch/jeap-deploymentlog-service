package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.api.ConfluenceCustomRestClient;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceClient;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluencePage;
import org.sahli.asciidoc.confluence.publisher.client.http.RequestFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = DocumentationGeneratorConfig.class)
@Import(ConfluenceAdapterRetryTest.TestConfig.class)
class ConfluenceAdapterRetryTest {

    @MockBean
    private ConfluenceClient confluenceClientMock;
    @MockBean
    private LockProvider lockProviderMock;
    @MockBean
    private MeterRegistry meterRegistryMock;
    @Mock
    private ConfluencePage pageMock;
    @Autowired
    private ConfluenceAdapter confluenceAdapter;

    @TestConfiguration
    static class TestConfig {

        @Bean
        ConfluenceAdapter confluenceAdapterForTest(ConfluenceClient confluenceClient) {
            DocumentationGeneratorConfluenceProperties props = new DocumentationGeneratorConfluenceProperties();
            return new ConfluenceAdapterImpl(confluenceClient, props, mock(ConfluenceCustomRestClient.class));
        }

    }

    @Test
    void addOrUpdatePageUnderAncestor_successAfterRetry() {
        when(confluenceClientMock.getPageByTitle(any(), any()))
                // First two call fail
                .thenThrow(RequestFailedException.class)
                .thenThrow(RequestFailedException.class)
                // Third call succeeds
                .thenReturn("1234");
        when(confluenceClientMock.getPageWithContentAndVersionById(any()))
                .thenReturn(pageMock);
        when(pageMock.getTitle())
                .thenReturn("pageName");

        confluenceAdapter.addOrUpdatePageUnderAncestor("ancestorId", "pageName", "content");

        verify(confluenceClientMock).updatePage(any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void getPageByName_successAfterRetry() {
        when(confluenceClientMock.getPageByTitle(any(), any()))
                // First call fails
                .thenThrow(RequestFailedException.class)
                .thenThrow(RequestFailedException.class)
                // Third call succeeds
                .thenReturn("1234");

        String result = confluenceAdapter.getPageByName("name");

        assertEquals("1234", result);
    }

    @Test
    void deletePage_successAfterRetry() {
        RequestFailedException rfe = mock(RequestFailedException.class);
        when(rfe.getMessage()).thenReturn("message");

        // First fail ...
        doThrow(rfe)
                // ... then succeed
                .doNothing()
                .when(confluenceClientMock).deletePage(any());

        assertDoesNotThrow(() ->
                confluenceAdapter.deletePage("name"));
    }
}
