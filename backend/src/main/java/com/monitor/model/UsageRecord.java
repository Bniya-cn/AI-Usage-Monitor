package com.monitor.model;


public class UsageRecord {
    private int id;
    private String service;
    private String model;
    private String dateStr;
    private int tokensIn;
    private int tokensOut;
    private int requests;
    private double costUsd;
    private String extraJson;
    private String syncedAt;

    public UsageRecord() {}

    public UsageRecord(String service, String model, String dateStr, int tokensIn, int tokensOut, int requests, double costUsd, String extraJson) {
        this.service = service;
        this.model = model;
        this.dateStr = dateStr;
        this.tokensIn = tokensIn;
        this.tokensOut = tokensOut;
        this.requests = requests;
        this.costUsd = costUsd;
        this.extraJson = extraJson;
    }


    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getDateStr() { return dateStr; }
    public void setDateStr(String dateStr) { this.dateStr = dateStr; }

    public int getTokensIn() { return tokensIn; }
    public void setTokensIn(int tokensIn) { this.tokensIn = tokensIn; }

    public int getTokensOut() { return tokensOut; }
    public void setTokensOut(int tokensOut) { this.tokensOut = tokensOut; }

    public int getRequests() { return requests; }
    public void setRequests(int requests) { this.requests = requests; }

    public double getCostUsd() { return costUsd; }
    public void setCostUsd(double costUsd) { this.costUsd = costUsd; }

    public String getExtraJson() { return extraJson; }
    public void setExtraJson(String extraJson) { this.extraJson = extraJson; }

    public String getSyncedAt() { return syncedAt; }
    public void setSyncedAt(String syncedAt) { this.syncedAt = syncedAt; }
}
