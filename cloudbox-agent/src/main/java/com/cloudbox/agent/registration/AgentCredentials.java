package com.cloudbox.agent.registration;

import java.util.UUID;

public record AgentCredentials(UUID nodeId, String token) {
}
