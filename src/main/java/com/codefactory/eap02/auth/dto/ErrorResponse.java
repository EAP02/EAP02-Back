package com.codefactory.eap02.auth.dto;

public record ErrorResponse(String errorCode, String message, Long retryAfterSeconds) {}
