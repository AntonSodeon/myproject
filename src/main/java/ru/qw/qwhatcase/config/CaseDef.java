package ru.qw.qwhatcase.config;

import java.util.List;

/**
 * Неизменяемый снимок настроек кейса. Начатое открытие держит ссылку на свой снимок,
 * поэтому /qwhatcase reload не влияет на уже выполняемую операцию.
 */
public record CaseDef(String id, boolean enabled, String name, List<String> description,
                      String iconMaterial, String iconItemModel, int iconCustomModelData,
                      String keyType, int keyCost, String permission,
                      AnimationSettings animation, String winMessage,
                      boolean broadcastEnabled, int broadcastMinRarity, String broadcastMessage,
                      double duplicateMultiplier, long duplicateBonus,
                      List<CaseReward> rewards, double totalWeight) {

    /** Фактическая вероятность награды в процентах: вес / сумма весов × 100. */
    public double chancePercent(CaseReward reward) {
        return reward.weight() / totalWeight * 100.0;
    }

    /** Жетоны за дубликат по правилам этого кейса. */
    public long compensationFor(Hat hat) {
        return Math.max(0L, Math.round(hat.compensation() * duplicateMultiplier) + duplicateBonus);
    }
}
