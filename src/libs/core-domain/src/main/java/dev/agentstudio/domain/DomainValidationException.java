package dev.agentstudio.domain;

public final class DomainValidationException extends IllegalArgumentException {
    public DomainValidationException(String message) { super(message); }
}
