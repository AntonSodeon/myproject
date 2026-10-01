package ru.qw.qwhatcase.service;

import org.junit.jupiter.api.Test;
import ru.qw.qwhatcase.TestCatalogs;
import ru.qw.qwhatcase.config.CaseDef;
import ru.qw.qwhatcase.config.Catalog;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Статистическая проверка: много розыгрышей, критерий хи-квадрат. */
class DistributionTest {

    @Test
    void bundledCasesMatchConfiguredProbabilities() throws Exception {
        Catalog catalog = TestCatalogs.bundled();
        RewardRoller roller = new RewardRoller(new SplittableRandom(20261001L));
        for (CaseDef def : catalog.cases().values()) {
            DistributionCheck.Report report = DistributionCheck.run(def, roller, 3_000_000);
            System.out.printf("%s: наград %d, χ²=%.1f df=%d z=%.2f макс.откл=%.4f п.п.%n", def.id(), def.rewards().size(),
                    report.chiSquare(), report.degreesOfFreedom(), report.z(), report.maxDeviationPp());
            assertTrue(report.passed(), def.id() + ": z=" + report.z());
            assertTrue(report.maxDeviationPp() < 0.2, def.id() + ": " + report.maxDeviationPp());
        }
    }

    @Test
    void secureRandomUsedInGameAlsoPasses() throws Exception {
        Catalog catalog = TestCatalogs.bundled();
        RewardRoller roller = new RewardRoller(new java.security.SecureRandom());
        DistributionCheck.Report report = DistributionCheck.run(catalog.caseDef("premium").orElseThrow(), roller, 1_000_000);
        assertTrue(Math.abs(report.z()) < 4.5, "z=" + report.z());
    }
}
