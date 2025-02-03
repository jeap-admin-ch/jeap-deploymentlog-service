package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.docgen.ConfluenceAdapter;
import ch.admin.bit.jeap.deploymentlog.web.api.BlogpostController;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.BlogpostCreateDto;
import ch.admin.bit.jeap.deploymentlog.web.config.WebSecurityConfig;
import ch.admin.bit.jeap.security.resource.properties.ResourceServerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {BlogpostController.class, WebSecurityConfig.class})
@Import(ResourceServerProperties.class)
@AutoConfigureMockMvc
class BlogpostControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private ConfluenceAdapter confluenceAdapter;

    @Test
    void createBlogpost_writeRole_thenReturnsCreated() throws Exception {
        BlogpostCreateDto dto = new BlogpostCreateDto();
        dto.setSpaceKey("mySpaceKey");
        dto.setTitle("myTitle");
        dto.setContent("myContent");

        mockMvc.perform(
                        post("/api/blogposts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto))
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isCreated());

        verify(confluenceAdapter, times(1)).createBlogpost(dto.getSpaceKey(), dto.getTitle(), dto.getContent());
    }

    @Test
    void createBlogpost_noWriteRole_thenReturnsForbidden() throws Exception {
        BlogpostCreateDto dto = new BlogpostCreateDto();
        dto.setSpaceKey("mySpaceKey");
        dto.setTitle("myTitle");
        dto.setContent("myContent");

        mockMvc.perform(
                        post("/api/blogposts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto))
                                .with(httpBasic("read", "secret")))
                .andExpect(status().isForbidden());

        verify(confluenceAdapter, never()).createBlogpost(anyString(), anyString(), anyString());
    }

}
