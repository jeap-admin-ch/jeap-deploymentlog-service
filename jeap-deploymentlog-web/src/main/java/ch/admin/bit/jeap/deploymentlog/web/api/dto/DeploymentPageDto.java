package ch.admin.bit.jeap.deploymentlog.web.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "One page of deployments")
public record DeploymentPageDto(
        List<DeploymentReadDto> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static DeploymentPageDto of(Page<DeploymentReadDto> page) {
        return new DeploymentPageDto(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
