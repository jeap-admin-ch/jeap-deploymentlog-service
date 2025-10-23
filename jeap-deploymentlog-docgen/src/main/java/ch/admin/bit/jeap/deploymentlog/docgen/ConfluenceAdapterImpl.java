package ch.admin.bit.jeap.deploymentlog.docgen;

import ch.admin.bit.jeap.deploymentlog.docgen.api.ConfluenceCustomRestClient;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceClient;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluencePage;
import org.sahli.asciidoc.confluence.publisher.client.http.NotFoundException;
import org.sahli.asciidoc.confluence.publisher.client.http.RequestFailedException;
import org.springframework.util.StringUtils;

import java.util.List;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

@RequiredArgsConstructor
@Slf4j
class ConfluenceAdapterImpl implements ConfluenceAdapter {
    static final String CONTENT_HASH_PROPERTY_KEY = "content-hash";
    private static final String VERSION_MESSAGE = "Documentation generated";

    private final ConfluenceClient confluenceClient;
    private final DocumentationGeneratorConfluenceProperties props;
    private final ConfluenceCustomRestClient confluenceCustomRestClient;

    private static boolean notSameHash(String actualHash, String newHash) {
        return actualHash == null || !actualHash.equals(newHash);
    }

    private static String hash(String content) {
        return sha256Hex(content);
    }

    @Override
    public String getPageByName(String pageName) {
        return confluenceClient.getPageByTitle(props.getSpaceKey(), pageName);
    }

    @Override
    public String addOrUpdatePageUnderAncestor(String ancestorId, String pageName, String content) {
        String contentId;
        try {
            contentId = confluenceClient.getPageByTitle(props.getSpaceKey(), pageName);
            updatePage(contentId, ancestorId, pageName, content);
        } catch (NotFoundException e) {
            log.info("Creating page {}", pageName);
            contentId = confluenceClient.addPageUnderAncestor(props.getSpaceKey(), ancestorId, pageName, content, VERSION_MESSAGE);
            confluenceClient.setPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY, hash(content));
        }

        return contentId;
    }

    @Override
    public void movePage(String ancestorId, String contentId) {
        try {
            ConfluencePage existingPage = confluenceClient.getPageWithContentAndVersionById(contentId);
            log.info("Moving page {}", existingPage.getTitle());
            updatePageWithRetryOnConflict(contentId, ancestorId, existingPage.getTitle(), existingPage.getContent(), existingPage);
        } catch (RequestFailedException rfe) {
            if (StringUtils.hasText(rfe.getMessage()) && rfe.getMessage().contains("response: 404")) {
                log.info("Page with id {} not found. Ignoring...", contentId);
            } else {
                throw rfe;
            }

        }
    }

    private void updatePage(String contentId, String ancestorId, String pageName, String content) {
        ConfluencePage existingPage = confluenceClient.getPageWithContentAndVersionById(contentId);
        String existingContentHash = confluenceClient.getPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY);
        String newContentHash = hash(content);

        if (notSameHash(existingContentHash, newContentHash) || !existingPage.getTitle().equals(pageName)) {
            log.info("Updating page {}", pageName);
            updatePageWithRetryOnConflict(contentId, ancestorId, pageName, content, existingPage);
            confluenceClient.deletePropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY);
            confluenceClient.setPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY, newContentHash);
        } else {
            log.info("Page {} is up-to-date", pageName);
        }
    }

    private void updatePageWithRetryOnConflict(String contentId, String ancestorId, String pageName, String content, ConfluencePage existingPage) {
        for (int retries = 0; ; retries++) {
            try {
                int newPageVersion = existingPage.getVersion() + 1;
                confluenceClient.updatePage(contentId, ancestorId, pageName, content, newPageVersion, VERSION_MESSAGE);
                return; // success
            } catch (RequestFailedException rfe) {
                if (rfe.getMessage().contains("response: 409") && retries < 2) {
                    log.warn("Failed to update page content for page {} - will try again in {}ms ({})",
                            contentId, props.getRetryOnConflictWaitDuration().toMillis(), rfe.getMessage());
                    waitForRetry();
                    existingPage = confluenceClient.getPageWithContentAndVersionById(contentId);
                } else {
                    throw rfe;
                }
            }
        }
    }

    @SneakyThrows
    private void waitForRetry() {
        Thread.sleep(props.getRetryOnConflictWaitDuration().toMillis());
    }

    @Override
    public void deletePage(String pageId) {
        try {
            confluenceClient.deletePage(pageId);
        } catch (RequestFailedException ex) {
            String message = ex.getMessage();
            // See https://docs.atlassian.com/atlassian-confluence/REST/6.5.2/#content-delete for response codes
            // See RequestFailedException#RequestFailedException() - status code is only available in message
            if (message.contains("response: 404")) {
                log.info("Page {} does not exist, already deleted (status code 404)", pageId);
            } else {
                throw ex;
            }
        }
    }

    @Override
    public void deletePageAndChildPages(String pageId) {
        List<String> childPages = confluenceClient.getChildPages(pageId).stream().map(ConfluencePage::getContentId).toList();
        log.info("Found {} childPages of {} to delete", childPages.size(), pageId);
        childPages.forEach(this::deletePageAndChildPages);
        log.info("Delete page with id {}", pageId);
        deletePage(pageId);
    }

    @Override
    public String createBlogpost(String spaceKey, String title, String content) {
        return confluenceCustomRestClient.createBlogpost(spaceKey, title, content);
    }
}
