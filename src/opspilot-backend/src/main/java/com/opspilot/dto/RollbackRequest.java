package com.opspilot.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RollbackRequest {
    @NotNull(message = "服务实例ID不能为空")
    private Long instanceId;
    @NotNull(message = "目标发版记录ID不能为空")
    private Long targetDeployId;
}
