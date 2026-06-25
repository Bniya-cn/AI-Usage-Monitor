package com.monitor;

import com.monitor.model.UsageRecord;
import com.monitor.service.collectors.GrokCollector;

import java.util.List;

public class GrokCollectorTest {
    public static void main(String[] args) throws Exception {
        GrokCollector collector = new GrokCollector();
        System.out.println("available=" + collector.isAvailable());
        List<UsageRecord> records = collector.collect();
        System.out.println("records=" + records.size());
        for (UsageRecord r : records) {
            System.out.println(r.getDateStr() + " req=" + r.getRequests() + " tok=" + (r.getTokensIn() + r.getTokensOut()) + " sessions=" + r.getExtraJson());
        }
    }
}