package com.cloudbox.agent.client;

import java.util.Set;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;
import com.fasterxml.jackson.annotation.JsonAnySetter;

public record RestartPolicySpec(String name, Integer maximumRetryCount,
                               @JsonAnySetter Map<String, Object> additionalOptions) {
    public static final RestartPolicySpec NONE = new RestartPolicySpec("NONE", 0);

    public RestartPolicySpec {
        maximumRetryCount = maximumRetryCount == null ? 0 : maximumRetryCount;
        additionalOptions = additionalOptions == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(additionalOptions));
    }

    public RestartPolicySpec(String name, Integer maximumRetryCount) {
        this(name, maximumRetryCount, null);
    }

    public void validate() {
        if (!additionalOptions.isEmpty() || name == null || !Set.of("NONE", "ALWAYS", "UNLESS_STOPPED", "ON_FAILURE").contains(name)
                || maximumRetryCount < 0 || maximumRetryCount > 100
                || (!"ON_FAILURE".equals(name) && maximumRetryCount != 0)) {
            throw new IllegalArgumentException("restartPolicy invalida ou maximumRetryCount incompativel");
        }
    }
}
