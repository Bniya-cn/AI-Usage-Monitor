package com.monitor.scheduler;

import com.monitor.MonitorServices;
import com.monitor.dao.UsageDao;
import com.monitor.model.UsageRecord;
import com.monitor.service.collectors.*;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


public class SyncScheduler implements ServletContextListener {

    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> apiSyncJob;
    private static ScheduledFuture<?> ccSyncJob;

    private static final UsageDao usageDao = new UsageDao();
    private static final Map<String, BaseCollector> collectorMap = new HashMap<>();

    static {
        collectorMap.put("chatgpt_api", new ChatGPTCollector());
        collectorMap.put("claude_api", new ClaudeApiCollector());
        collectorMap.put("kimi_api", new KimiApiCollector());
        collectorMap.put("glm_api", new GLMApiCollector());
        collectorMap.put("deepseek_api", new DeepSeekCollector());
        collectorMap.put("claude_code", new ClaudeCodeCollector());
        collectorMap.put("grok_build", new GrokCollector());
        collectorMap.put("codex", new CodexCollector());
    }

    public static List<String> getAllServiceIds() {
        return new ArrayList<>(collectorMap.keySet());
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("AI Usage Monitor 后台同步调度器正在初始化...");
        scheduler = Executors.newScheduledThreadPool(2);


        scheduler.submit(() -> {
            System.out.println("启动时执行全量初始数据抓取...");
            runSync(new ArrayList<>(collectorMap.keySet()));
            System.out.println("全量初始数据抓取完成。");
        });

        startJobs();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("AI Usage Monitor 后台同步调度器正在关闭...");
        shutdown();
        com.monitor.dao.DBUtils.shutdown();
        System.out.println("调度器关闭完成。");
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
            System.err.println("解析同步周期配置失败，将采用默认值。");
        }

        System.out.println("排期任务：API 同步周期为 " + apiInterval + " 分钟，Claude Code 同步周期为 " + ccInterval + " 分钟。");


        List<String> apiServices = List.of("chatgpt_api", "claude_api", "kimi_api", "glm_api", "deepseek_api");
        apiSyncJob = scheduler.scheduleAtFixedRate(
                () -> runSync(apiServices),
                apiInterval,
                apiInterval,
                TimeUnit.MINUTES
        );


        List<String> ccServices = MonitorServices.LOCAL_COLLECTORS;
        ccSyncJob = scheduler.scheduleAtFixedRate(
                () -> runSync(ccServices),
                ccInterval,
                ccInterval,
                TimeUnit.MINUTES
        );
    }


    public static void runSync(List<String> serviceIds) {
        for (String id : serviceIds) {
            BaseCollector collector = collectorMap.get(id);
            if (collector == null) continue;

            if (!collector.isAvailable()) {

                if (!MonitorServices.isLocalCollector(id)) {
                    usageDao.setAuthStatus(id, false, "missing_credentials");
                }
                continue;
            }

            try {
                List<UsageRecord> records = collector.collect();
                for (UsageRecord r : records) {
                    usageDao.upsertSnapshot(r);
                }
                usageDao.setAuthStatus(id, true, "");
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
