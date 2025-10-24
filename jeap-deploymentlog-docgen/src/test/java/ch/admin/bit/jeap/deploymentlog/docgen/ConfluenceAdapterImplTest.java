package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.api.ConfluenceCustomRestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceClient;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluencePage;
import org.sahli.asciidoc.confluence.publisher.client.http.NotFoundException;
import org.sahli.asciidoc.confluence.publisher.client.http.RequestFailedException;

import java.time.Duration;
import java.util.List;

import static ch.admin.bit.jeap.deploymentlog.docgen.ConfluenceAdapterImpl.CONTENT_HASH_PROPERTY_KEY;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfluenceAdapterImplTest {

    private static final String SPACE_KEY = "SPACE_KEY";

    @Mock
    ConfluenceClient confluenceClientMock;

    @Mock
    ConfluenceCustomRestClient confluenceClientImplMock;

    private ConfluenceAdapterImpl confluenceAdapter;

    @Test
    void addOrUpdatePageUnderAncestor_newPage() {
        String pageId = "pageId";
        String pageName = "pageName";
        String ancestorId = "ancestorId";
        String content = "content";
        String contentHash = sha256Hex(content);
        doThrow(new NotFoundException()).when(confluenceClientMock).getPageByTitle(SPACE_KEY, pageName);
        doReturn(pageId)
                .when(confluenceClientMock)
                .addPageUnderAncestor(eq(SPACE_KEY), eq(ancestorId), eq(pageName), eq(content), anyString());

        confluenceAdapter.addOrUpdatePageUnderAncestor(ancestorId, pageName, content);

        verify(confluenceClientMock).setPropertyByKey(pageId, CONTENT_HASH_PROPERTY_KEY, contentHash);

    }

    @Test
    void addOrUpdatePageUnderAncestor_existingPageWithNewContent() {
        String pageId = "pageId";
        String pageName = "pageName";
        String ancestorId = "ancestorId";
        String content = "content";
        String contentHash = sha256Hex(content);
        int version = 1;
        ConfluencePage existingPage = new ConfluencePage(pageId, pageName, version);
        doReturn(pageId)
                .when(confluenceClientMock).getPageByTitle(SPACE_KEY, pageName);
        doReturn(existingPage)
                .when(confluenceClientMock).getPageWithContentAndVersionById(pageId);
        doReturn("differenthash")
                .when(confluenceClientMock).getPropertyByKey(pageId, CONTENT_HASH_PROPERTY_KEY);

        confluenceAdapter.addOrUpdatePageUnderAncestor(ancestorId, pageName, content);

        verify(confluenceClientMock).updatePage(eq(pageId), eq(ancestorId), eq(pageName), eq(content), eq(version + 1), anyString());
        verify(confluenceClientMock).setPropertyByKey(pageId, CONTENT_HASH_PROPERTY_KEY, contentHash);
    }

    @Test
    void addOrUpdatePageUnderAncestor_existingPageWithNewContent_retryOnConflict() {
        String pageId = "pageId";
        String pageName = "pageName";
        String ancestorId = "ancestorId";
        String content = "content";
        String contentHash = sha256Hex(content);
        int version1 = 1;
        int version2 = 2;
        RequestFailedException ex = mock(RequestFailedException.class);
        doReturn("request failed (request: PUT https://confluence-test.com/rest/api/content/361257046 , response: 409")
                .when(ex).getMessage();

        ConfluencePage existingPageV1 = new ConfluencePage(pageId, pageName, version1);
        ConfluencePage existingPageV2 = new ConfluencePage(pageId, pageName, version2);

        doReturn(pageId)
                .when(confluenceClientMock).getPageByTitle(SPACE_KEY, pageName);
        doReturn(existingPageV1)
                .doReturn(existingPageV2)
                .when(confluenceClientMock).getPageWithContentAndVersionById(pageId);
        doReturn("differenthash")
                .when(confluenceClientMock).getPropertyByKey(pageId, CONTENT_HASH_PROPERTY_KEY);
        doThrow(ex) // First attempt fails
                .doNothing() // Second attempt succeeds
                .when(confluenceClientMock).updatePage(anyString(), anyString(), anyString(), anyString(), anyInt(), anyString());

        confluenceAdapter.addOrUpdatePageUnderAncestor(ancestorId, pageName, content);

        verify(confluenceClientMock).updatePage(eq(pageId), eq(ancestorId), eq(pageName), eq(content), eq(version1 + 1), anyString());
        verify(confluenceClientMock).updatePage(eq(pageId), eq(ancestorId), eq(pageName), eq(content), eq(version2 + 1), anyString());
        verify(confluenceClientMock).setPropertyByKey(pageId, CONTENT_HASH_PROPERTY_KEY, contentHash);
    }

    @Test
    void addOrUpdatePageUnderAncestor_existingPageWithNewContent_retryOnConflictAndFailIfProblemPersists() {
        String pageId = "pageId";
        String pageName = "pageName";
        String ancestorId = "ancestorId";
        String content = "content";
        int version = 1;
        RequestFailedException ex = mock(RequestFailedException.class);
        doReturn("request failed (request: PUT https://confluence-test.com/rest/api/content/361257046 , response: 409")
                .when(ex).getMessage();

        ConfluencePage existingPage = new ConfluencePage(pageId, pageName, version);

        doReturn(pageId)
                .when(confluenceClientMock).getPageByTitle(SPACE_KEY, pageName);
        doReturn(existingPage)
                .when(confluenceClientMock).getPageWithContentAndVersionById(pageId);
        doReturn("differenthash")
                .when(confluenceClientMock).getPropertyByKey(pageId, CONTENT_HASH_PROPERTY_KEY);
        doThrow(ex)
                .when(confluenceClientMock).updatePage(anyString(), anyString(), anyString(), anyString(), anyInt(), anyString());

        assertThrows(RequestFailedException.class, () ->
                confluenceAdapter.addOrUpdatePageUnderAncestor(ancestorId, pageName, content));

        verify(confluenceClientMock, times(3))
                .updatePage(eq(pageId), eq(ancestorId), eq(pageName), eq(content), eq(version + 1), anyString());
    }

    @Test
    void addOrUpdatePageUnderAncestor_existingPageNoContentChange() {
        String pageId = "pageId";
        String pageName = "pageName";
        String ancestorId = "ancestorId";
        String content = "content";
        String contentHash = sha256Hex(content);
        int version = 1;
        ConfluencePage existingPage = new ConfluencePage(pageId, pageName, version);
        doReturn(pageId)
                .when(confluenceClientMock).getPageByTitle(SPACE_KEY, pageName);
        doReturn(existingPage)
                .when(confluenceClientMock).getPageWithContentAndVersionById(pageId);
        doReturn(contentHash)
                .when(confluenceClientMock).getPropertyByKey(pageId, CONTENT_HASH_PROPERTY_KEY);

        confluenceAdapter.addOrUpdatePageUnderAncestor(ancestorId, pageName, content);

        verifyNoMoreInteractions(confluenceClientMock);
    }

    @Test
    void delete_success() {
        String pageId = "pageId";

        confluenceAdapter.deletePage(pageId);

        verify(confluenceClientMock).deletePage(pageId);
        verifyNoMoreInteractions(confluenceClientMock);
    }

    @Test
    void delete_idempotent() {
        String pageId = "pageId";
        RequestFailedException ex = mock(RequestFailedException.class);
        doReturn("prefix response: 404 postfix").when(ex).getMessage();
        doThrow(ex).when(confluenceClientMock).deletePage(pageId);

        assertDoesNotThrow(() -> confluenceAdapter.deletePage(pageId));
    }

    @Test
    void delete_fails() {
        String pageId = "pageId";
        RequestFailedException ex = mock(RequestFailedException.class);
        doReturn("prefix response: 500 postfix").when(ex).getMessage();
        doThrow(ex).when(confluenceClientMock).deletePage(pageId);

        assertThrows(RequestFailedException.class, () -> confluenceAdapter.deletePage(pageId));
    }

    @Test
    void movePage() {
        String pageId = "pageId";
        String pageName = "pageName";
        String ancestorId = "ancestorId";
        String content = "content";
        int version = 1;
        ConfluencePage existingPage = mock(ConfluencePage.class);
        when(existingPage.getTitle()).thenReturn(pageName);
        when(existingPage.getContent()).thenReturn(content);
        when(existingPage.getVersion()).thenReturn(version);
        doReturn(existingPage).when(confluenceClientMock).getPageWithContentAndVersionById(pageId);

        confluenceAdapter.movePage(ancestorId, pageId);

        verify(confluenceClientMock).updatePage(pageId, ancestorId, pageName, content, version + 1, "Documentation generated");
    }

    @Test
    void movePage_requestFailedException404() {
        String pageId = "pageId";
        String ancestorId = "ancestorId";
        RequestFailedException rfe = mock(RequestFailedException.class);
        when(rfe.getMessage()).thenReturn("request failed (request: GET https://some-confluence-server.com/rest/api/content/954415933?expand=body.storage,version <empty body>, response: 404  {\"statusCode\":404,\"data\":{\"authorized\":false,\"valid\":true,\"allowedInReadOnlyMode\":true,\"errors\":[],\"successful\":false},\"message\":\"No content found with id: ContentId{id=954415933}\",\"reason\":\"Not Found\"})");
        doThrow(rfe).when(confluenceClientMock).getPageWithContentAndVersionById(pageId);

        assertDoesNotThrow(() -> confluenceAdapter.movePage(ancestorId, pageId));
        verify(confluenceClientMock).getPageWithContentAndVersionById(pageId);
        verifyNoMoreInteractions(confluenceClientMock);
    }

    @Test
    void movePage_requestFailedExceptionEmptyMessage() {
        String pageId = "pageId";
        String ancestorId = "ancestorId";
        RequestFailedException rfe = mock(RequestFailedException.class);
        when(rfe.getMessage()).thenReturn("");
        doThrow(rfe).when(confluenceClientMock).getPageWithContentAndVersionById(pageId);

        assertThrows(RequestFailedException.class, () -> confluenceAdapter.movePage(ancestorId, pageId));
        verify(confluenceClientMock).getPageWithContentAndVersionById(pageId);
        verifyNoMoreInteractions(confluenceClientMock);
    }

    @Test
    void deletePageAndChildPages() {
        String parentPageId = "parentPageId";
        String childPage1Id = "childPage1Id";
        String childPage1bId = "childPage1bId";
        String childPage2Id = "childPage2Id";
        ConfluencePage childPage1 = mock(ConfluencePage.class);
        when(childPage1.getContentId()).thenReturn(childPage1Id);
        ConfluencePage childPage1b = mock(ConfluencePage.class);
        when(childPage1b.getContentId()).thenReturn(childPage1bId);
        ConfluencePage childPage2 = mock(ConfluencePage.class);
        when(childPage2.getContentId()).thenReturn(childPage2Id);
        when(confluenceClientMock.getChildPages(parentPageId)).thenReturn(List.of(childPage1, childPage1b));
        when(confluenceClientMock.getChildPages(childPage1Id)).thenReturn(List.of(childPage2));

        confluenceAdapter.deletePageAndChildPages(parentPageId);

        verify(confluenceClientMock).deletePage(parentPageId);
        verify(confluenceClientMock).deletePage(childPage1Id);
        verify(confluenceClientMock).deletePage(childPage1bId);
        verify(confluenceClientMock).deletePage(childPage2Id);
        verify(confluenceClientMock).getChildPages(parentPageId);
        verify(confluenceClientMock).getChildPages(childPage1Id);
        verify(confluenceClientMock).getChildPages(childPage1bId);
        verify(confluenceClientMock).getChildPages(childPage2Id);
        verifyNoMoreInteractions(confluenceClientMock);
    }

    @BeforeEach
    void setUp() {
        DocumentationGeneratorConfluenceProperties props = new DocumentationGeneratorConfluenceProperties();
        props.setSpaceKey(SPACE_KEY);
        props.setRetryOnConflictWaitDuration(Duration.ofMillis(1));
        confluenceAdapter = new ConfluenceAdapterImpl(confluenceClientMock, props, confluenceClientImplMock);
    }
}
