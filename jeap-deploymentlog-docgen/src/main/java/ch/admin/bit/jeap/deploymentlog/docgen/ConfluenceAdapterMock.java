package ch.admin.bit.jeap.deploymentlog.docgen;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

@Getter
@Slf4j
public class ConfluenceAdapterMock implements ConfluenceAdapter {

    private final Set<String> modifiedPages = new ConcurrentSkipListSet<>();

    @Override
    public String getPageByName(String pageName) {
        int fakePageId = pageName.hashCode();
        log.info("Get page by name: pageName={} fakePageId={}", pageName, fakePageId);
        modifiedPages.add(pageName);
        return String.valueOf(fakePageId);
    }

    @Override
    public String addOrUpdatePageUnderAncestor(String ancestorId, String pageName, String content) {
        int fakePageId = (ancestorId + pageName).hashCode();
        log.info("Add or update page: ancestorId={} pageName={} fakePageId={}", ancestorId, pageName, fakePageId);
        modifiedPages.add(pageName);
        return String.valueOf(fakePageId);
    }

    @Override
    public void movePage(String ancestorId, String contentId) {
        log.info("Move page: contentId={} to ancestorId={}", contentId, ancestorId);
        modifiedPages.add(contentId);
    }

    @Override
    public void deletePage(String pageId) {
        log.info("Mock delete page {}", pageId);
    }

    @Override
    public void deletePageAndChildPages(String pageId) {
        log.info("Mock delete page and child pages {}", pageId);
    }

    @Override
    public String createBlogpost(String spaceKey, String title, String content) {
        log.debug("MOCK create blogpost with title '{}' in space '{}'", title, spaceKey);
        return "http://localhost/my+blog+mock";
    }
}
