package com.monitor.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.monitor.MonitorServices;
import com.monitor.dao.UsageDao;
import com.monitor.SyncScheduler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class AuthServlet extends HttpServlet {

    private final UsageDao usageDao = new UsageDao();
    private final Gson gson = new Gson();
    private static final String[] SERVICES = MonitorServices.ALL;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");

        Map<String, Map<String, Object>> authMap = new HashMap<>();
        for (String svc : SERVICES) {
            Map<String, Object> status = usageDao.getAuthStatus(svc);


            boolean hasKey = false;
            if (MonitorServices.isLocalCollector(svc)) {
                hasKey = true;
            } else {
                String key = usageDao.getApiKey(svc);
                hasKey = (key != null && !key.trim().isEmpty());
            }
            status.put("has_key", hasKey);
            authMap.put(svc, status);
        }

        resp.getWriter().write(gson.toJson(authMap));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");

        try {
            String requestBody = req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
            JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();

            String service = jsonObject.has("service") ? jsonObject.get("service").getAsString() : "";
            String apiKey = jsonObject.has("api_key") ? jsonObject.get("api_key").getAsString() : null;
            String action = jsonObject.has("action") ? jsonObject.get("action").getAsString() : "";

            if (service.isEmpty()) {
                throw new IllegalArgumentException("service 字段不能为空");
            }

            JsonObject res = new JsonObject();

            if ("clear".equalsIgnoreCase(action)) {

                usageDao.deleteApiKey(service);
                usageDao.setAuthStatus(service, false, "missing_credentials");
                res.addProperty("success", true);
                res.addProperty("message", "密钥已清除");
            } else {
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    throw new IllegalArgumentException("api_key 字段不能为空");
                }


                usageDao.setApiKey(service, apiKey.trim());


                SyncScheduler.runSync(List.of(service));

                Map<String, Object> newStatus = usageDao.getAuthStatus(service);
                int isAuth = (int) newStatus.getOrDefault("is_auth", 0);
                String errorMsg = (String) newStatus.getOrDefault("error_msg", "");

                if (isAuth == 1) {
                    res.addProperty("success", true);
                    res.addProperty("message", "验证成功并已保存配置");
                } else {
                    res.addProperty("success", false);
                    res.addProperty("error", errorMsg.isEmpty() ? "密钥校验未通过" : errorMsg);
                }
            }

            resp.getWriter().write(gson.toJson(res));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject res = new JsonObject();
            res.addProperty("success", false);
            res.addProperty("error", e.getMessage());
            resp.getWriter().write(gson.toJson(res));
        }
    }
}
