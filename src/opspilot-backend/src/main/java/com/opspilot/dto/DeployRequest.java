package com.opspilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeployRequest {
    @NotNull(message = "服务实例ID不能为空")
    private Long instanceId;
    @NotBlank(message = "分支不能为空")
    private String gitBranch;
    private String gitCommit;
    private Integer deployType = 0; // 0=手动
    private Long scheduleId;
}
