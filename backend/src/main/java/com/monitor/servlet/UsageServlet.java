package com.monitor.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.monitor.MonitorServices;
import com.monitor.dao.UsageDao;
import com.monitor.model.UsageRecord;
import com.monitor.SyncScheduler;
import com.monitor.DeepSeekCsvParser;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.IOException;
import java.util.*;


@MultipartConfig(maxFileSize = 50 * 1024 * 1024, maxRequestSize = 50 * 1024 * 1024)
public class UsageServlet extends HttpServlet {

    private final UsageDao usageDao = new UsageDao();
    private final Gson gson = new Gson();
    private static final String[] SERVICES = MonitorServices.ALL;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");

        String action = req.getParameter("action");
        if (action == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "缺少 action 参数 (summary 或 history)");
            resp.getWriter().write(gson.toJson(error));
            return;
        }

        if ("summary".equalsIgnoreCase(action)) {

            Map<String, Map<String, Object>> summaryMap = new HashMap<>();
            for (String svc : SERVICES) {
                Map<String, Object> sum = usageDao.getSummary(svc);


                sum.putIfAbsent("tokens_in", 0);
                sum.putIfAbsent("tokens_out", 0);
                sum.putIfAbsent("requests", 0);
                sum.putIfAbsent("cost_usd", 0.0);
                sum.putIfAbsent("last_sync", "");


                String latestExtra = usageDao.getLatestExtraJson(svc);
                sum.put("latest_extra", latestExtra != null ? latestExtra : "");

                summaryMap.put(svc, sum);
            }
            resp.getWriter().write(gson.toJson(summaryMap));

        } else if ("history".equalsIgnoreCase(action)) {

            String service = req.getParameter("service");
            String daysStr = req.getParameter("days");
            int days = 30;

            if (service == null || service.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "缺少 service 参数");
                resp.getWriter().write(gson.toJson(error));
                return;
            }

            if (daysStr != null) {
                try {
                    days = Integer.parseInt(daysStr);
                } catch (NumberFormatException ignored) {}
            }

            List<UsageRecord> list = usageDao.getSnapshots(service, days);
            resp.getWriter().write(gson.toJson(list));

        } else {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "无效的 action 参数: " + action);
            resp.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");

        String action = req.getParameter("action");
        if ("sync".equalsIgnoreCase(action)) {

            System.out.println("用户手动触发了所有服务同步...");


            SyncScheduler.runSync(SyncScheduler.getAllServiceIds());

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("synced_at", new java.util.Date().toString());
            resp.getWriter().write(gson.toJson(result));
        } else if ("import_deepseek_csv".equalsIgnoreCase(action)) {
            try {
                Part filePart = req.getPart("file");
                if (filePart == null || filePart.getSize() == 0) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "请上传 CSV 或 ZIP 文件");
                    resp.getWriter().write(gson.toJson(error));
                    return;
                }

                String filename = filePart.getSubmittedFileName();
                DeepSeekCsvParser.ImportResult importResult = new DeepSeekCsvParser()
                        .parseUpload(filePart.getInputStream(), filename);

                for (UsageRecord record : importResult.records) {
                    usageDao.upsertSnapshot(record);
                }

                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("days_imported", importResult.daysImported);
                result.addProperty("total_tokens", importResult.totalTokens);
                result.addProperty("total_requests", importResult.totalRequests);
                result.addProperty("total_cost", importResult.totalCost);
                resp.getWriter().write(gson.toJson(result));
            } catch (Exception e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", e.getMessage() != null ? e.getMessage() : "CSV 导入失败");
                resp.getWriter().write(gson.toJson(error));
            }
        } else {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "不支持的 POST action: " + action);
            resp.getWriter().write(gson.toJson(error));
        }
    }
}
