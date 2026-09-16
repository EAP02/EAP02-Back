package com.codefactory.devnet.profile.domain;

public class InvalidProfileException extends RuntimeException {
    private final String field;
    public InvalidProfileException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
