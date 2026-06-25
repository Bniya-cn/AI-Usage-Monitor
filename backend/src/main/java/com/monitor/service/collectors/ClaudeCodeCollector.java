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
import java.time.format.DateTimeFormatter;
import java.util.*;


public class ClaudeCodeCollector extends BaseCollector {

    private static final File CLAUDE_DIR = new File(System.getProperty("user.home"), ".claude/projects");

    @Override
    public String getServiceId() {
        return "claude_code";
    }

    @Override
    public String getDisplayName() {
        return "Claude Code";
    }

    @Override
    public boolean isAvailable() {
        return CLAUDE_DIR.exists() && CLAUDE_DIR.isDirectory();
    }

    @Override
    public List<UsageRecord> collect() throws Exception {
        if (!isAvailable()) {
            return Collections.emptyList();
        }


        Map<String, DailyStats> daily = new HashMap<>();

        File[] projectDirs = CLAUDE_DIR.listFiles(File::isDirectory);
        if (projectDirs != null) {
            for (File projectDir : projectDirs) {

                File indexFile = new File(projectDir, "sessions-index.json");
                if (indexFile.exists() && indexFile.isFile()) {
                    parseSessionsIndex(indexFile, daily);
                }


                File[] jsonlFiles = projectDir.listFiles((dir, name) -> name.endsWith(".jsonl"));
                if (jsonlFiles != null) {
                    for (File jsonlFile : jsonlFiles) {
                        parseJsonl(jsonlFile, daily);
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
            extra.addProperty("projects", stats.projects.size());

            records.add(new UsageRecord(
                    getServiceId(),
                    null,
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

    private void parseSessionsIndex(File indexFile, Map<String, DailyStats> daily) {
        try (FileReader fr = new FileReader(indexFile, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(fr);
            JsonArray sessions = null;

            if (rootElement.isJsonArray()) {
                sessions = rootElement.getAsJsonArray();
            } else if (rootElement.isJsonObject()) {
                JsonObject rootObj = rootElement.getAsJsonObject();
                if (rootObj.has("sessions") && rootObj.get("sessions").isJsonArray()) {
                    sessions = rootObj.getAsJsonArray("sessions");
                } else if (rootObj.has("data") && rootObj.get("data").isJsonArray()) {
                    sessions = rootObj.getAsJsonArray("data");
                } else {

                    sessions = new JsonArray();
                    for (Map.Entry<String, JsonElement> entry : rootObj.entrySet()) {
                        if (entry.getValue().isJsonObject()) {
                            sessions.add(entry.getValue());
                        }
                    }
                }
            }

            if (sessions == null) return;
            String projectName = indexFile.getParentFile().getName();

            for (JsonElement element : sessions) {
                if (!element.isJsonObject()) continue;
                JsonObject session = element.getAsJsonObject();


                String ts = "";
                if (session.has("lastUpdated")) ts = session.get("lastUpdated").getAsString();
                else if (session.has("updatedAt")) ts = session.get("updatedAt").getAsString();
                else if (session.has("createdAt")) ts = session.get("createdAt").getAsString();
                else if (session.has("timestamp")) ts = session.get("timestamp").getAsString();

                String dateStr = extractDate(ts);
                if (dateStr == null) continue;

                DailyStats stats = daily.computeIfAbsent(dateStr, k -> new DailyStats());
                stats.sessions += 1;
                stats.projects.add(projectName);

                int msgCount = 0;
                if (session.has("messageCount")) msgCount = session.get("messageCount").getAsInt();
                else if (session.has("message_count")) msgCount = session.get("message_count").getAsInt();
                else if (session.has("numMessages")) msgCount = session.get("numMessages").getAsInt();

                stats.requests += msgCount;
            }
        } catch (Exception e) {

        }
    }

    private void parseJsonl(File jsonlFile, Map<String, DailyStats> daily) {
        try (BufferedReader br = new BufferedReader(new FileReader(jsonlFile, StandardCharsets.UTF_8))) {
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

                String ts = "";
                if (obj.has("timestamp")) ts = obj.get("timestamp").getAsString();
                else if (obj.has("created_at")) ts = obj.get("created_at").getAsString();
                else if (obj.has("createdAt")) ts = obj.get("createdAt").getAsString();
                else if (obj.has("time")) ts = obj.get("time").getAsString();

                String dateStr = extractDate(ts);
                if (dateStr == null) continue;

                DailyStats stats = daily.computeIfAbsent(dateStr, k -> new DailyStats());


                JsonObject usage = null;
                if (obj.has("usage") && obj.get("usage").isJsonObject()) {
                    usage = obj.getAsJsonObject("usage");
                } else if (obj.has("token_usage") && obj.get("token_usage").isJsonObject()) {
                    usage = obj.getAsJsonObject("token_usage");
                } else if (obj.has("message") && obj.get("message").isJsonObject()) {
                    JsonObject message = obj.getAsJsonObject("message");
                    if (message.has("usage") && message.get("usage").isJsonObject()) {
                        usage = message.getAsJsonObject("usage");
                    }
                }

                if (usage != null) {
                    int promptTokens = 0;
                    if (usage.has("input_tokens")) promptTokens = usage.get("input_tokens").getAsInt();
                    else if (usage.has("prompt_tokens")) promptTokens = usage.get("prompt_tokens").getAsInt();
                    else if (usage.has("cache_read_input_tokens")) promptTokens = usage.get("cache_read_input_tokens").getAsInt();

                    int completionTokens = 0;
                    if (usage.has("output_tokens")) completionTokens = usage.get("output_tokens").getAsInt();
                    else if (usage.has("completion_tokens")) completionTokens = usage.get("completion_tokens").getAsInt();

                    stats.tokensIn += promptTokens;
                    stats.tokensOut += completionTokens;
                }


                String role = obj.has("role") ? obj.get("role").getAsString() : "";
                String type = obj.has("type") ? obj.get("type").getAsString() : "";
                if ("assistant".equals(role) || "tool_result".equals(role) || "assistant".equals(type) || usage != null) {
                    stats.requests += 1;
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
        Set<String> projects = new HashSet<>();
    }
}
