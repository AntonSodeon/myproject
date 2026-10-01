package ru.qw.qwhatcase.service;

import ru.qw.qwhatcase.config.CaseDef;
import ru.qw.qwhatcase.config.CaseReward;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Честный розыгрыш по весам: P(награда) = вес / сумма весов.
 * Никаких поправок по игроку, числу покупок или времени нет.
 */
public final class RewardRoller {
    private final RandomGenerator random;

    public RewardRoller(RandomGenerator random) {
        this.random = random;
    }

    public CaseReward roll(CaseDef caseDef) {
        return pick(caseDef.rewards(), caseDef.totalWeight());
    }

    public CaseReward pick(List<CaseReward> rewards, double totalWeight) {
        double point = random.nextDouble() * totalWeight;
        double cumulative = 0;
        for (CaseReward reward : rewards) {
            cumulative += reward.weight();
            if (point < cumulative) {
                return reward;
            }
        }
        // Защита от погрешности округления double: последняя награда.
        return rewards.get(rewards.size() - 1);
    }
}
