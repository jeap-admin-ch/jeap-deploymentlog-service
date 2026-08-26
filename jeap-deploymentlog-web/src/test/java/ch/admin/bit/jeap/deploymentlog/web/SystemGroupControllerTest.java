package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.SystemGroup;
import ch.admin.bit.jeap.deploymentlog.domain.SystemGroupService;
import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidSystemGroupNameException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemGroupNameAlreadyExistsException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemGroupNotFoundException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemNotFoundByIdException;
import ch.admin.bit.jeap.deploymentlog.web.api.SystemGroupController;
import ch.admin.bit.jeap.deploymentlog.web.config.WebSecurityConfig;
import ch.admin.bit.jeap.security.resource.properties.ResourceServerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SystemGroupController.class)
@Import({WebSecurityConfig.class, ResourceServerProperties.class})
@AutoConfigureMockMvc
class SystemGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SystemGroupService systemGroupService;

    @Test
    void readerCanListAndReadGroups() throws Exception {
        SystemGroup group = new SystemGroup("Border Control");
        System system = new System("border-system");
        group.getSystems().add(system);
        when(systemGroupService.findAll()).thenReturn(List.of(group));
        when(systemGroupService.get(group.getId())).thenReturn(group);

        mockMvc.perform(get("/api/system-groups")
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(group.getId().toString()))
                .andExpect(jsonPath("$[0].name").value("Border Control"))
                .andExpect(jsonPath("$[0].systems[0].id").value(system.getId().toString()))
                .andExpect(jsonPath("$[0].systems[0].name").value("border-system"));

        mockMvc.perform(get("/api/system-groups/{groupId}", group.getId())
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(group.getId().toString()));
    }

    @Test
    void writerCanCreateRenameAssignRemoveAndDelete() throws Exception {
        SystemGroup group = new SystemGroup("Border Control");
        UUID systemId = UUID.randomUUID();
        when(systemGroupService.create(" Border Control ")).thenReturn(group);
        when(systemGroupService.rename(group.getId(), "Border Applications")).thenAnswer(invocation -> {
            group.rename(invocation.getArgument(1));
            return group;
        });

        mockMvc.perform(post("/api/system-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Border Control \"}")
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(group.getId().toString()));

        mockMvc.perform(put("/api/system-groups/{groupId}", group.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Border Applications\"}")
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Border Applications"));

        mockMvc.perform(put("/api/system-groups/{groupId}/systems/{systemId}", group.getId(), systemId)
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/system-groups/{groupId}/systems/{systemId}", group.getId(), systemId)
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/system-groups/{groupId}", group.getId())
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isNoContent());

        verify(systemGroupService).assignSystem(group.getId(), systemId);
        verify(systemGroupService).removeSystem(group.getId(), systemId);
        verify(systemGroupService).delete(group.getId());
    }

    @Test
    void invalidNameReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/system-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}")
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nameLengthIsNotLimitedByRequestValidation() throws Exception {
        String longName = "a".repeat(5_000);
        SystemGroup group = new SystemGroup(longName);
        when(systemGroupService.create(longName)).thenReturn(group);

        mockMvc.perform(post("/api/system-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + longName + "\"}")
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(longName));
    }

    @Test
    void duplicateNameReturnsConflict() throws Exception {
        when(systemGroupService.create("Border Control"))
                .thenThrow(new SystemGroupNameAlreadyExistsException("Border Control"));

        mockMvc.perform(post("/api/system-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Border Control\"}")
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_GROUP_NAME_CONFLICT"))
                .andExpect(jsonPath("$.detail").value("System group name already exists"));
    }

    @Test
    void duplicateNameOnRenameReturnsConflict() throws Exception {
        UUID groupId = UUID.randomUUID();
        when(systemGroupService.rename(groupId, "Border Control"))
                .thenThrow(new SystemGroupNameAlreadyExistsException("Border Control"));

        mockMvc.perform(put("/api/system-groups/{groupId}", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Border Control\"}")
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_GROUP_NAME_CONFLICT"));
    }

    @Test
    void invalidDomainNameReturnsBadRequestProblem() throws Exception {
        when(systemGroupService.create("Invalid"))
                .thenThrow(new InvalidSystemGroupNameException());

        mockMvc.perform(post("/api/system-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Invalid\"}")
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SYSTEM_GROUP_NAME"));
    }

    @Test
    void missingResourcesReturnNotFound() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID systemId = UUID.randomUUID();
        when(systemGroupService.get(groupId)).thenThrow(new SystemGroupNotFoundException(groupId));
        doThrow(new SystemNotFoundByIdException(systemId))
                .when(systemGroupService).assignSystem(groupId, systemId);
        doThrow(new SystemGroupNotFoundException(groupId))
                .when(systemGroupService).delete(groupId);
        doThrow(new SystemNotFoundByIdException(systemId))
                .when(systemGroupService).removeSystem(groupId, systemId);

        mockMvc.perform(get("/api/system-groups/{groupId}", groupId)
                .with(httpBasic("read", "secret")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_GROUP_NOT_FOUND"));
        mockMvc.perform(put("/api/system-groups/{groupId}/systems/{systemId}", groupId, systemId)
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_NOT_FOUND"));
        mockMvc.perform(delete("/api/system-groups/{groupId}", groupId)
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_GROUP_NOT_FOUND"));
        mockMvc.perform(delete("/api/system-groups/{groupId}/systems/{systemId}", groupId, systemId)
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_NOT_FOUND"));
    }

    @Test
    void readerCannotMutateGroups() throws Exception {
        mockMvc.perform(post("/api/system-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Border Control\"}")
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isForbidden());
    }
}
