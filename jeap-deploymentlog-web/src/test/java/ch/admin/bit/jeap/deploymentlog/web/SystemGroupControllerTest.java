package ch.admin.bit.jeap.deploymentlog.web;

import ch.admin.bit.jeap.deploymentlog.domain.System;
import ch.admin.bit.jeap.deploymentlog.domain.SystemGroup;
import ch.admin.bit.jeap.deploymentlog.domain.SystemGroupService;
import ch.admin.bit.jeap.deploymentlog.domain.exception.InvalidSystemGroupNameException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemGroupNameAlreadyExistsException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemGroupNotFoundException;
import ch.admin.bit.jeap.deploymentlog.domain.exception.SystemNotFoundForGroupException;
import ch.admin.bit.jeap.deploymentlog.docgen.service.DocgenAsyncService;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SystemGroupController.class)
@Import({WebSecurityConfig.class, ResourceServerProperties.class})
@AutoConfigureMockMvc
class SystemGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SystemGroupService systemGroupService;
    @MockitoBean
    private DocgenAsyncService docgenAsyncService;

    @Test
    void readerCanListAndReadGroups() throws Exception {
        SystemGroup group = new SystemGroup("Example Group");
        System system = new System("border-system");
        group.getSystems().add(system);
        when(systemGroupService.findAll()).thenReturn(List.of(group));
        when(systemGroupService.get(group.getId())).thenReturn(group);

        mockMvc.perform(get("/api/system-groups")
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(group.getId().toString()))
                .andExpect(jsonPath("$[0].name").value("Example Group"))
                .andExpect(jsonPath("$[0].systems[0].id").value(system.getId().toString()))
                .andExpect(jsonPath("$[0].systems[0].name").value("border-system"));

        mockMvc.perform(get("/api/system-groups/{groupId}", group.getId())
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(group.getId().toString()));
    }

    @Test
    void writerCanCreateRenameAssignRemoveAndDelete() throws Exception {
        SystemGroup group = new SystemGroup("Example Group");
        String systemName = "my-system";
        when(systemGroupService.create(" Example Group ")).thenReturn(group);
        when(systemGroupService.rename(group.getId(), "Renamed Group")).thenAnswer(invocation -> {
            group.rename(invocation.getArgument(1));
            return group;
        });

        mockMvc.perform(post("/deploymentlog/api/system-groups")
                        .contextPath("/deploymentlog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Example Group \"}")
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "http://localhost/deploymentlog/api/system-groups/" + group.getId()))
                .andExpect(jsonPath("$.id").value(group.getId().toString()));

        mockMvc.perform(put("/api/system-groups/{groupId}", group.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed Group\"}")
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Group"));

        mockMvc.perform(put("/api/system-groups/{groupId}/systems/{systemName}", group.getId(), systemName)
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/system-groups/{groupId}/systems/{systemName}", group.getId(), systemName)
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/system-groups/{groupId}", group.getId())
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isNoContent());

        verify(systemGroupService).assignSystem(group.getId(), systemName);
        verify(systemGroupService).removeSystem(group.getId(), systemName);
        verify(systemGroupService).delete(group.getId());
        verify(docgenAsyncService, times(4)).triggerDocumentationStructureReconciliation();
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
        when(systemGroupService.create("Example Group"))
                .thenThrow(new SystemGroupNameAlreadyExistsException("Example Group"));

        mockMvc.perform(post("/api/system-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Example Group\"}")
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_GROUP_NAME_CONFLICT"))
                .andExpect(jsonPath("$.detail").value("System group name already exists"));
    }

    @Test
    void duplicateNameOnRenameReturnsConflict() throws Exception {
        UUID groupId = UUID.randomUUID();
        when(systemGroupService.rename(groupId, "Example Group"))
                .thenThrow(new SystemGroupNameAlreadyExistsException("Example Group"));

        mockMvc.perform(put("/api/system-groups/{groupId}", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Example Group\"}")
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
        String systemName = "unknown-system";
        when(systemGroupService.get(groupId)).thenThrow(new SystemGroupNotFoundException(groupId));
        doThrow(new SystemNotFoundForGroupException(systemName))
                .when(systemGroupService).assignSystem(groupId, systemName);
        doThrow(new SystemGroupNotFoundException(groupId))
                .when(systemGroupService).delete(groupId);
        doThrow(new SystemNotFoundForGroupException(systemName))
                .when(systemGroupService).removeSystem(groupId, systemName);

        mockMvc.perform(get("/api/system-groups/{groupId}", groupId)
                .with(httpBasic("read", "secret")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_GROUP_NOT_FOUND"));
        mockMvc.perform(put("/api/system-groups/{groupId}/systems/{systemName}", groupId, systemName)
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_NOT_FOUND"));
        mockMvc.perform(delete("/api/system-groups/{groupId}", groupId)
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_GROUP_NOT_FOUND"));
        mockMvc.perform(delete("/api/system-groups/{groupId}/systems/{systemName}", groupId, systemName)
                        .with(httpBasic("write", "secret")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_NOT_FOUND"));
    }

    @Test
    void readerCannotMutateGroups() throws Exception {
        mockMvc.perform(post("/api/system-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Example Group\"}")
                        .with(httpBasic("read", "secret")))
                .andExpect(status().isForbidden());
    }
}
