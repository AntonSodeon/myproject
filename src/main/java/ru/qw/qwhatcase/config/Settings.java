package ru.qw.qwhatcase.config;

import java.util.List;

/** Общие настройки из config.yml. */
public record Settings(
        String databaseFile,
        HelmetPolicy helmetPolicy,
        PackMode packMode, String packUrl, String packSha1, boolean packRequired, String packPrompt,
        boolean blockCasesUntilLoaded,
        boolean allowSkipAnimation, long clickCooldownMs,
        boolean announcementEnabled, long announcementMinutes, String announcementMessage,
        boolean externalShopEnabled, String externalShopUrl,
        List<String> migrationSources, boolean convertLegacyItems,
        int historyPageSize) {

    public enum HelmetPolicy {
        /** Настоящий шлем переносится в инвентарь; если места нет — отказ. */
        MOVE_TO_INVENTORY,
        /** Если надет настоящий шлем — отказ с сообщением. */
        REFUSE
    }

    public enum PackMode {
        /** Плагин сам отправляет пак при входе. */
        PLUGIN,
        /** Пак отправляет сервер (server.properties) или другой плагин; QWHatCase только следит за статусом. */
        SERVER,
        /** Без проверки пака. */
        NONE
    }
}
