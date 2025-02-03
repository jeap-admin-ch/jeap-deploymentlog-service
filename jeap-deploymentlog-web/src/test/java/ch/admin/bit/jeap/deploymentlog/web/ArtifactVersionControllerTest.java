package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.domain.ArtifactVersion;
import ch.admin.bit.jeap.deploymentlog.domain.ArtifactVersionService;
import ch.admin.bit.jeap.deploymentlog.web.api.ArtifactVersionController;
import ch.admin.bit.jeap.deploymentlog.web.api.dto.ArtifactVersionCreateDto;
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

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ArtifactVersionController.class, WebSecurityConfig.class})
@Import(ResourceServerProperties.class)
@AutoConfigureMockMvc
class ArtifactVersionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private ArtifactVersionService service;

    @Test
    void putNewArtifactVersion_userIsAuthorized_artifactCreated() throws Exception {

        final ArtifactVersionCreateDto artifactVersionCreateDto = generateCreateDto();

        mockMvc.perform(
                        put("/api/artifact-version/8efbe1a6-6a43-11ed-a1eb-0242ac120002")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(artifactVersionCreateDto))
                                .with(httpBasic("write", "secret")))
                .andDo(result -> java.lang.System.out.println(result.getResponse().getContentAsString()))
                .andExpect(status().isCreated());

        verify(service, times(1)).saveArtifactVersion(
                UUID.fromString("8efbe1a6-6a43-11ed-a1eb-0242ac120002"),
                artifactVersionCreateDto.getCoordinates(),
                artifactVersionCreateDto.getBuildJobLink());
    }

    @Test
    void putNewArtifactVersion_artifactVersionAlreadyExists_returnOK() throws Exception {

        final ArtifactVersionCreateDto artifactVersionCreateDto = generateCreateDto();

        when(service.findById(any())).thenReturn(Optional.of(mock(ArtifactVersion.class)));

        mockMvc.perform(
                        put("/api/artifact-version/8efbe1a6-6a43-11ed-a1eb-0242ac120002")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(artifactVersionCreateDto))
                                .with(httpBasic("write", "secret")))
                .andDo(result -> java.lang.System.out.println(result.getResponse().getContentAsString()))
                .andExpect(status().isOk());

        verify(service, never()).saveArtifactVersion(any(), any(), anyString());
    }

    @Test
    void putNewArtifactVersion_notAuthorized_returnForbidden() throws Exception {

        final ArtifactVersionCreateDto artifactVersionCreateDto = generateCreateDto();

        mockMvc.perform(
                        put("/api/artifact-version/8efbe1a6-6a43-11ed-a1eb-0242ac120002")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(artifactVersionCreateDto))
                                .with(httpBasic("read", "secret")))
                .andDo(result -> java.lang.System.out.println(result.getResponse().getContentAsString()))
                .andExpect(status().isForbidden());
    }

    private static ArtifactVersionCreateDto generateCreateDto(){
        final ArtifactVersionCreateDto artifactVersionCreateDto = new ArtifactVersionCreateDto();
        artifactVersionCreateDto.setBuildJobLink("myBuildJobLink");
        artifactVersionCreateDto.setCoordinates("1.2.3");
        return artifactVersionCreateDto;
    }


}
