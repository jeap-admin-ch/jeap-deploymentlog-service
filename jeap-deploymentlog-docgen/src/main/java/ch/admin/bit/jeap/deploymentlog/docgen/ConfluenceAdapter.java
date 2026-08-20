package ch.admin.bit.jeap.deploymentlog.docgen;

import org.sahli.asciidoc.confluence.publisher.client.http.RequestFailedException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.util.function.Supplier;

@Retryable(retryFor = RequestFailedException.class,
        maxAttempts = 4,
        backoff = @Backoff(delay = 2000, multiplier = 2))
public interface ConfluenceAdapter {
    /**
     * Creates the page if it does not exist yet, or updates it if its content has changed.
     * <p>
     * The content is passed as a supplier and not as a fixed value on purpose: if confluence rejects the update
     * because the page has been modified concurrently (HTTP 409), the content is rendered again from the current
     * state before the update is retried. Writing an already rendered snapshot with an incremented version number
     * would silently discard the concurrent modification.
     *
     * @return Page ID
     */
    String addOrUpdatePageUnderAncestor(String ancestorId, String pageName, Supplier<String> contentSupplier);

    void movePage(String ancestorId, String contentId);

    void deletePage(String pageId);

    void deletePageAndChildPages(String pageId);

    String createBlogpost(String spaceKey, String title, String content);

}
