package com.monitor.service.collectors;

import com.google.gson.JsonObject;
import com.monitor.model.UsageRecord;
import com.monitor.utils.HttpUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class GLMApiCollector extends BaseCollector {

    private static final String BASE_URL = "https://open.bigmodel.cn/api/paas/v4";

    @Override
    public String getServiceId() {
        return "glm_api";
    }

    @Override
    public String getDisplayName() {
        return "GLM API";
    }

    @Override
    public List<UsageRecord> collect() throws Exception {
        String key = usageDao.getApiKey(getServiceId());
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("未配置 GLM API Key，请在设置中添加。");
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + key);
        headers.put("content-type", "application/json");

        try {
            HttpUtils.get(BASE_URL + "/models", headers);
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("403"))) {
                throw new IllegalArgumentException("GLM API Key 无效或无权限。");
            }
            throw e;
        }

        String today = LocalDate.now().toString();
        List<UsageRecord> list = new ArrayList<>();

        JsonObject extra = new JsonObject();
        extra.addProperty("status", "key_validated");
        extra.addProperty("note", "已校验 GLM API Key。当前版本不抓取网页用量。");

        list.add(new UsageRecord(
                getServiceId(),
                "glm-api",
                today,
                0,
                0,
                0,
                0.0,
                extra.toString()
        ));

        return list;
    }
}
