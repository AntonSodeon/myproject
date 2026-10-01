package ru.qw.qwhatcase.storage;

import java.util.UUID;

public record OpeningResult(Status status, UUID operationId, String caseId, String hatId, Outcome outcome,
                            long tokensAwarded, long tokensBalance, long keysBalance, long createdAt) {

    public enum Status {
        /** Ключи списаны, результат сохранён. */
        SUCCESS,
        /** Эта операция уже была выполнена ранее — повторно ничего не списано и не выдано. */
        ALREADY_PROCESSED,
        /** Не хватает ключей — ничего не изменено. */
        NOT_ENOUGH_KEYS,
        /** Ошибка БД — транзакция откатана, ничего не изменено. */
        ERROR
    }

    public enum Outcome {
        NEW, DUPLICATE
    }

    public static OpeningResult failure(Status status, OpeningRequest request, long keysBalance) {
        return new OpeningResult(status, request.operationId(), request.caseId(), request.hatId(), null,
                0, 0, keysBalance, request.createdAt());
    }
}
