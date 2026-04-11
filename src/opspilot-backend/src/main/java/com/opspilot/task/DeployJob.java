package com.opspilot.task;

import com.opspilot.dto.DeployRequest;
import com.opspilot.entity.DeploySchedule;
import com.opspilot.entity.ServiceInstance;
import com.opspilot.mapper.DeployScheduleMapper;
import com.opspilot.mapper.ServiceInstanceMapper;
import com.opspilot.service.DeployService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeployJob implements Job {

    private final DeployService deployService;
    private final DeployScheduleMapper scheduleMapper;
    private final ServiceInstanceMapper instanceMapper;

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        Long scheduleId = dataMap.getLong("scheduleId");
        Long operatorId = dataMap.getLong("operatorId");

        DeploySchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            log.error("Schedule {} not found", scheduleId);
            return;
        }

        log.info("Executing scheduled deploy: {}", schedule.getScheduleName());

        try {
            // Parse instance IDs
            String[] ids = schedule.getInstanceIds().split(",");
            for (String idStr : ids) {
                Long instanceId = Long.parseLong(idStr.trim());
                ServiceInstance inst = instanceMapper.selectById(instanceId);
                if (inst == null) {
                    log.warn("Instance {} not found, skipping", instanceId);
                    continue;
                }

                DeployRequest request = new DeployRequest();
                request.setInstanceId(instanceId);
                request.setGitBranch(schedule.getTargetBranch());
                request.setDeployType(1); // scheduled
                request.setScheduleId(scheduleId);

                Long deployId = deployService.startDeploy(request, operatorId, "system");
                log.info("Scheduled deploy started: deployId={}, instanceId={}", deployId, instanceId);
            }
        } catch (Exception e) {
            log.error("Scheduled deploy failed: {}", e.getMessage(), e);
        }
    }
}
