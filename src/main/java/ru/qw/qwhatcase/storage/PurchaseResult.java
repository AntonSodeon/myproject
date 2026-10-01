package ru.qw.qwhatcase.storage;

public record PurchaseResult(Status status, long tokensBalance) {
    public enum Status {
        SUCCESS, ALREADY_OWNED, NOT_ENOUGH_TOKENS, ERROR
    }
}
