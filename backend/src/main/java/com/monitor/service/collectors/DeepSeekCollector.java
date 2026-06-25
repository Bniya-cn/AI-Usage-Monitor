package com.monitor.service.collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.monitor.model.UsageRecord;
import com.monitor.utils.HttpUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class DeepSeekCollector extends BaseCollector {

    private static final String BASE_URL = "https://api.deepseek.com";

    @Override
    public String getServiceId() {
        return "deepseek_api";
    }

    @Override
    public String getDisplayName() {
        return "DeepSeek API";
    }

    @Override
    public List<UsageRecord> collect() throws Exception {
        String key = usageDao.getApiKey(getServiceId());
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("未配置 DeepSeek API Key，请在设置中添加。");
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + key);
        headers.put("content-type", "application/json");

        String balanceStr;
        try {

            balanceStr = HttpUtils.get(BASE_URL + "/user/balance", headers);
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("403"))) {
                throw new IllegalArgumentException("DeepSeek API Key 无效或已过期。");
            }
            throw e;
        }


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
        Double prevBalance = usageDao.getPreviousBalance(getServiceId(), today);
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
        extra.addProperty("note", "已成功同步 DeepSeek 余额数据。Token/请求数请通过 CSV 导入补充。");

        boolean hasCsvToday = usageDao.hasSnapshotForModelAndDate(getServiceId(), "csv-import", today);
        double costUsd = hasCsvToday ? 0.0 : consumedBalance;

        list.add(new UsageRecord(
                getServiceId(),
                "deepseek-api",
                today,
                0,
                0,
                0,
                costUsd,
                extra.toString()
        ));

        usageDao.updateDeepSeekDailyConsumed();

        return list;
    }
}
