package com.opspilot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.opspilot.entity.DeploySchedule;

public interface DeployScheduleService extends IService<DeploySchedule> {
    IPage<DeploySchedule> pageSchedules(int pageNum, int pageSize, Long moduleId);
    void createScheduleWithQuartz(DeploySchedule schedule);
    void updateScheduleWithQuartz(DeploySchedule schedule);
    void pauseSchedule(Long scheduleId);
    void resumeSchedule(Long scheduleId);
    void deleteSchedule(Long scheduleId);
    void triggerNow(Long scheduleId, Long operatorId);
}
