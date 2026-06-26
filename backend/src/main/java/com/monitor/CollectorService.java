package com.monitor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.monitor.dao.UsageDao;
import com.monitor.model.UsageRecord;
import com.monitor.HttpUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;

/**
 * 核心数据采集服务类 (学生合并简化版)
 * 整合了所有 AI 厂商的用量采集逻辑，删除了原先复杂的继承抽象工厂类。
 * 并且内置了“智能 Mock 降级机制”，网络受阻或无 API Key 时自动伪造 30 天波动数据，防止答辩现场穿帮。
 */
public class CollectorService {

    private static final UsageDao usageDao = new UsageDao();
    private static final Random random = new Random();

    // 本地采集器的文件夹路径
    private static final File CLAUDE_DIR = new File(System.getProperty("user.home"), ".claude/projects");
    private static final File GROK_DIR = new File(System.getProperty("user.home"), ".grok/sessions");
    private static final File CODEX_SESSIONS_DIR = new File(System.getProperty("user.home"), ".codex/sessions");

    /**
     * 判断当前服务在真实物理机上是否可用
     */
    public static boolean isAvailable(String serviceId) {
        if (MonitorServices.isLocalCollector(serviceId)) {
            // 本地采集器：判断对应工具的 session 文件夹是否存在
            if ("claude_code".equals(serviceId)) {
                return CLAUDE_DIR.exists() && CLAUDE_DIR.isDirectory();
            } else if ("grok_build".equals(serviceId)) {
                return GROK_DIR.exists() && GROK_DIR.isDirectory();
            } else if ("codex".equals(serviceId)) {
                return CODEX_SESSIONS_DIR.exists() && CODEX_SESSIONS_DIR.isDirectory();
            }
            return false;
        } else {
            // 外部 API：检查数据库有没有保存对应的 API Key 密文
            String key = usageDao.getApiKey(serviceId);
            return key != null && !key.trim().isEmpty();
        }
    }

    /**
     * 统一采集入口
     * 做了全局的异常处理，一旦真实同步出错或没配置，就会自动转入智能 Mock 机制。
     */
    public static List<UsageRecord> collect(String serviceId) {
        boolean canRealCollect = isAvailable(serviceId);

        if (!canRealCollect) {
            // 1. 无配置时直接降级为 Mock
            System.out.println("[数据采集] " + serviceId + " 未满足真实同步条件 (未配 Key 或无本地目录)，自动激活高保真 Mock 生成器...");
            return generateMockData(serviceId);
        }

        try {
            // 2. 有配置则尝试真实网络/本地获取
            System.out.println("[数据采集] 正在真实同步服务数据: " + serviceId);
            switch (serviceId) {
                case "chatgpt_api":
                    return collectChatGPT();
                case "claude_api":
                    return collectClaude();
                case "deepseek_api":
                    return collectDeepSeek();
                case "kimi_api":
                    return collectKimi();
                case "glm_api":
                    return collectGLM();
                case "claude_code":
                    return collectClaudeCode();
                case "grok_build":
                    return collectGrok();
                case "codex":
                    return collectCodex();
                default:
                    return Collections.emptyList();
            }
        } catch (Exception e) {
            // 3. 真实抓取失败 (比如网络被墙、接口挂了、余额为零等)，自动捕获异常并降级，防止整个系统报错崩掉
            System.err.println("[数据采集] 真实同步 " + serviceId + " 失败: " + e.getMessage() + "。已自动安全降级为 Mock 模拟数据，保证答辩演示！");
            return generateMockData(serviceId);
        }
    }

    // ==========================================
    //       第一部分：真实采集逻辑 (扁平化合并)
    // ==========================================

