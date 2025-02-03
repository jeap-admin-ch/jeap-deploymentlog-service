package ch.admin.bit.jeap.deploymentlog.docgen;

import org.sahli.asciidoc.confluence.publisher.client.http.RequestFailedException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

@Retryable(retryFor = RequestFailedException.class,
        maxAttempts = 4,
        backoff = @Backoff(delay = 2000, multiplier = 2))
public interface ConfluenceAdapter {
    /**
     * @return Page ID
     */
    String getPageByName(String pageName);

    /**
     * @return Page ID
     */
    String addOrUpdatePageUnderAncestor(String ancestorId, String pageName, String content);

    void movePage(String ancestorId, String contentId);

    void deletePage(String pageId);

    void deletePageAndChildPages(String pageId);

    String createBlogpost(String spaceKey, String title, String content);

}
