package com.codefactory.eap02.auth.DTO;

public record ErrorResponse(String errorCode, String message, Long retryAfterSeconds) {}
