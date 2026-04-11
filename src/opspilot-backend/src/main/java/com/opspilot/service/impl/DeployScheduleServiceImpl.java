package com.opspilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.opspilot.common.BusinessException;
import com.opspilot.entity.DeploySchedule;
import com.opspilot.mapper.DeployScheduleMapper;
import com.opspilot.service.DeployScheduleService;
import com.opspilot.task.DeployJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeployScheduleServiceImpl extends ServiceImpl<DeployScheduleMapper, DeploySchedule> implements DeployScheduleService {

    private final Scheduler scheduler;

    @Override
    public IPage<DeploySchedule> pageSchedules(int pageNum, int pageSize, Long moduleId) {
        LambdaQueryWrapper<DeploySchedule> wrapper = new LambdaQueryWrapper<>();
        if (moduleId != null) wrapper.eq(DeploySchedule::getModuleId, moduleId);
        wrapper.orderByDesc(DeploySchedule::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public void createScheduleWithQuartz(DeploySchedule schedule) {
        if (StringUtils.hasText(schedule.getScheduleName())) {
            long count = count(new LambdaQueryWrapper<DeploySchedule>()
                    .eq(DeploySchedule::getScheduleName, schedule.getScheduleName()));
            if (count > 0) throw new BusinessException("定时任务名称已存在");
        }
        save(schedule);
        if (schedule.getStatus() == 1) {
            addQuartzJob(schedule);
        }
    }

    @Override
    public void updateScheduleWithQuartz(DeploySchedule schedule) {
        DeploySchedule existing = getById(schedule.getId());
        if (existing == null) throw new BusinessException("定时任务不存在");
        // Remove old quartz job
        removeQuartzJob(existing.getId());
        updateById(schedule);
        if (schedule.getStatus() == 1) {
            addQuartzJob(schedule);
        }
    }

    @Override
    public void pauseSchedule(Long scheduleId) {
        DeploySchedule schedule = getById(scheduleId);
        if (schedule == null) throw new BusinessException("定时任务不存在");
        schedule.setStatus(0);
        updateById(schedule);
        removeQuartzJob(scheduleId);
    }

    @Override
    public void resumeSchedule(Long scheduleId) {
        DeploySchedule schedule = getById(scheduleId);
        if (schedule == null) throw new BusinessException("定时任务不存在");
        schedule.setStatus(1);
        updateById(schedule);
        addQuartzJob(schedule);
    }

    @Override
    public void deleteSchedule(Long scheduleId) {
        removeQuartzJob(scheduleId);
        removeById(scheduleId);
    }

    @Override
    public void triggerNow(Long scheduleId, Long operatorId) {
        DeploySchedule schedule = getById(scheduleId);
        if (schedule == null) throw new BusinessException("定时任务不存在");
        // Execute immediately
        try {
            JobDataMap dataMap = new JobDataMap();
            dataMap.put("scheduleId", scheduleId);
            dataMap.put("operatorId", operatorId);
            JobDetail job = JobBuilder.newJob(DeployJob.class)
                    .withIdentity("manual_" + scheduleId)
                    .usingJobData(dataMap)
                    .build();
            scheduler.addJob(job, true);
            scheduler.triggerJob(job.getKey());
        } catch (SchedulerException e) {
            throw new BusinessException("触发任务失败: " + e.getMessage());
        }
    }

    private void addQuartzJob(DeploySchedule schedule) {
        try {
            JobDataMap dataMap = new JobDataMap();
            dataMap.put("scheduleId", schedule.getId());
            dataMap.put("operatorId", schedule.getCreatorId());

            JobDetail job = JobBuilder.newJob(DeployJob.class)
                    .withIdentity("schedule_" + schedule.getId(), "deploy")
                    .usingJobData(dataMap)
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("trigger_" + schedule.getId(), "deploy")
                    .withSchedule(CronScheduleBuilder.cronSchedule(schedule.getCronExpression()))
                    .build();

            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            throw new BusinessException("添加定时任务失败: " + e.getMessage());
        }
    }

    private void removeQuartzJob(Long scheduleId) {
        try {
            JobKey jobKey = new JobKey("schedule_" + scheduleId, "deploy");
            scheduler.deleteJob(jobKey);
        } catch (SchedulerException e) {
            log.warn("Failed to remove quartz job {}: {}", scheduleId, e.getMessage());
        }
    }
}
