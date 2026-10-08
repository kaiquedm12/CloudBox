package com.cloudbox.agent.client;

import java.util.List;

public record AgentCapabilities(int contractVersion, List<String> features,
                                boolean ephemeralDiskQuota, boolean volumeQuota) {
    public static final AgentCapabilities CURRENT = new AgentCapabilities(2,
            List.of("ENVIRONMENT", "COMMAND_ARGS", "RESTART_POLICY"), false, false);

    public AgentCapabilities {
        features = List.copyOf(features);
    }
}
