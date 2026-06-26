package com.monitor;

import com.monitor.dao.UsageDao;
import com.monitor.model.UsageRecord;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 后台定时数据采集调度器 (Listener 模式)
 * 启动时会自动注册到 web 容器中，定期触发各个 AI 服务的数据拉取或降级 Mock 生成。
 */
public class SyncScheduler implements ServletContextListener {

    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> apiSyncJob;
    private static ScheduledFuture<?> ccSyncJob;

    private static final UsageDao usageDao = new UsageDao();

    // 所有的服务列表
    private static final List<String> ALL_SERVICES = Arrays.asList(
            "chatgpt_api", "claude_api", "kimi_api", "glm_api",
            "deepseek_api", "claude_code", "grok_build", "codex"
    );

    public static List<String> getAllServiceIds() {
        return ALL_SERVICES;
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[系统启动] AI 用量监控定时器初始化中...");
        scheduler = Executors.newScheduledThreadPool(2);

        // 启动时在后台子线程立即拉取一次
        scheduler.submit(() -> {
            System.out.println("[系统启动] 启动时执行第一次全量用量同步...");
            runSync(ALL_SERVICES);
            System.out.println("[系统启动] 全量初始用量数据同步完成。");
        });

        // 启动定时循环任务
        startJobs();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("[系统关闭] AI 用量监控定时器正在关闭...");
        shutdown();
        com.monitor.dao.DBUtils.shutdown();
        System.out.println("[系统关闭] 定时器注销完成。");
    }

    public static synchronized void startJobs() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(2);
        }

        if (apiSyncJob != null) {
            apiSyncJob.cancel(false);
        }
        if (ccSyncJob != null) {
            ccSyncJob.cancel(false);
        }

        // 从数据库里读取同步间隔配置，如果读取失败就采用默认值
        Map<String, String> configs = usageDao.getConfigs();
        int apiInterval = 30;
        int ccInterval = 5;

        try {
            if (configs.containsKey("sync_interval_minutes")) {
                apiInterval = Math.max(5, Integer.parseInt(configs.get("sync_interval_minutes")));
            }
            if (configs.containsKey("claude_code_sync_interval_minutes")) {
                ccInterval = Math.max(1, Integer.parseInt(configs.get("claude_code_sync_interval_minutes")));
            }
        } catch (NumberFormatException e) {
            System.err.println("[定时配置] 解析配置周期失败，采用默认值 (API: 30分钟, 本地: 5分钟)。");
        }

        System.out.println("[定时配置] 定时任务开启：在线API采集周期为 " + apiInterval + " 分钟，本地工具采集周期为 " + ccInterval + " 分钟。");

        // 1. 在线 API 类服务同步
        List<String> apiServices = List.of("chatgpt_api", "claude_api", "kimi_api", "glm_api", "deepseek_api");
        apiSyncJob = scheduler.scheduleAtFixedRate(
                () -> runSync(apiServices),
                apiInterval,
                apiInterval,
                TimeUnit.MINUTES
        );

        // 2. 本地开发者工具用量同步
        List<String> ccServices = MonitorServices.LOCAL_COLLECTORS;
        ccSyncJob = scheduler.scheduleAtFixedRate(
                () -> runSync(ccServices),
                ccInterval,
                ccInterval,
                TimeUnit.MINUTES
        );
    }

    /**
     * 执行具体列表的数据收集并入库
     */
    public static void runSync(List<String> serviceIds) {
        for (String id : serviceIds) {
            try {
                // 调用合并后的 CollectorService 统一采集（如果失败或未配置会自动返回 Mock 数据）
                List<UsageRecord> records = CollectorService.collect(id);
                for (UsageRecord r : records) {
                    usageDao.upsertSnapshot(r);
                }
                
                // 判断是真的采集到并且可用，或者是 Mock 的
                boolean hasKey = !MonitorServices.isLocalCollector(id) && CollectorService.isAvailable(id);
                boolean isLocalAvailable = MonitorServices.isLocalCollector(id) && CollectorService.isAvailable(id);
                
                if (hasKey || isLocalAvailable) {
                    usageDao.setAuthStatus(id, true, "");
                } else {
                    usageDao.setAuthStatus(id, true, "Mock 演示模式已激活");
                }
            } catch (Exception e) {
                String errMsg = e.getMessage() != null ? e.getMessage() : "未知采集错误";
                usageDao.setAuthStatus(id, false, errMsg.length() > 200 ? errMsg.substring(0, 200) : errMsg);
            }
        }
    }

    public static void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
    }
}
