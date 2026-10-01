package ru.qw.qwhatcase.config;

import org.junit.jupiter.api.Test;
import ru.qw.qwhatcase.TestCatalogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @Test
    void bundledConfigurationLoadsWithoutProblemsExceptMissingPackUrl() throws Exception {
        Catalog catalog = TestCatalogs.bundled();
        assertEquals(281, catalog.hats().size(), "все шляпы старого плагина (CMD 90..370)");
        assertEquals(3, catalog.cases().size());
        assertEquals(3, catalog.enabledCases().size());
        // Единственное ожидаемое замечание — не задан URL ресурс-пака.
        assertEquals(1, catalog.problems().size(), catalog.problems().toString());
        assertTrue(catalog.problems().get(0).contains("resource-pack.url"));
        assertEquals(Settings.PackMode.NONE, catalog.settings().packMode());
    }

    @Test
    void everyOldIdMapsToExactlyOneHatWithSameModel() throws Exception {
        Catalog catalog = TestCatalogs.bundled();
        for (int cmd = 90; cmd <= 370; cmd++) {
            Hat hat = catalog.byLegacy("hat_" + cmd).orElseThrow(() -> new AssertionError("нет сопоставления"));
            assertEquals(cmd, hat.customModelData(), "модель должна сохраниться для " + hat.id());
            assertEquals("minecraft:carved_pumpkin", hat.itemModel());
        }
        assertEquals("halo_shiny", catalog.byLegacy("hat_90").orElseThrow().id());
        assertTrue(catalog.byLegacy("hat_89").isEmpty());
        assertTrue(catalog.byLegacy("hat_371").isEmpty());
    }

    @Test
    void probabilitiesFollowFormula() throws Exception {
        Catalog catalog = TestCatalogs.bundled();
        for (CaseDef def : catalog.cases().values()) {
            double sum = def.rewards().stream().mapToDouble(def::chancePercent).sum();
            assertEquals(100.0, sum, 1e-9, def.id());
            for (CaseReward reward : def.rewards()) {
                assertTrue(reward.weight() > 0);
                assertEquals(reward.weight() / def.totalWeight() * 100.0, def.chancePercent(reward), 1e-12);
            }
        }
    }

    private static final String HATS = """
            hats:
              a: {name: A, rarity: common, custom-model-data: 1}
              b: {name: B, rarity: common, custom-model-data: 2}
              c: {name: C, rarity: rare, custom-model-data: 3, compensation: 7}
            """;
    private static final String CONFIG = """
            rarities:
              common: {name: Обычная, compensation: 5, order: 0}
              rare: {name: Редкая, compensation: 25, order: 2}
            """;

    @Test
    void exampleWeights70_25_5() throws Exception {
        String cases = """
                cases:
                  t:
                    rewards:
                      - {hat: a, weight: 70}
                      - {hat: b, weight: 25}
                      - {hat: c, weight: 5}
                """;
        Catalog catalog = new ConfigLoader().load(TestCatalogs.yaml(CONFIG), TestCatalogs.yaml(HATS),
                TestCatalogs.yaml(cases), TestCatalogs.yaml(""));
        CaseDef def = catalog.caseDef("t").orElseThrow();
        assertEquals(70.0, def.chancePercent(def.rewards().get(0)), 1e-12);
        assertEquals(25.0, def.chancePercent(def.rewards().get(1)), 1e-12);
        assertEquals(5.0, def.chancePercent(def.rewards().get(2)), 1e-12);
        assertEquals(7, def.compensationFor(catalog.hat("c").orElseThrow()), "компенсация шляпы перекрывает редкость");
        assertEquals(5, def.compensationFor(catalog.hat("a").orElseThrow()), "компенсация из редкости");
    }

    @Test
    void brokenCaseIsDisabledOthersKeepWorking() throws Exception {
        String cases = """
                cases:
                  good:
                    rewards: [{hat: a, weight: 1}]
                  negative:
                    rewards: [{hat: a, weight: -5}]
                  unknown_hat:
                    rewards: [{hat: nope, weight: 1}]
                  empty: {}
                  zero_cost:
                    key-cost: 0
                    rewards: [{hat: a, weight: 1}]
                """;
        Catalog catalog = new ConfigLoader().load(TestCatalogs.yaml(CONFIG), TestCatalogs.yaml(HATS),
                TestCatalogs.yaml(cases), TestCatalogs.yaml(""));
        assertEquals(1, catalog.cases().size());
        assertTrue(catalog.caseDef("good").isPresent());
        assertEquals(4, catalog.problems().stream().filter(p -> p.startsWith("Кейс")).count(), catalog.problems().toString());
    }

    @Test
    void duplicatedRewardLinesSumWeights() throws Exception {
        String cases = """
                cases:
                  t:
                    rewards:
                      - {pool: {rarity: common}, weight: 1}
                      - {hat: a, weight: 2}
                """;
        Catalog catalog = new ConfigLoader().load(TestCatalogs.yaml(CONFIG), TestCatalogs.yaml(HATS),
                TestCatalogs.yaml(cases), TestCatalogs.yaml(""));
        CaseDef def = catalog.caseDef("t").orElseThrow();
        assertEquals(2, def.rewards().size());
        assertEquals(3.0, def.rewards().stream().filter(r -> r.hat().id().equals("a")).findFirst().orElseThrow().weight());
    }

    @Test
    void fatalErrorsAreReportedNotSilentlyAccepted() {
        assertThrows(ConfigLoader.ConfigException.class, () -> new ConfigLoader().load(TestCatalogs.yaml("rarities: {}"),
                TestCatalogs.yaml(HATS), TestCatalogs.yaml(""), TestCatalogs.yaml("")));
        assertThrows(ConfigLoader.ConfigException.class, () -> new ConfigLoader().load(TestCatalogs.yaml(CONFIG),
                TestCatalogs.yaml("hats: {}"), TestCatalogs.yaml(""), TestCatalogs.yaml("")));
        assertThrows(ConfigLoader.ConfigException.class, () -> new ConfigLoader().load(
                TestCatalogs.yaml(CONFIG + "resource-pack: {sha1: xyz}\n"),
                TestCatalogs.yaml(HATS), TestCatalogs.yaml(""), TestCatalogs.yaml("")));
    }

    @Test
    void unavailableHatIsNotDroppedButStaysInCatalog() throws Exception {
        String hats = HATS + "  d: {name: D, rarity: common, custom-model-data: 4, available: false}\n";
        String cases = """
                cases:
                  t:
                    rewards:
                      - {pool: {rarity: common}, weight: 1}
                """;
        Catalog catalog = new ConfigLoader().load(TestCatalogs.yaml(CONFIG), TestCatalogs.yaml(hats),
                TestCatalogs.yaml(cases), TestCatalogs.yaml("rarity-prices: {common: 10}"));
        assertTrue(catalog.hat("d").isPresent());
        assertFalse(catalog.caseDef("t").orElseThrow().rewards().stream().anyMatch(r -> r.hat().id().equals("d")));
        assertFalse(catalog.shopHats().stream().anyMatch(h -> h.id().equals("d")));
        assertEquals(2, catalog.shopHats().size());
    }
}
