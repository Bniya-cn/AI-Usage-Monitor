package com.monitor.service.collectors;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.monitor.model.UsageRecord;

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


public class CodexCollector extends BaseCollector {

    private static final File CODEX_SESSIONS_DIR = new File(System.getProperty("user.home"), ".codex/sessions");

    @Override
    public String getServiceId() {
        return "codex";
    }

    @Override
    public String getDisplayName() {
        return "Codex";
    }

    @Override
    public boolean isAvailable() {
        return CODEX_SESSIONS_DIR.exists() && CODEX_SESSIONS_DIR.isDirectory();
    }

    @Override
    public List<UsageRecord> collect() throws Exception {
        if (!isAvailable()) {
            return Collections.emptyList();
        }

        Map<String, DailyStats> daily = new HashMap<>();
        Set<String> sessionIds = new HashSet<>();

        try (Stream<java.nio.file.Path> paths = Files.walk(CODEX_SESSIONS_DIR.toPath())) {
            paths.filter(p -> p.getFileName().toString().startsWith("rollout-")
                    && p.getFileName().toString().endsWith(".jsonl"))
                    .forEach(p -> parseRolloutFile(p.toFile(), daily, sessionIds));
        }

        List<UsageRecord> records = new ArrayList<>();
        for (Map.Entry<String, DailyStats> entry : daily.entrySet()) {
            DailyStats stats = entry.getValue();

            JsonObject extra = new JsonObject();
            extra.addProperty("sessions", stats.sessions);
            extra.addProperty("sources", String.join(",", stats.sources));

            records.add(new UsageRecord(
                    getServiceId(),
                    "codex-local",
                    entry.getKey(),
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

    private void parseRolloutFile(File file, Map<String, DailyStats> daily, Set<String> sessionIds) {
        String sessionId = extractSessionId(file.getName());
        String sessionDate = extractDateFromFilename(file.getName());

        try (BufferedReader br = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
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

                String timestamp = obj.has("timestamp") ? obj.get("timestamp").getAsString() : "";
                String dateStr = extractDate(timestamp);
                if (dateStr == null) dateStr = sessionDate;
                if (dateStr == null) continue;

                DailyStats stats = daily.computeIfAbsent(dateStr, k -> new DailyStats());
                String type = obj.has("type") ? obj.get("type").getAsString() : "";

                if ("session_meta".equals(type) && obj.has("payload")) {
                    JsonObject payload = obj.getAsJsonObject("payload");
                    String sid = payload.has("session_id") ? payload.get("session_id").getAsString() : sessionId;
                    if (sid != null && sessionIds.add(sid + ":" + dateStr)) {
                        stats.sessions += 1;
                    }
                    if (payload.has("source")) {
                        stats.sources.add(payload.get("source").getAsString());
                    } else if (payload.has("originator")) {
                        stats.sources.add(payload.get("originator").getAsString());
                    }
                }

                if ("event_msg".equals(type) && obj.has("payload")) {
                    JsonObject payload = obj.getAsJsonObject("payload");
                    if (!payload.has("type") || !"token_count".equals(payload.get("type").getAsString())) {
                        continue;
                    }
                    if (!payload.has("info")) continue;

                    JsonObject info = payload.getAsJsonObject("info");
                    JsonObject usage = null;
                    if (info.has("last_token_usage")) {
                        usage = info.getAsJsonObject("last_token_usage");
                    } else if (info.has("total_token_usage")) {
                        usage = info.getAsJsonObject("total_token_usage");
                    }
                    if (usage == null) continue;

                    int input = usage.has("input_tokens") ? usage.get("input_tokens").getAsInt() : 0;
                    int output = usage.has("output_tokens") ? usage.get("output_tokens").getAsInt() : 0;
                    if (usage.has("reasoning_output_tokens")) {
                        output += usage.get("reasoning_output_tokens").getAsInt();
                    }

                    stats.tokensIn += input;
                    stats.tokensOut += output;
                    stats.requests += 1;
                }
            }
        } catch (Exception e) {
            // skip unreadable files
        }
    }

    private String extractSessionId(String filename) {
        int lastDash = filename.lastIndexOf('-');
        if (lastDash > 0 && filename.endsWith(".jsonl")) {
            return filename.substring(lastDash + 1, filename.length() - 6);
        }
        return filename;
    }

    private String extractDateFromFilename(String filename) {
        int tIdx = filename.indexOf('T');
        if (tIdx >= 10) {
            return filename.substring(0, 10);
        }
        return null;
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
        Set<String> sources = new HashSet<>();
    }
}