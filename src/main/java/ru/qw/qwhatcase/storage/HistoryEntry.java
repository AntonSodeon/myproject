package ru.qw.qwhatcase.storage;

import java.util.UUID;

public record HistoryEntry(UUID operationId, UUID player, String caseId, String hatId, int keysSpent,
                           String outcome, long tokensAwarded, long tokensBalance, long createdAt, boolean shown) {
}
