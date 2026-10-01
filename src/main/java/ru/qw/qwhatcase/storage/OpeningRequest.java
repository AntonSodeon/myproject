package ru.qw.qwhatcase.storage;

import java.util.UUID;

/**
 * Запрос на открытие кейса. Награда уже определена до транзакции,
 * транзакция только фиксирует её вместе со списанием ключей.
 *
 * @param duplicateTokens жетоны, которые будут начислены, если шляпа уже есть
 */
public record OpeningRequest(UUID operationId, UUID player, String playerName, String caseId, String keyType,
                             int keyCost, String hatId, long duplicateTokens, long createdAt, String point) {
    /** Открытие без точки в мире (команда/меню). */
    public OpeningRequest(UUID operationId, UUID player, String playerName, String caseId, String keyType,
                          int keyCost, String hatId, long duplicateTokens, long createdAt) {
        this(operationId, player, playerName, caseId, keyType, keyCost, hatId, duplicateTokens, createdAt, null);
    }
}
