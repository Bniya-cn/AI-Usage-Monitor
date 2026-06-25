package com.monitor;

import java.util.Arrays;
import java.util.List;


public final class MonitorServices {

    public static final String[] ALL = {
            "chatgpt_api", "claude_api", "kimi_api", "glm_api",
            "deepseek_api", "claude_code", "grok_build", "codex"
    };

    public static final List<String> LOCAL_COLLECTORS = Arrays.asList(
            "claude_code", "grok_build", "codex"
    );

    public static boolean isLocalCollector(String serviceId) {
        return LOCAL_COLLECTORS.contains(serviceId);
    }

    private MonitorServices() {}
}