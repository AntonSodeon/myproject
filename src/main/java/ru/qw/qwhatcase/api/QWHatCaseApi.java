package ru.qw.qwhatcase.api;

import ru.qw.qwhatcase.storage.BalanceChange;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Публичный API для других плагинов (магазин, квесты, голосование).
 * Получение: {@code Bukkit.getServicesManager().load(QWHatCaseApi.class)}.
 * Все методы асинхронные: результат приходит после фиксации транзакции в БД
 * (future завершается в потоке БД — не трогайте из него мир/инвентари без перехода в основной поток).
 * Все изменения записываются в журнал действий с указанным source.
 */
public interface QWHatCaseApi {
    Set<String> caseIds();

    Set<String> hatIds();

    /** Ключи для кейса (ключи хранятся по типу ключа кейса). */
    CompletableFuture<Long> getKeys(UUID player, String caseId);

    CompletableFuture<BalanceChange> giveKeys(UUID player, String caseId, long amount, String source);

    /** Списывает до amount ключей; баланс не становится отрицательным. changed() — сколько реально списано. */
    CompletableFuture<BalanceChange> takeKeys(UUID player, String caseId, long amount, String source);

    CompletableFuture<BalanceChange> setKeys(UUID player, String caseId, long amount, String source);

    CompletableFuture<Long> getTokens(UUID player);

    CompletableFuture<BalanceChange> giveTokens(UUID player, long amount, String source);

    CompletableFuture<BalanceChange> takeTokens(UUID player, long amount, String source);

    CompletableFuture<Boolean> hasHat(UUID player, String hatId);

    /** @return true, если шляпа добавлена; false — уже была. */
    CompletableFuture<Boolean> grantHat(UUID player, String hatId, String source);

    CompletableFuture<Boolean> revokeHat(UUID player, String hatId, String source);
}
