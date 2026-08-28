package com.cloudbox.master.auth.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn) {
}
