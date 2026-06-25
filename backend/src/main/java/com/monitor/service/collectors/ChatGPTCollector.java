package com.monitor.service.collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.monitor.model.UsageRecord;
import com.monitor.utils.HttpUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;


public class ChatGPTCollector extends BaseCollector {

    private static final String BASE_URL = "https://api.openai.com/v1";
    private static final String[] USAGE_TYPES = {
            "completions", "embeddings", "images", "audio_speeches", "audio_transcriptions"
    };

    @Override
    public String getServiceId() {
        return "chatgpt_api";
    }

    @Override
    public String getDisplayName() {
        return "ChatGPT API";
    }

    @Override
    public boolean isAvailable() {
        String key = usageDao.getApiKey(getServiceId());
        return key != null && key.startsWith("sk-");
    }

    @Override
    public List<UsageRecord> collect() throws Exception {
        String key = usageDao.getApiKey(getServiceId());
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("未配置 ChatGPT/OpenAI API Key，请在设置中添加。");
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + key);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        long startTimeUnix = startDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long endTimeUnix = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond() - 1;


        Map<String, UsageStats> aggregated = new HashMap<>();

        for (String usageType : USAGE_TYPES) {
            try {
                String url = String.format("%s/organization/usage/%s?start_time=%d&end_time=%d&bucket_width=1d&limit=31",
                        BASE_URL, usageType, startTimeUnix, endTimeUnix);
                String resp = HttpUtils.get(url, headers);
                parseUsageResponse(resp, usageType, aggregated);
            } catch (Exception e) {

                if (e.getMessage() != null && (e.getMessage().contains("403") || e.getMessage().contains("404"))) {
                    continue;
                }
                throw e;
            }
        }

        List<UsageRecord> records = new ArrayList<>();
        for (Map.Entry<String, UsageStats> entry : aggregated.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            String dateStr = parts[0];
            String model = parts[1];
            UsageStats stats = entry.getValue();

            JsonObject extra = new JsonObject();
            JsonArray typesArray = new JsonArray();
            for (String t : stats.usageTypes) {
                typesArray.add(t);
            }
            extra.add("usage_types", typesArray);

            records.add(new UsageRecord(
                    getServiceId(),
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

    private void parseUsageResponse(String responseStr, String usageType, Map<String, UsageStats> aggregated) {
        JsonObject responseObj = JsonParser.parseString(responseStr).getAsJsonObject();
        if (!responseObj.has("data")) return;

        JsonArray dataArray = responseObj.getAsJsonArray("data");
        for (JsonElement item : dataArray) {
            JsonObject bucket = item.getAsJsonObject();
            long startTime = bucket.has("start_time") ? bucket.get("start_time").getAsLong() : 0;
            String dateStr = Instant.ofEpochSecond(startTime).atZone(ZoneOffset.UTC).toLocalDate().toString();

            if (!bucket.has("results")) continue;
            JsonArray resultsArray = bucket.getAsJsonArray("results");

            for (JsonElement resItem : resultsArray) {
                JsonObject result = resItem.getAsJsonObject();
                String model = result.has("model") ? result.get("model").getAsString() : "unknown";
                int tokensIn = result.has("input_tokens") ? result.get("input_tokens").getAsInt() : 0;
                int tokensOut = result.has("output_tokens") ? result.get("output_tokens").getAsInt() : 0;
                int reqCount = result.has("num_model_requests") ? result.get("num_model_requests").getAsInt() : 0;
                double cost = estimateCost(model, tokensIn, tokensOut, usageType);

                String aggKey = dateStr + ":" + model;
                UsageStats stats = aggregated.computeIfAbsent(aggKey, k -> new UsageStats());
                stats.tokensIn += tokensIn;
                stats.tokensOut += tokensOut;
                stats.requests += reqCount;
                stats.costUsd += cost;
                stats.usageTypes.add(usageType);
            }
        }
    }

    private double estimateCost(String model, int tokensIn, int tokensOut, String usageType) {
        if (!"completions".equals(usageType)) {
            return 0.0;
        }


        Map<String, double[]> pricing = new HashMap<>();
        pricing.put("gpt-4o", new double[]{2.5, 10.0});
        pricing.put("gpt-4o-mini", new double[]{0.15, 0.6});
        pricing.put("o1", new double[]{15.0, 60.0});
        pricing.put("o3", new double[]{10.0, 40.0});
        pricing.put("o3-mini", new double[]{1.1, 4.4});
        pricing.put("gpt-4-turbo", new double[]{10.0, 30.0});
        pricing.put("gpt-4", new double[]{30.0, 60.0});
        pricing.put("gpt-3.5-turbo", new double[]{0.5, 1.5});

        for (Map.Entry<String, double[]> entry : pricing.entrySet()) {
            if (model.startsWith(entry.getKey())) {
                double[] prices = entry.getValue();
                return (tokensIn * prices[0] + tokensOut * prices[1]) / 1_000_000.0;
            }
        }


        return (tokensIn * 2.5 + tokensOut * 10.0) / 1_000_000.0;
    }

    private static class UsageStats {
        int tokensIn = 0;
        int tokensOut = 0;
        int requests = 0;
        double costUsd = 0.0;
        Set<String> usageTypes = new HashSet<>();
    }
}