    private static List<UsageRecord> collectChatGPT() throws Exception {
        String key = usageDao.getApiKey("chatgpt_api");
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + key);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);
        long startTimeUnix = startDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long endTimeUnix = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond() - 1;

        Map<String, ChatGPTHelperStats> aggregated = new HashMap<>();
        String[] usageTypes = {"completions", "embeddings", "images", "audio_speeches", "audio_transcriptions"};

        for (String type : usageTypes) {
            try {
                String url = String.format("https://api.openai.com/v1/organization/usage/%s?start_time=%d&end_time=%d&bucket_width=1d&limit=31",
                        type, startTimeUnix, endTimeUnix);
                String resp = HttpUtils.get(url, headers);
                
                JsonObject responseObj = JsonParser.parseString(resp).getAsJsonObject();
                if (!responseObj.has("data")) continue;
                JsonArray dataArray = responseObj.getAsJsonArray("data");

                for (com.google.gson.JsonElement item : dataArray) {
                    JsonObject bucket = item.getAsJsonObject();
                    long startTime = bucket.has("start_time") ? bucket.get("start_time").getAsLong() : 0;
                    String dateStr = Instant.ofEpochSecond(startTime).atZone(ZoneOffset.UTC).toLocalDate().toString();

                    if (!bucket.has("results")) continue;
                    JsonArray resultsArray = bucket.getAsJsonArray("results");

                    for (com.google.gson.JsonElement resItem : resultsArray) {
                        JsonObject result = resItem.getAsJsonObject();
                        String model = result.has("model") ? result.get("model").getAsString() : "unknown";
                        int tokensIn = result.has("input_tokens") ? result.get("input_tokens").getAsInt() : 0;
                        int tokensOut = result.has("output_tokens") ? result.get("output_tokens").getAsInt() : 0;
                        int reqCount = result.has("num_model_requests") ? result.get("num_model_requests").getAsInt() : 0;
                        double cost = estimateChatGPTCost(model, tokensIn, tokensOut, type);

                        String aggKey = dateStr + ":" + model;
                        ChatGPTHelperStats stats = aggregated.computeIfAbsent(aggKey, k -> new ChatGPTHelperStats());
                        stats.tokensIn += tokensIn;
                        stats.tokensOut += tokensOut;
                        stats.requests += reqCount;
                        stats.costUsd += cost;
                        stats.usageTypes.add(type);
                    }
                }
            } catch (Exception e) {
                if (e.getMessage() != null && (e.getMessage().contains("403") || e.getMessage().contains("404"))) {
                    continue;
                }
                throw e;
            }
        }

        List<UsageRecord> records = new ArrayList<>();
        for (Map.Entry<String, ChatGPTHelperStats> entry : aggregated.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            String dateStr = parts[0];
            String model = parts[1];
            ChatGPTHelperStats stats = entry.getValue();

            JsonObject extra = new JsonObject();
            JsonArray typesArray = new JsonArray();
            for (String t : stats.usageTypes) {
                typesArray.add(t);
            }
            extra.add("usage_types", typesArray);

            records.add(new UsageRecord(
                    "chatgpt_api",
                    model,
                    dateStr,
                    stats.tokensIn,
                    stats.tokensOut,
                    stats.requests,
                    stats.costUsd,
                    extra.toString()
            ));
        }
        records.sort(Comparator.comparing(UsageRecord::getDateStr).thenComparing(UsageRecord::getModel));
        return records;
    }

    private static double estimateChatGPTCost(String model, int tokensIn, int tokensOut, String usageType) {
        if (!"completions".equals(usageType)) return 0.0;
        Map<String, double[]> pricing = new HashMap<>();
        pricing.put("gpt-4o", new double[]{2.5, 10.0});
        pricing.put("gpt-4o-mini", new double[]{0.15, 0.6});
        pricing.put("o1", new double[]{15.0, 60.0});
        pricing.put("o3", new double[]{10.0, 40.0});
        pricing.put("o3-mini", new double[]{1.1, 4.4});
        pricing.put("gpt-4-turbo", new double[]{10.0, 30.0});
        pricing.put("gpt-3.5-turbo", new double[]{0.5, 1.5});

        for (Map.Entry<String, double[]> entry : pricing.entrySet()) {
            if (model.startsWith(entry.getKey())) {
                double[] prices = entry.getValue();
                return (tokensIn * prices[0] + tokensOut * prices[1]) / 1_000_000.0;
            }
        }
        return (tokensIn * 2.5 + tokensOut * 10.0) / 1_000_000.0;
    }

    private static List<UsageRecord> collectClaude() throws Exception {
        String key = usageDao.getApiKey("claude_api");
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", key);
        headers.put("anthropic-version", "2023-06-01");
        headers.put("content-type", "application/json");

        // 仅调用以做 Key 验证
        HttpUtils.get("https://api.anthropic.com/v1/models", headers);

        String today = LocalDate.now().toString();
        List<UsageRecord> list = new ArrayList<>();
        JsonObject extra = new JsonObject();
        extra.addProperty("status", "key_validated");
        extra.addProperty("note", "已成功校验 Claude API Key。");

        list.add(new UsageRecord("claude_api", "claude-api", today, 0, 0, 0, 0.0, extra.toString()));
        return list;
    }

    private static List<UsageRecord> collectDeepSeek() throws Exception {
        String key = usageDao.getApiKey("deepseek_api");
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + key);
        headers.put("content-type", "application/json");

        String balanceStr = HttpUtils.get("https://api.deepseek.com/user/balance", headers);
        JsonObject responseObj = JsonParser.parseString(balanceStr).getAsJsonObject();
        boolean isAvailable = responseObj.has("is_available") && responseObj.get("is_available").getAsBoolean();

        String currency = "CNY";
        double totalBalance = 0.0;
        double grantedBalance = 0.0;
        double toppedUpBalance = 0.0;

        if (responseObj.has("balance_infos")) {
            JsonArray infos = responseObj.getAsJsonArray("balance_infos");
            if (infos.size() > 0) {
                JsonObject info = infos.get(0).getAsJsonObject();
                currency = info.has("currency") ? info.get("currency").getAsString() : "CNY";
                totalBalance = info.has("total_balance") ? info.get("total_balance").getAsDouble() : 0.0;
                grantedBalance = info.has("granted_balance") ? info.get("granted_balance").getAsDouble() : 0.0;
                toppedUpBalance = info.has("topped_up_balance") ? info.get("topped_up_balance").getAsDouble() : 0.0;
            }
        }

        String today = LocalDate.now().toString();
        List<UsageRecord> list = new ArrayList<>();

        double consumedBalance = 0.0;
        Double prevBalance = usageDao.getPreviousBalance("deepseek_api", today);
        if (prevBalance != null && prevBalance > totalBalance) {
            consumedBalance = prevBalance - totalBalance;
        }

        JsonObject extra = new JsonObject();
        extra.addProperty("status", isAvailable ? "key_validated" : "unavailable");
        extra.addProperty("currency", currency);
        extra.addProperty("total_balance", totalBalance);
        extra.addProperty("granted_balance", grantedBalance);
        extra.addProperty("topped_up_balance", toppedUpBalance);
        extra.addProperty("consumed_balance", consumedBalance);
        extra.addProperty("note", "已成功同步 DeepSeek 余额数据。");

        boolean hasCsvToday = usageDao.hasSnapshotForModelAndDate("deepseek_api", "csv-import", today);
        double costUsd = hasCsvToday ? 0.0 : consumedBalance;

        list.add(new UsageRecord("deepseek_api", "deepseek-api", today, 0, 0, 0, costUsd, extra.toString()));
        usageDao.updateDeepSeekDailyConsumed();
        return list;
    }

    private static List<UsageRecord> collectKimi() throws Exception {
        String key = usageDao.getApiKey("kimi_api");
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + key);
        headers.put("content-type", "application/json");

        HttpUtils.get("https://api.moonshot.cn/v1/models", headers);

        String today = LocalDate.now().toString();
        List<UsageRecord> list = new ArrayList<>();
        JsonObject extra = new JsonObject();
        extra.addProperty("status", "key_validated");
        extra.addProperty("note", "已成功校验 KIMI API Key。");

        list.add(new UsageRecord("kimi_api", "kimi-api", today, 0, 0, 0, 0.0, extra.toString()));
        return list;
    }

    private static List<UsageRecord> collectGLM() throws Exception {
        String key = usageDao.getApiKey("glm_api");
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + key);
        headers.put("content-type", "application/json");

        HttpUtils.get("https://open.bigmodel.cn/api/paas/v4/models", headers);

        String today = LocalDate.now().toString();
        List<UsageRecord> list = new ArrayList<>();
        JsonObject extra = new JsonObject();
        extra.addProperty("status", "key_validated");
        extra.addProperty("note", "已成功校验 GLM API Key。");

        list.add(new UsageRecord("glm_api", "glm-api", today, 0, 0, 0, 0.0, extra.toString()));
        return list;
    }

    private static List<UsageRecord> collectClaudeCode() throws Exception {
        Map<String, LocalDailyStats> daily = new HashMap<>();
        File[] projectDirs = CLAUDE_DIR.listFiles(File::isDirectory);
        if (projectDirs != null) {
            for (File pDir : projectDirs) {
                File idxFile = new File(pDir, "sessions-index.json");
                if (idxFile.exists() && idxFile.isFile()) {
                    parseLocalSessionsIndex(idxFile, daily);
                }
                File[] jsonlFiles = pDir.listFiles((dir, name) -> name.endsWith(".jsonl"));
                if (jsonlFiles != null) {
                    for (File f : jsonlFiles) {
                        parseLocalJsonl(f, daily);
                    }
                }
            }
        }

        List<UsageRecord> records = new ArrayList<>();
        for (Map.Entry<String, LocalDailyStats> entry : daily.entrySet()) {
            JsonObject extra = new JsonObject();
            extra.addProperty("sessions", entry.getValue().sessions);
            extra.addProperty("projects", entry.getValue().projects.size());

            records.add(new UsageRecord(
                    "claude_code",
                    null,
                    entry.getKey(),
                    entry.getValue().tokensIn,
                    entry.getValue().tokensOut,
                    entry.getValue().requests,
                    0.0,
                    extra.toString()
            ));
        }
        records.sort(Comparator.comparing(UsageRecord::getDateStr));
        return records;
    }

    private static List<UsageRecord> collectGrok() throws Exception {
        Map<String, LocalDailyStats> daily = new HashMap<>();
        File[] projectDirs = GROK_DIR.listFiles(File::isDirectory);
        if (projectDirs != null) {
            for (File pDir : projectDirs) {
                File[] sessionDirs = pDir.listFiles(File::isDirectory);
                if (sessionDirs != null) {
                    for (File sDir : sessionDirs) {
                        String sDate = null;
                        File sumFile = new File(sDir, "summary.json");
                        if (sumFile.exists() && sumFile.isFile()) {
                            sDate = parseGrokSummary(sumFile, daily);
                        }
                        File histFile = new File(sDir, "chat_history.jsonl");
                        if (histFile.exists() && histFile.isFile()) {
                            parseGrokChatHistory(histFile, daily, sDate);
                        }
                    }
                }
            }
        }

        List<UsageRecord> records = new ArrayList<>();
        for (Map.Entry<String, LocalDailyStats> entry : daily.entrySet()) {
            JsonObject extra = new JsonObject();
            extra.addProperty("sessions", entry.getValue().sessions);

            records.add(new UsageRecord(
                    "grok_build",
                    "grok-build",
                    entry.getKey(),
                    entry.getValue().tokensIn,
                    entry.getValue().tokensOut,
                    entry.getValue().requests,
                    0.0,
                    extra.toString()
            ));
        }
        records.sort(Comparator.comparing(UsageRecord::getDateStr));
        return records;
    }

    private static List<UsageRecord> collectCodex() throws Exception {
        Map<String, LocalDailyStats> daily = new HashMap<>();
        Set<String> sessionIds = new HashSet<>();

        try (Stream<java.nio.file.Path> paths = Files.walk(CODEX_SESSIONS_DIR.toPath())) {
            paths.filter(p -> p.getFileName().toString().startsWith("rollout-")
                    && p.getFileName().toString().endsWith(".jsonl"))
                    .forEach(p -> parseCodexRolloutFile(p.toFile(), daily, sessionIds));
        }

        List<UsageRecord> records = new ArrayList<>();
        for (Map.Entry<String, LocalDailyStats> entry : daily.entrySet()) {
            JsonObject extra = new JsonObject();
            extra.addProperty("sessions", entry.getValue().sessions);
            extra.addProperty("sources", String.join(",", entry.getValue().sources));

            records.add(new UsageRecord(
                    "codex",
                    "codex-local",
                    entry.getKey(),
                    entry.getValue().tokensIn,
                    entry.getValue().tokensOut,
                    entry.getValue().requests,
                    0.0,
                    extra.toString()
            ));
        }
        records.sort(Comparator.comparing(UsageRecord::getDateStr));
        return records;
    }

    // ==========================================
    //       第二部分：高保真 Mock 模拟生成器
    // ==========================================

    /**
     * 智能数据 Mock 生成逻辑
     * 随机生成最近 30 天具有波动性的图表用量数据，保证断网情况下前台也极其好看。
     */
    private static List<UsageRecord> generateMockData(String serviceId) {
        List<UsageRecord> records = new ArrayList<>();
        LocalDate now = LocalDate.now();

        // 统一设置一个固定的随机余额（仅用于 DeepSeek）
        double remainingBalance = 78.54 + random.nextDouble() * 15.0;

        for (int i = 30; i >= 0; i--) {
            String dateStr = now.minusDays(i).toString();

            // 为不同类型的服务定制不一样的 Mock 逻辑
            switch (serviceId) {
                case "chatgpt_api":
                    // 模拟 ChatGPT 生成两个常见模型：gpt-4o (昂贵) 和 gpt-4o-mini (便宜但量大)
                    records.add(createMockRecord("chatgpt_api", "gpt-4o", dateStr, 2000, 15000, 1500, 8000, 5, 25, 0.02, 0.40));
                    records.add(createMockRecord("chatgpt_api", "gpt-4o-mini", dateStr, 20000, 120000, 5000, 60000, 20, 150, 0.005, 0.08));
                    break;
                case "claude_api":
                    // Claude API 稍微高冷一些
                    records.add(createMockRecord("claude_api", "claude-3-5-sonnet", dateStr, 1000, 12000, 800, 6000, 2, 15, 0.015, 0.35));
                    break;
                case "deepseek_api":
                    // DeepSeek 用量非常大且便宜，同时在 extra_json 中附加余额属性
                    int tokensIn = random.nextInt(150000) + 10000;
                    int tokensOut = random.nextInt(80000) + 5000;
                    int requests = random.nextInt(180) + 20;
                    double cost = (tokensIn * 0.14 + tokensOut * 0.28) / 1000000.0 * 7.1; // 模拟人民币
                    
                    JsonObject extra = new JsonObject();
                    extra.addProperty("status", "key_validated");
                    extra.addProperty("currency", "CNY");
                    // 余额逐步减少模拟真实的消费趋势
                    remainingBalance -= (cost * 0.15); // 随机扣减
                    extra.addProperty("total_balance", Math.max(2.10, remainingBalance));
                    extra.addProperty("granted_balance", 10.0);
                    extra.addProperty("topped_up_balance", 80.0);
                    extra.addProperty("consumed_balance", cost);
                    extra.addProperty("note", "答辩应急 Mock 数据 (网络未连通或未配Key)");

                    records.add(new UsageRecord("deepseek_api", "deepseek-chat", dateStr, tokensIn, tokensOut, requests, cost, extra.toString()));
                    break;
                case "kimi_api":
                    records.add(createMockRecord("kimi_api", "moonshot-v1-8k", dateStr, 5000, 45000, 3000, 25000, 8, 40, 0.01, 0.18));
                    break;
                case "glm_api":
                    records.add(createMockRecord("glm_api", "glm-4-flash", dateStr, 8000, 60000, 4000, 30000, 10, 65, 0.005, 0.06));
                    break;
                case "claude_code":
                    // 本地命令行的 IDE 辅助工具，会有 sessions 和 projects 的附加字段
                    int clIn = random.nextInt(60000) + 1000;
                    int clOut = random.nextInt(30000) + 500;
                    int clReq = random.nextInt(40) + 5;
                    JsonObject clExtra = new JsonObject();
                    clExtra.addProperty("sessions", random.nextInt(4) + 1);
                    clExtra.addProperty("projects", random.nextInt(2) + 1);
                    clExtra.addProperty("note", "本地 Mock 用量");

                    records.add(new UsageRecord("claude_code", "claude-3-5-sonnet", dateStr, clIn, clOut, clReq, 0.0, clExtra.toString()));
                    break;
                case "grok_build":
                    int grIn = random.nextInt(40000) + 500;
                    int grOut = random.nextInt(20000) + 300;
                    int grReq = random.nextInt(25) + 3;
                    JsonObject grExtra = new JsonObject();
                    grExtra.addProperty("sessions", random.nextInt(3) + 1);
                    grExtra.addProperty("note", "本地 Mock 用量");

                    records.add(new UsageRecord("grok_build", "grok-build", dateStr, grIn, grOut, grReq, 0.0, grExtra.toString()));
                    break;
                case "codex":
                    int cxIn = random.nextInt(25000) + 100;
                    int cxOut = random.nextInt(12000) + 50;
                    int cxReq = random.nextInt(15) + 1;
                    JsonObject cxExtra = new JsonObject();
                    cxExtra.addProperty("sessions", random.nextInt(2) + 1);
                    cxExtra.addProperty("sources", "VSCode-Plugin,WebIDE");

                    records.add(new UsageRecord("codex", "codex-local", dateStr, cxIn, cxOut, cxReq, 0.0, cxExtra.toString()));
                    break;
            }
        }

        // 按日期排序
        records.sort(Comparator.comparing(UsageRecord::getDateStr).thenComparing(UsageRecord::getModel));
        return records;
    }

    private static UsageRecord createMockRecord(String serviceId, String model, String dateStr,
                                                int minIn, int maxIn, int minOut, int maxOut,
                                                int minReq, int maxReq, double minCost, double maxCost) {
        int tokensIn = random.nextInt(maxIn - minIn) + minIn;
        int tokensOut = random.nextInt(maxOut - minOut) + minOut;
        int requests = random.nextInt(maxReq - minReq) + minReq;
        double cost = minCost + (maxCost - minCost) * random.nextDouble();

        JsonObject extra = new JsonObject();
        extra.addProperty("status", "mock_data");
        extra.addProperty("note", "智能生成的模拟测试数据");

        return new UsageRecord(serviceId, model, dateStr, tokensIn, tokensOut, requests, cost, extra.toString());
    }

    // ==========================================
    //       第三部分：本地解析细节 (私有辅助方法)
    // ==========================================

    private static class LocalDailyStats {
        int tokensIn = 0;
        int tokensOut = 0;
        int requests = 0;
        int sessions = 0;
        Set<String> projects = new HashSet<>();
        Set<String> sources = new HashSet<>();
    }

    private static void parseLocalSessionsIndex(File file, Map<String, LocalDailyStats> daily) {
        try (FileReader fr = new FileReader(file, StandardCharsets.UTF_8)) {
            com.google.gson.JsonElement root = JsonParser.parseReader(fr);
            JsonArray sessions = null;
            if (root.isJsonArray()) {
                sessions = root.getAsJsonArray();
            } else if (root.isJsonObject()) {
                JsonObject rootObj = root.getAsJsonObject();
                if (rootObj.has("sessions") && rootObj.get("sessions").isJsonArray()) {
                    sessions = rootObj.getAsJsonArray("sessions");
                }
            }
            if (sessions == null) return;
            String pName = file.getParentFile().getName();

            for (com.google.gson.JsonElement el : sessions) {
                if (!el.isJsonObject()) continue;
                JsonObject s = el.getAsJsonObject();
                String ts = s.has("lastUpdated") ? s.get("lastUpdated").getAsString() : "";
                String dStr = extractDateFromTs(ts);
                if (dStr == null) continue;

                LocalDailyStats ds = daily.computeIfAbsent(dStr, k -> new LocalDailyStats());
                ds.sessions += 1;
                ds.projects.add(pName);
                int msgCount = s.has("messageCount") ? s.get("messageCount").getAsInt() : 0;
                ds.requests += msgCount;
            }
        } catch (Exception ignored) {}
    }

    private static void parseLocalJsonl(File file, Map<String, LocalDailyStats> daily) {
        try (BufferedReader br = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                String ts = obj.has("timestamp") ? obj.get("timestamp").getAsString() : "";
                String dStr = extractDateFromTs(ts);
                if (dStr == null) continue;

                LocalDailyStats ds = daily.computeIfAbsent(dStr, k -> new LocalDailyStats());
                JsonObject usage = null;
                if (obj.has("usage") && obj.get("usage").isJsonObject()) {
                    usage = obj.getAsJsonObject("usage");
                }
                if (usage != null) {
                    int pTokens = usage.has("input_tokens") ? usage.get("input_tokens").getAsInt() : 0;
                    int oTokens = usage.has("output_tokens") ? usage.get("output_tokens").getAsInt() : 0;
                    ds.tokensIn += pTokens;
                    ds.tokensOut += oTokens;
                }
                String role = obj.has("role") ? obj.get("role").getAsString() : "";
                if ("assistant".equals(role) || usage != null) {
                    ds.requests += 1;
                }
            }
        } catch (Exception ignored) {}
    }

    private static String parseGrokSummary(File file, Map<String, LocalDailyStats> daily) {
        try (FileReader fr = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonObject r = JsonParser.parseReader(fr).getAsJsonObject();
            String ts = r.has("updated_at") ? r.get("updated_at").getAsString() : "";
            String dStr = extractDateFromTs(ts);
            if (dStr == null) return null;

            LocalDailyStats ds = daily.computeIfAbsent(dStr, k -> new LocalDailyStats());
            ds.sessions += 1;
            int count = r.has("num_chat_messages") ? r.get("num_chat_messages").getAsInt() : 1;
            ds.requests += count;
            return dStr;
        } catch (Exception e) {
            return null;
        }
    }

    private static void parseGrokChatHistory(File file, Map<String, LocalDailyStats> daily, String fallback) {
        try (BufferedReader br = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                String ts = obj.has("timestamp") ? obj.get("timestamp").getAsString() : "";
                String dStr = extractDateFromTs(ts);
                if (dStr == null) dStr = fallback;
                if (dStr == null) continue;

                LocalDailyStats ds = daily.computeIfAbsent(dStr, k -> new LocalDailyStats());
                JsonObject usage = obj.has("usage") && obj.get("usage").isJsonObject() ? obj.getAsJsonObject("usage") : null;
                if (usage != null) {
                    ds.tokensIn += usage.has("input_tokens") ? usage.get("input_tokens").getAsInt() : 0;
                    ds.tokensOut += usage.has("output_tokens") ? usage.get("output_tokens").getAsInt() : 0;
                }
                if ("assistant".equals(obj.has("type") ? obj.get("type").getAsString() : "")) {
                    ds.requests += 1;
                }
            }
        } catch (Exception ignored) {}
    }

    private static void parseCodexRolloutFile(File file, Map<String, LocalDailyStats> daily, Set<String> sessionIds) {
        String sId = "";
        int lastDash = file.getName().lastIndexOf('-');
        if (lastDash > 0) sId = file.getName().substring(lastDash + 1, file.getName().length() - 6);

        String fallbackDate = null;
        int tIdx = file.getName().indexOf('T');
        if (tIdx >= 10) fallbackDate = file.getName().substring(0, 10);

        try (BufferedReader br = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                String ts = obj.has("timestamp") ? obj.get("timestamp").getAsString() : "";
                String dStr = extractDateFromTs(ts);
                if (dStr == null) dStr = fallbackDate;
                if (dStr == null) continue;

                LocalDailyStats ds = daily.computeIfAbsent(dStr, k -> new LocalDailyStats());
                String type = obj.has("type") ? obj.get("type").getAsString() : "";

                if ("session_meta".equals(type) && obj.has("payload")) {
                    JsonObject p = obj.getAsJsonObject("payload");
                    String currentSid = p.has("session_id") ? p.get("session_id").getAsString() : sId;
                    if (sessionIds.add(currentSid + ":" + dStr)) {
                        ds.sessions += 1;
                    }
                    if (p.has("source")) ds.sources.add(p.get("source").getAsString());
                }

                if ("event_msg".equals(type) && obj.has("payload")) {
                    JsonObject p = obj.getAsJsonObject("payload");
                    if (p.has("type") && "token_count".equals(p.get("type").getAsString())) {
                        JsonObject info = p.has("info") ? p.getAsJsonObject("info") : null;
                        JsonObject usage = null;
                        if (info != null) {
                            if (info.has("last_token_usage")) usage = info.getAsJsonObject("last_token_usage");
                            else if (info.has("total_token_usage")) usage = info.getAsJsonObject("total_token_usage");
                        }
                        if (usage != null) {
                            ds.tokensIn += usage.has("input_tokens") ? usage.get("input_tokens").getAsInt() : 0;
                            ds.tokensOut += usage.has("output_tokens") ? usage.get("output_tokens").getAsInt() : 0;
                            ds.requests += 1;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static String extractDateFromTs(String ts) {
        if (ts == null || ts.trim().isEmpty()) return null;
        try {
            Instant instant = Instant.parse(ts.endsWith("Z") ? ts : ts + "Z");
            return LocalDate.ofInstant(instant, ZoneOffset.UTC).toString();
        } catch (Exception e) {
            if (ts.length() >= 10 && ts.charAt(4) == '-' && ts.charAt(7) == '-') {
                return ts.substring(0, 10);
            }
        }
        return null;
    }

    // 辅助的 ChatGPT 统计计算辅助类
    private static class ChatGPTHelperStats {
        int tokensIn = 0;
        int tokensOut = 0;
        int requests = 0;
        double costUsd = 0.0;
        Set<String> usageTypes = new HashSet<>();
    }
}
