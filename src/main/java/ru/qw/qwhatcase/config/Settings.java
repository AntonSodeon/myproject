package ru.qw.qwhatcase.config;

import java.util.List;
import java.util.Map;

/** Общие настройки из config.yml. */
public record Settings(
        String databaseFile,
        HelmetPolicy helmetPolicy,
        PackMode packMode, String packUrl, String packSha1, boolean packRequired, String packPrompt,
        boolean blockCasesUntilLoaded,
        boolean allowSkipAnimation, long clickCooldownMs,
        boolean announcementEnabled, long announcementMinutes, String announcementMessage,
        boolean externalShopEnabled, String externalShopUrl,
        List<String> migrationSources, LegacyItems legacyItems,
        int historyPageSize,
        Map<String, Integer> hatEnchantments, Map<String, Double> hatAttributes, boolean hatGlint,
        boolean bookEnchanting,
        boolean donateShopEnabled, String donatePrice, String telegram) {

    public enum LegacyItems {
        /** Как в PTrap: предметы шляп из инвентаря попадают в коллекцию (с чарами) и убираются. */
        IMPORT,
        /** Убирать только предметы шляп, которые уже есть в коллекции. */
        CONVERT,
        /** Не трогать старые предметы. */
        IGNORE
    }

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
