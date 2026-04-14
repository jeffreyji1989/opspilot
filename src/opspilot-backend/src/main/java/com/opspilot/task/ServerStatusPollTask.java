package com.opspilot.task;

import com.opspilot.service.ServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 服务器状态定时探测任务
 *
 * <p>每 5 分钟探测一次所有服务器的在线/离线状态。</p>
 *
 * @author opspilot-team
 * @since 2026-04-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServerStatusPollTask {

    private final ServerService serverService;

    /**
     * 每 5 分钟执行一次服务器状态更新
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void pollServerStatus() {
        log.info("开始执行服务器状态定时探测");
        try {
            serverService.updateAllServerStatus();
        } catch (Exception e) {
            log.error("服务器状态定时探测异常: {}", e.getMessage(), e);
        }
    }
}
