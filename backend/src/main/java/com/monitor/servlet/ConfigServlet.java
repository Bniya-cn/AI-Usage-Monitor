package com.monitor.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.monitor.dao.UsageDao;
import com.monitor.SyncScheduler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;


public class ConfigServlet extends HttpServlet {

    private final UsageDao usageDao = new UsageDao();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");

        Map<String, String> configs = usageDao.getConfigs();


        configs.putIfAbsent("sync_interval_minutes", "30");
        configs.putIfAbsent("claude_code_sync_interval_minutes", "5");
        configs.putIfAbsent("theme", "dark");

        resp.getWriter().write(gson.toJson(configs));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");

        try {
            String requestBody = req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
            JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();

            boolean restartScheduler = false;

            for (Map.Entry<String, com.google.gson.JsonElement> entry : jsonObject.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue().getAsString();

                usageDao.saveConfig(key, value);

                if ("sync_interval_minutes".equals(key) || "claude_code_sync_interval_minutes".equals(key)) {
                    restartScheduler = true;
                }
            }


            if (restartScheduler) {
                SyncScheduler.startJobs();
            }

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
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
