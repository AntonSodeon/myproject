package ru.qw.qwhatcase.service;

import ru.qw.qwhatcase.config.CaseDef;
import ru.qw.qwhatcase.config.CaseReward;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Программная проверка распределения: много розыгрышей тем же генератором, что и в игре,
 * затем критерий хи-квадрат (z-оценка по Уилсону—Хилферти).
 */
public final class DistributionCheck {
    public record Row(CaseReward reward, double expectedPercent, double actualPercent, long count) {
    }

    public record Report(long rolls, double chiSquare, int degreesOfFreedom, double z, double maxDeviationPp, List<Row> rows) {
        /** |z| < 3 — расхождение в пределах случайности (≈99.7%). */
        public boolean passed() {
            return Math.abs(z) < 3.0;
        }
    }

    private DistributionCheck() {
    }

    public static Report run(CaseDef def, RewardRoller roller, long rolls) {
        Map<String, Long> counts = new HashMap<>();
        for (long i = 0; i < rolls; i++) {
            counts.merge(roller.roll(def).hat().id(), 1L, Long::sum);
        }
        double chi = 0;
        double maxDev = 0;
        List<Row> rows = new java.util.ArrayList<>();
        for (CaseReward reward : def.rewards()) {
            long observed = counts.getOrDefault(reward.hat().id(), 0L);
            double p = reward.weight() / def.totalWeight();
            double expected = p * rolls;
            chi += (observed - expected) * (observed - expected) / expected;
            double actualPct = observed * 100.0 / rolls;
            maxDev = Math.max(maxDev, Math.abs(actualPct - p * 100));
            rows.add(new Row(reward, p * 100, actualPct, observed));
        }
        int df = Math.max(1, def.rewards().size() - 1);
        double z = (Math.cbrt(chi / df) - (1 - 2.0 / (9 * df))) / Math.sqrt(2.0 / (9 * df));
        return new Report(rolls, chi, df, z, maxDev, rows);
    }
}
