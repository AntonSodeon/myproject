package ru.qw.qwhatcase.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Вызывается в основном потоке ПОСЛЕ того, как результат открытия сохранён в БД.
 * Событие информационное: отменить или изменить выигрыш нельзя.
 */
public final class CaseOpenedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final UUID operationId;
    private final String caseId;
    private final String hatId;
    private final boolean duplicate;
    private final long tokensAwarded;

    public CaseOpenedEvent(Player player, UUID operationId, String caseId, String hatId, boolean duplicate, long tokensAwarded) {
        this.player = player;
        this.operationId = operationId;
        this.caseId = caseId;
        this.hatId = hatId;
        this.duplicate = duplicate;
        this.tokensAwarded = tokensAwarded;
    }

    public Player getPlayer() {
        return player;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getHatId() {
        return hatId;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public long getTokensAwarded() {
        return tokensAwarded;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
