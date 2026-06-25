package com.monitor.dao;

import com.monitor.model.UsageRecord;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class UsageDao {


    public void upsertSnapshot(UsageRecord r) {
        String querySelect = "SELECT id FROM usage_snapshots WHERE service = ? AND (model = ? OR (model IS NULL AND ? IS NULL)) AND date_str = ?";
        String queryInsert = "INSERT INTO usage_snapshots (service, model, date_str, tokens_in, tokens_out, requests, cost_usd, extra_json, synced_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String queryUpdate = "UPDATE usage_snapshots SET tokens_in = ?, tokens_out = ?, requests = ?, cost_usd = ?, extra_json = ?, synced_at = ? WHERE id = ?";

        String syncedAt = LocalDateTime.now().toString();

        try (Connection conn = DBUtils.getConnection()) {
            Integer existingId = null;
            try (PreparedStatement ps = conn.prepareStatement(querySelect)) {
                ps.setString(1, r.getService());
                ps.setString(2, r.getModel());
                ps.setString(3, r.getModel());
                ps.setString(4, r.getDateStr());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        existingId = rs.getInt("id");
                    }
                }
            }

            if (existingId != null) {
                try (PreparedStatement ps = conn.prepareStatement(queryUpdate)) {
                    ps.setInt(1, r.getTokensIn());
                    ps.setInt(2, r.getTokensOut());
                    ps.setInt(3, r.getRequests());
                    ps.setDouble(4, r.getCostUsd());
                    ps.setString(5, r.getExtraJson());
                    ps.setString(6, syncedAt);
                    ps.setInt(7, existingId);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(queryInsert)) {
                    ps.setString(1, r.getService());
                    ps.setString(2, r.getModel());
                    ps.setString(3, r.getDateStr());
                    ps.setInt(4, r.getTokensIn());
                    ps.setInt(5, r.getTokensOut());
                    ps.setInt(6, r.getRequests());
                    ps.setDouble(7, r.getCostUsd());
                    ps.setString(8, r.getExtraJson());
                    ps.setString(9, syncedAt);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("upsertSnapshot 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public List<UsageRecord> getSnapshots(String service, int days) {
        List<UsageRecord> list = new ArrayList<>();
        String startDate = LocalDate.now().minusDays(days).toString();
        String sql = "SELECT * FROM usage_snapshots WHERE service = ? AND date_str >= ? ORDER BY date_str ASC, model ASC";

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, service);
            ps.setString(2, startDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UsageRecord r = new UsageRecord();
                    r.setId(rs.getInt("id"));
                    r.setService(rs.getString("service"));
                    r.setModel(rs.getString("model"));
                    r.setDateStr(rs.getString("date_str"));
                    r.setTokensIn(rs.getInt("tokens_in"));
                    r.setTokensOut(rs.getInt("tokens_out"));
                    r.setRequests(rs.getInt("requests"));
                    r.setCostUsd(rs.getDouble("cost_usd"));
                    r.setExtraJson(rs.getString("extra_json"));
                    r.setSyncedAt(rs.getString("synced_at"));
                    list.add(r);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }


    public Map<String, Object> getSummary(String service) {
        Map<String, Object> summary = new HashMap<>();
        String monthStart = LocalDate.now().withDayOfMonth(1).toString();
        String sql = "SELECT SUM(tokens_in) AS tokens_in, SUM(tokens_out) AS tokens_out, " +
                     "SUM(requests) AS requests, SUM(cost_usd) AS cost_usd, MAX(synced_at) AS last_sync " +
                     "FROM usage_snapshots WHERE service = ? AND date_str >= ?";

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, service);
            ps.setString(2, monthStart);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    summary.put("tokens_in", rs.getInt("tokens_in"));
                    summary.put("tokens_out", rs.getInt("tokens_out"));
                    summary.put("requests", rs.getInt("requests"));
                    summary.put("cost_usd", rs.getDouble("cost_usd"));
                    summary.put("last_sync", rs.getString("last_sync"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return summary;
    }


    public Double getPreviousBalance(String service, String beforeDate) {
        String sql = "SELECT extra_json FROM usage_snapshots WHERE service = ? AND model = 'deepseek-api' " +
                     "AND date_str < ? ORDER BY date_str DESC, id DESC LIMIT 1";
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, service);
            ps.setString(2, beforeDate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String extra = rs.getString("extra_json");
                    if (extra != null && extra.contains("total_balance")) {
                        com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(extra).getAsJsonObject();
                        if (obj.has("total_balance")) {
                            return obj.get("total_balance").getAsDouble();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }


    public boolean hasSnapshotForModelAndDate(String service, String model, String dateStr) {
        String sql = "SELECT id FROM usage_snapshots WHERE service = ? AND model = ? AND date_str = ? LIMIT 1";
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, service);
            ps.setString(2, model);
            ps.setString(3, dateStr);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }


    public void updateDeepSeekDailyConsumed() {
        String sql = "SELECT id, date_str, extra_json FROM usage_snapshots " +
                     "WHERE service = 'deepseek_api' AND model = 'deepseek-api' ORDER BY date_str ASC";
        List<Integer> ids = new ArrayList<>();
        List<String> extras = new ArrayList<>();

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            Double prevBalance = null;
            while (rs.next()) {
                int id = rs.getInt("id");
                String extraStr = rs.getString("extra_json");
                if (extraStr == null) continue;

                com.google.gson.JsonObject extra = com.google.gson.JsonParser.parseString(extraStr).getAsJsonObject();
                if (!extra.has("total_balance")) continue;

                double currentBalance = extra.get("total_balance").getAsDouble();
                double dailyConsumed = 0.0;
                if (prevBalance != null && prevBalance > currentBalance) {
                    dailyConsumed = prevBalance - currentBalance;
                }
                extra.addProperty("daily_consumed", dailyConsumed);
                prevBalance = currentBalance;

                ids.add(id);
                extras.add(extra.toString());
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        String updateSql = "UPDATE usage_snapshots SET extra_json = ? WHERE id = ?";
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setString(1, extras.get(i));
                ps.setInt(2, ids.get(i));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public String getLatestExtraJson(String service) {
        String sql = "SELECT extra_json FROM usage_snapshots WHERE service = ? ORDER BY date_str DESC, id DESC LIMIT 1";
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, service);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("extra_json");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }


    public void setAuthStatus(String service, boolean isAuth, String errorMsg) {
        String sql = "INSERT INTO auth_status (service, is_auth, last_check, error_msg) VALUES (?, ?, ?, ?) " +
                     "ON CONFLICT(service) DO UPDATE SET is_auth = excluded.is_auth, last_check = excluded.last_check, error_msg = excluded.error_msg";


        String mysqlSql = "INSERT INTO auth_status (service, is_auth, last_check, error_msg) VALUES (?, ?, ?, ?) " +
                          "ON DUPLICATE KEY UPDATE is_auth = VALUES(is_auth), last_check = VALUES(last_check), error_msg = VALUES(error_msg)";

        String lastCheck = LocalDateTime.now().toString();
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(mysqlSql)) {
            ps.setString(1, service);
            ps.setInt(2, isAuth ? 1 : 0);
            ps.setString(3, lastCheck);
            ps.setString(4, errorMsg);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public Map<String, Object> getAuthStatus(String service) {
        Map<String, Object> map = new HashMap<>();
        String sql = "SELECT * FROM auth_status WHERE service = ?";
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, service);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    map.put("service", rs.getString("service"));
                    map.put("is_auth", rs.getInt("is_auth"));
                    map.put("last_check", rs.getString("last_check"));
                    map.put("error_msg", rs.getString("error_msg"));
                } else {
                    map.put("service", service);
                    map.put("is_auth", 0);
                    map.put("error_msg", "");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }


    private static final String ALGORITHM = "AES";
    private static final byte[] KEY_VAL = "AiUsageMonKey123".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private String encrypt(String value) {
        if (value == null) return null;
        try {
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(KEY_VAL, ALGORITHM);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(ALGORITHM);
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            e.printStackTrace();
            return value;
        }
    }

    private String decrypt(String value) {
        if (value == null) return null;
        try {
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(KEY_VAL, ALGORITHM);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(ALGORITHM);
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(value));
            return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 如果原先存的是明文解密失败，直接返回原值做兼容
            return value;
        }
    }

    public void setApiKey(String service, String apiKey) {
        String sql = "INSERT INTO api_keys (service, api_key) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE api_key = VALUES(api_key)";
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, service);
            ps.setString(2, encrypt(apiKey));
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public String getApiKey(String service) {
        String sql = "SELECT api_key FROM api_keys WHERE service = ?";
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, service);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return decrypt(rs.getString("api_key"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }


    public void deleteApiKey(String service) {
        String sql = "DELETE FROM api_keys WHERE service = ?";
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, service);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void saveConfig(String key, String value) {
        String sql = "INSERT INTO config_settings (cfg_key, cfg_value) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE cfg_value = VALUES(cfg_value)";
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public Map<String, String> getConfigs() {
        Map<String, String> configs = new HashMap<>();
        String sql = "SELECT * FROM config_settings";
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                configs.put(rs.getString("cfg_key"), rs.getString("cfg_value"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return configs;
    }
}
