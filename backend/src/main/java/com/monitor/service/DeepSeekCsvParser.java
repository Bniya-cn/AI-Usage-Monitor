package com.monitor.service;

import com.monitor.model.UsageRecord;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public class DeepSeekCsvParser {

    private static final String SERVICE_ID = "deepseek_api";
    private static final String MODEL = "csv-import";

    public static class ImportResult {
        public int daysImported;
        public int totalTokens;
        public int totalRequests;
        public double totalCost;
        public List<UsageRecord> records = new ArrayList<>();
    }

    public ImportResult parseUpload(InputStream inputStream, String filename) throws Exception {
        String lower = filename != null ? filename.toLowerCase() : "";
        String amountCsv = null;
        String costCsv = null;

        if (lower.endsWith(".zip")) {
            Map<String, String> files = extractZip(inputStream);
            for (Map.Entry<String, String> entry : files.entrySet()) {
                String name = entry.getKey().toLowerCase();
                if (name.contains("amount") && name.endsWith(".csv")) {
                    amountCsv = entry.getValue();
                } else if (name.contains("cost") && name.endsWith(".csv")) {
                    costCsv = entry.getValue();
                }
            }
        } else if (lower.contains("amount") && lower.endsWith(".csv")) {
            amountCsv = readStream(inputStream);
        } else if (lower.contains("cost") && lower.endsWith(".csv")) {
            costCsv = readStream(inputStream);
        } else {
            String content = readStream(inputStream);
            if (content.contains("request_count") || content.contains("output_tokens")) {
                amountCsv = content;
            } else if (content.contains("cost") && content.contains("utc_date")) {
                costCsv = content;
            } else {
                throw new IllegalArgumentException("无法识别的 CSV 格式，请上传 DeepSeek 平台导出的 amount 或 cost CSV/ZIP 文件。");
            }
        }

        Map<String, DayStats> daily = new HashMap<>();
        if (amountCsv != null) {
            parseAmountCsv(amountCsv, daily);
        }
        if (costCsv != null) {
            parseCostCsv(costCsv, daily);
        }

        if (daily.isEmpty()) {
            throw new IllegalArgumentException("CSV 中未解析到有效用量数据。");
        }

        ImportResult result = new ImportResult();
        for (Map.Entry<String, DayStats> entry : daily.entrySet()) {
            DayStats stats = entry.getValue();
            com.google.gson.JsonObject extra = new com.google.gson.JsonObject();
            extra.addProperty("source", "csv_import");
            extra.addProperty("note", "来自 DeepSeek 平台 CSV 导入");

            UsageRecord record = new UsageRecord(
                    SERVICE_ID,
                    MODEL,
                    entry.getKey(),
                    stats.tokensIn,
                    stats.tokensOut,
                    stats.requests,
                    stats.cost,
                    extra.toString()
            );
            result.records.add(record);
            result.totalTokens += stats.tokensIn + stats.tokensOut;
            result.totalRequests += stats.requests;
            result.totalCost += stats.cost;
        }
        result.daysImported = daily.size();
        return result;
    }

    private void parseAmountCsv(String csv, Map<String, DayStats> daily) {
        String[] lines = csv.split("\n");
        if (lines.length < 2) return;

        int dateIdx = -1, typeIdx = -1, amountIdx = -1;
        String[] header = parseCsvLine(lines[0]);
        for (int i = 0; i < header.length; i++) {
            String h = header[i].trim().toLowerCase();
            if (h.equals("utc_date")) dateIdx = i;
            else if (h.equals("type")) typeIdx = i;
            else if (h.equals("amount")) amountIdx = i;
        }
        if (dateIdx < 0 || typeIdx < 0 || amountIdx < 0) return;

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] cols = parseCsvLine(line);
            if (cols.length <= Math.max(dateIdx, Math.max(typeIdx, amountIdx))) continue;

            String date = cols[dateIdx].trim();
            if (date.length() >= 10) date = date.substring(0, 10);
            String type = cols[typeIdx].trim().toLowerCase();
            long amount;
            try {
                amount = (long) Double.parseDouble(cols[amountIdx].trim());
            } catch (NumberFormatException e) {
                continue;
            }

            DayStats stats = daily.computeIfAbsent(date, k -> new DayStats());
            switch (type) {
                case "request_count":
                    stats.requests += (int) amount;
                    break;
                case "output_tokens":
                    stats.tokensOut += (int) amount;
                    break;
                case "input_cache_hit_tokens":
                case "input_cache_miss_tokens":
                    stats.tokensIn += (int) amount;
                    break;
                default:
                    break;
            }
        }
    }

    private void parseCostCsv(String csv, Map<String, DayStats> daily) {
        String[] lines = csv.split("\n");
        if (lines.length < 2) return;

        int dateIdx = -1, costIdx = -1;
        String[] header = parseCsvLine(lines[0]);
        for (int i = 0; i < header.length; i++) {
            String h = header[i].trim().toLowerCase();
            if (h.equals("utc_date")) dateIdx = i;
            else if (h.equals("cost")) costIdx = i;
        }
        if (dateIdx < 0 || costIdx < 0) return;

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] cols = parseCsvLine(line);
            if (cols.length <= Math.max(dateIdx, costIdx)) continue;

            String date = cols[dateIdx].trim();
            if (date.length() >= 10) date = date.substring(0, 10);
            double cost;
            try {
                cost = Double.parseDouble(cols[costIdx].trim());
            } catch (NumberFormatException e) {
                continue;
            }

            DayStats stats = daily.computeIfAbsent(date, k -> new DayStats());
            stats.cost += Math.abs(cost);
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private Map<String, String> extractZip(InputStream inputStream) throws Exception {
        Map<String, String> result = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".csv")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    result.put(entry.getName(), baos.toString(StandardCharsets.UTF_8));
                }
                zis.closeEntry();
            }
        }
        return result;
    }

    private String readStream(InputStream inputStream) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static class DayStats {
        int tokensIn = 0;
        int tokensOut = 0;
        int requests = 0;
        double cost = 0.0;
    }
}