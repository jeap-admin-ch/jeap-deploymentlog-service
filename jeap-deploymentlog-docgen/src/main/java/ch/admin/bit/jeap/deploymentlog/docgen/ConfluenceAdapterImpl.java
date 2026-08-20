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
import java.util.function.Function;
import java.util.function.Supplier;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

@RequiredArgsConstructor
@Slf4j
class ConfluenceAdapterImpl implements ConfluenceAdapter {
    static final String CONTENT_HASH_PROPERTY_KEY = "content-hash";
    private static final String VERSION_MESSAGE = "Documentation generated";
    private static final String NOT_FOUND_RESPONSE = "response: 404";
    private static final String CONFLICT_RESPONSE = "response: 409";
    private static final int MAX_CONFLICT_RETRIES = 2;

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
    public String addOrUpdatePageUnderAncestor(String ancestorId, String pageName, Supplier<String> contentSupplier) {
        String contentId;
        try {
            contentId = confluenceClient.getPageByTitle(props.getSpaceKey(), ancestorId, pageName);
            updatePage(contentId, ancestorId, pageName, contentSupplier);
        } catch (NotFoundException e) {
            log.info("Creating page {}", pageName);
            String content = contentSupplier.get();
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
            // The page content is not modified by a move - on conflict, keep the content the concurrent writer stored
            updatePageWithRetryOnConflict(contentId, ancestorId, existingPage.getTitle(), existingPage.getContent(),
                    existingPage, ConfluencePage::getContent);
        } catch (RequestFailedException rfe) {
            if (isNotFound(rfe)) {
                log.info("Page with id {} not found. Ignoring...", contentId);
            } else {
                throw rfe;
            }

        }
    }

    private void updatePage(String contentId, String ancestorId, String pageName, Supplier<String> contentSupplier) {
        ConfluencePage existingPage = confluenceClient.getPageWithContentAndVersionById(contentId);
        String existingContentHash = confluenceClient.getPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY);
        String content = contentSupplier.get();

        if (notSameHash(existingContentHash, hash(content)) || !existingPage.getTitle().equals(pageName)) {
            log.info("Updating page {}", pageName);
            // On conflict, render the content again to include the change of the concurrent writer
            String updatedContent = updatePageWithRetryOnConflict(contentId, ancestorId, pageName, content,
                    existingPage, page -> contentSupplier.get());
            confluenceClient.deletePropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY);
            confluenceClient.setPropertyByKey(contentId, CONTENT_HASH_PROPERTY_KEY, hash(updatedContent));
        } else {
            log.info("Page {} is up-to-date", pageName);
        }
    }

    /**
     * Updates a page, retrying on a version conflict (HTTP 409) caused by a concurrent update of the same page.
     *
     * @param content           the content to write on the first attempt
     * @param contentOnConflict provides the content to write after a conflict, based on the page as it has been
     *                          re-read from confluence. Must not just return {@code content} again: writing an
     *                          outdated snapshot with an incremented version number would discard the change of the
     *                          concurrent writer without any error being reported.
     *                          <p>
     *                          Note that the callers render inside the transaction of the docgen run, so a re-render
     *                          reliably picks up rows another instance has inserted meanwhile, while changes to
     *                          entities the persistence context has already loaded are not refreshed.
     * @return the content that has been written to the page
     */
    private String updatePageWithRetryOnConflict(String contentId, String ancestorId, String pageName, String content,
                                                 ConfluencePage existingPage, Function<ConfluencePage, String> contentOnConflict) {
        ConfluencePage currentPage = existingPage;
        String currentContent = content;
        for (int retries = 0; ; retries++) {
            try {
                int newPageVersion = currentPage.getVersion() + 1;
                confluenceClient.updatePage(contentId, ancestorId, pageName, currentContent, newPageVersion, VERSION_MESSAGE, true);
                return currentContent; // success
            } catch (RequestFailedException rfe) {
                if (isVersionConflict(rfe) && retries < MAX_CONFLICT_RETRIES) {
                    log.warn("Failed to update page content for page {} - will re-read the page and try again in {}ms ({})",
                            contentId, props.getRetryOnConflictWaitDuration().toMillis(), rfe.getMessage());
                    waitForRetry();
                    currentPage = confluenceClient.getPageWithContentAndVersionById(contentId);
                    currentContent = contentOnConflict.apply(currentPage);
                } else {
                    throw rfe;
                }
            }
        }
    }

    private static boolean isVersionConflict(RequestFailedException rfe) {
        return StringUtils.hasText(rfe.getMessage()) && rfe.getMessage().contains(CONFLICT_RESPONSE);
    }

    private static boolean isNotFound(RequestFailedException rfe) {
        return StringUtils.hasText(rfe.getMessage()) && rfe.getMessage().contains(NOT_FOUND_RESPONSE);
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
            // See https://docs.atlassian.com/atlassian-confluence/REST/6.5.2/#content-delete for response codes
            // See RequestFailedException#RequestFailedException() - status code is only available in message
            if (isNotFound(ex)) {
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
