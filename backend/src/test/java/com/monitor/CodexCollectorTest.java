package com.monitor;

import com.monitor.model.UsageRecord;
import com.monitor.service.collectors.CodexCollector;

import java.util.List;

public class CodexCollectorTest {
    public static void main(String[] args) throws Exception {
        CodexCollector collector = new CodexCollector();
        System.out.println("available=" + collector.isAvailable());
        List<UsageRecord> records = collector.collect();
        System.out.println("records=" + records.size());
        for (UsageRecord r : records) {
            System.out.println(r.getDateStr() + " req=" + r.getRequests() + " tok=" + (r.getTokensIn() + r.getTokensOut()) + " extra=" + r.getExtraJson());
        }
    }
}