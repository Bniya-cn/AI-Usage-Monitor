package com.monitor.service.collectors;

import com.monitor.dao.UsageDao;
import com.monitor.model.UsageRecord;

import java.util.List;


public abstract class BaseCollector {
    protected final UsageDao usageDao = new UsageDao();


    public abstract String getServiceId();


    public abstract String getDisplayName();


    public abstract List<UsageRecord> collect() throws Exception;


    public boolean isAvailable() {
        String key = usageDao.getApiKey(getServiceId());
        return key != null && !key.trim().isEmpty();
    }
}
