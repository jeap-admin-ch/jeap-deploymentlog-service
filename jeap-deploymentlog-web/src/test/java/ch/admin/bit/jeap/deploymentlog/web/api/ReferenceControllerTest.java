package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.domain.ReferenceService;
import ch.admin.bit.jeap.deploymentlog.domain.ReferenceType;
import ch.admin.bit.jeap.deploymentlog.web.api.ReferenceController;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.ReferenceDto;
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

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ReferenceController.class, WebSecurityConfig.class})
@Import(ResourceServerProperties.class)
@AutoConfigureMockMvc
class ReferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private ReferenceService referenceService;

    @Test
    void saveNewReference_whenNotExists_thenReturnsCreated() throws Exception {
        UUID referenceId = UUID.randomUUID();
        ReferenceDto referenceDto = ReferenceDto.builder()
                .id(referenceId)
                .type(ReferenceType.BUILD_JOB_LINK_BY_GIT_URL_AND_VERSION)
                .referenceIdentifier("identifier")
                .uri("http://example.com").build();

        when(referenceService.referenceExistsById(referenceId)).thenReturn(false);

        mockMvc.perform(
                        put("/api/reference/{id}", referenceId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(referenceDto))
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isCreated());

        verify(referenceService, times(1)).referenceExistsById(referenceId);
        verify(referenceService, times(1)).saveReference(
                referenceId, referenceDto.getType(), referenceDto.getReferenceIdentifier(),
                referenceDto.getUri());
    }

    @Test
    void saveNewReference_whenExists_thenReturnsOk() throws Exception {
        UUID referenceId = UUID.randomUUID();
        ReferenceDto referenceDto = ReferenceDto.builder()
                .id(referenceId)
                .type(ReferenceType.BUILD_JOB_LINK_BY_GIT_URL_AND_VERSION)
                .referenceIdentifier("identifier")
                .uri("http://example.com").build();

        when(referenceService.referenceExistsById(referenceId)).thenReturn(true);

        mockMvc.perform(
                        put("/api/reference/{id}", referenceId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(referenceDto))
                                .with(httpBasic("write", "secret")))
                .andExpect(status().isOk());

        verify(referenceService, times(1)).referenceExistsById(referenceId);
        verify(referenceService, never()).saveReference(
                any(), any(), any(), any());
    }

    @Test
    void saveNewReference_noWriteRole_thenReturnsForbidden() throws Exception {
        UUID referenceId = UUID.randomUUID();
        ReferenceDto referenceDto = ReferenceDto.builder()
                .id(referenceId)
                .type(ReferenceType.BUILD_JOB_LINK_BY_GIT_URL_AND_VERSION)
                .referenceIdentifier("identifier")
                .uri("http://example.com").build();

        mockMvc.perform(
                        put("/api/reference/{id}", referenceId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(referenceDto))
                                .with(httpBasic("read", "secret")))
                .andExpect(status().isForbidden());

        verify(referenceService, never()).referenceExistsById(referenceId);
        verify(referenceService, never()).saveReference(
                any(), any(), any(), any());
    }
}
