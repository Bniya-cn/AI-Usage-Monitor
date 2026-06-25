package com.monitor.service.collectors;

import com.google.gson.*;
import com.monitor.model.UsageRecord;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;


public class GrokCollector extends BaseCollector {

    private static final File GROK_DIR = new File(System.getProperty("user.home"), ".grok/sessions");

    @Override
    public String getServiceId() {
        return "grok_build";
    }

    @Override
    public String getDisplayName() {
        return "Grok Build";
    }

    @Override
    public boolean isAvailable() {
        return GROK_DIR.exists() && GROK_DIR.isDirectory();
    }

    @Override
    public List<UsageRecord> collect() throws Exception {
        if (!isAvailable()) {
            return Collections.emptyList();
        }


        Map<String, DailyStats> daily = new HashMap<>();

        File[] projectDirs = GROK_DIR.listFiles(File::isDirectory);
        if (projectDirs != null) {
            for (File projectDir : projectDirs) {
                File[] sessionDirs = projectDir.listFiles(File::isDirectory);
                if (sessionDirs != null) {
                    for (File sessionDir : sessionDirs) {

                        String sessionDate = null;
                        File summaryFile = new File(sessionDir, "summary.json");
                        if (summaryFile.exists() && summaryFile.isFile()) {
                            sessionDate = parseSummary(summaryFile, daily);
                        }

                        File historyFile = new File(sessionDir, "chat_history.jsonl");
                        if (historyFile.exists() && historyFile.isFile()) {
                            parseChatHistory(historyFile, daily, sessionDate);
                        }
                    }
                }
            }
        }

        List<UsageRecord> records = new ArrayList<>();
        for (Map.Entry<String, DailyStats> entry : daily.entrySet()) {
            String dateStr = entry.getKey();
            DailyStats stats = entry.getValue();

            JsonObject extra = new JsonObject();
            extra.addProperty("sessions", stats.sessions);

            records.add(new UsageRecord(
                    getServiceId(),
                    "grok-build",
                    dateStr,
                    stats.tokensIn,
                    stats.tokensOut,
                    stats.requests,
                    0.0,
                    extra.toString()
            ));
        }

        records.sort(Comparator.comparing(UsageRecord::getDateStr));
        return records;
    }

    private String parseSummary(File summaryFile, Map<String, DailyStats> daily) {
        try (FileReader fr = new FileReader(summaryFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(fr).getAsJsonObject();

            String ts = "";
            if (root.has("updated_at")) ts = root.get("updated_at").getAsString();
            else if (root.has("last_active_at")) ts = root.get("last_active_at").getAsString();
            else if (root.has("created_at")) ts = root.get("created_at").getAsString();

            String dateStr = extractDate(ts);
            if (dateStr == null) return null;

            DailyStats stats = daily.computeIfAbsent(dateStr, k -> new DailyStats());
            stats.sessions += 1;

            int msgCount = 0;
            if (root.has("num_chat_messages")) {
                msgCount = root.get("num_chat_messages").getAsInt();
            } else if (root.has("num_messages")) {
                msgCount = root.get("num_messages").getAsInt();
            }
            stats.requests += msgCount > 0 ? msgCount : 1;
            return dateStr;
        } catch (Exception e) {
            return null;
        }
    }

    private void parseChatHistory(File historyFile, Map<String, DailyStats> daily, String fallbackDate) {
        try (BufferedReader br = new BufferedReader(new FileReader(historyFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                JsonObject obj;
                try {
                    obj = JsonParser.parseString(line).getAsJsonObject();
                } catch (Exception e) {
                    continue;
                }

                String type = obj.has("type") ? obj.get("type").getAsString() : "";
                String ts = "";
                if (obj.has("timestamp")) ts = obj.get("timestamp").getAsString();
                else if (obj.has("created_at")) ts = obj.get("created_at").getAsString();

                String dateStr = extractDate(ts);
                if (dateStr == null) dateStr = fallbackDate;
                if (dateStr == null) continue;

                DailyStats stats = daily.computeIfAbsent(dateStr, k -> new DailyStats());

                if ("assistant".equals(type) && obj.has("tool_calls")) {
                    stats.requests += 1;
                }

                JsonObject usage = null;
                if (obj.has("usage") && obj.get("usage").isJsonObject()) {
                    usage = obj.getAsJsonObject("usage");
                }
                if (usage != null) {
                    int promptTokens = 0;
                    if (usage.has("input_tokens")) promptTokens = usage.get("input_tokens").getAsInt();
                    else if (usage.has("prompt_tokens")) promptTokens = usage.get("prompt_tokens").getAsInt();

                    int completionTokens = 0;
                    if (usage.has("output_tokens")) completionTokens = usage.get("output_tokens").getAsInt();
                    else if (usage.has("completion_tokens")) completionTokens = usage.get("completion_tokens").getAsInt();

                    stats.tokensIn += promptTokens;
                    stats.tokensOut += completionTokens;
                }
            }
        } catch (Exception e) {

        }
    }

    private String extractDate(String ts) {
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

    private static class DailyStats {
        int tokensIn = 0;
        int tokensOut = 0;
        int requests = 0;
        int sessions = 0;
    }
}
