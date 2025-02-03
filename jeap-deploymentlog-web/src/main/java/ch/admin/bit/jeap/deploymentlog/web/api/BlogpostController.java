package ch.admin.bit.jeap.deploymentlog.web.api;

import ch.admin.bit.jeap.deploymentlog.docgen.ConfluenceAdapter;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.BlogpostCreateDto;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/blogposts")
@RequiredArgsConstructor
@Slf4j
public class BlogpostController {

    private final ConfluenceAdapter confluenceAdapter;

    @PostMapping()
    @Operation(summary = "Create a new blogpost")
    @PreAuthorize("hasRole('deploymentlog-write')")
    public ResponseEntity<String> createBlogpost(@RequestBody BlogpostCreateDto blogpostCreateDto) {
        log.debug("Create new blogpost in confluence space with key '{}' with title '{}'", blogpostCreateDto.getSpaceKey(), blogpostCreateDto.getTitle());
        return ResponseEntity.status(HttpStatus.CREATED).body(confluenceAdapter.createBlogpost(blogpostCreateDto.getSpaceKey(), blogpostCreateDto.getTitle(), blogpostCreateDto.getContent()));
    }
}
