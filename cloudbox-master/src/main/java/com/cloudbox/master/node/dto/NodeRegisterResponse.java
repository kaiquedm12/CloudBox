package com.cloudbox.master.node.dto;

import java.util.UUID;

public record NodeRegisterResponse(UUID id, String token) {
}