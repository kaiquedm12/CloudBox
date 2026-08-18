package com.cloudbox.agent.client;

import java.util.UUID;

public record NodeRegisterResponse(UUID id, String token) {
}
