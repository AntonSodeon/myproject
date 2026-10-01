package ru.qw.qwhatcase.service;

import org.junit.jupiter.api.Test;
import ru.qw.qwhatcase.TestCatalogs;
import ru.qw.qwhatcase.config.Catalog;
import ru.qw.qwhatcase.config.Hat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyParserTest {
    /** Формат players.yml, который пишет PTrap 1.1.19 (ItemStack сериализуется картой с ключом "=="). */
    private static final String PLAYERS = """
            0a0b0c0d-0000-0000-0000-000000000001:
              name: Alice
              hats:
                hat_90:
                  ==: org.bukkit.inventory.ItemStack
                  DataVersion: 4440
                  id: minecraft:carved_pumpkin
                  count: 1
                  components:
                    minecraft:custom_model_data: '{floats:[90.0f]}'
                hat_370:
                  ==: org.bukkit.inventory.ItemStack
                  id: minecraft:carved_pumpkin
                hat_5000:
                  ==: org.bukkit.inventory.ItemStack
                  id: minecraft:carved_pumpkin
            0a0b0c0d-0000-0000-0000-000000000002:
              name: Bob
            not-a-uuid:
              name: Broken
            """;

    @Test
    void parsesOldFormatAndReportsUnknown() throws Exception {
        Catalog catalog = TestCatalogs.bundled();
        LegacyParser.Plan plan = LegacyParser.parse(PLAYERS, id -> catalog.byLegacy(id).map(Hat::id));
        assertEquals(1, plan.players().size(), "у Боба нет шляп — пропущен");
        assertEquals(3, plan.hats());
        assertEquals(2, plan.mapped());
        assertEquals(1, plan.unknown());
        var alice = plan.players().get(0);
        assertEquals("Alice", alice.name());
        assertEquals(java.util.List.of("halo_shiny", "gearhead_shiny"), alice.hatIds());
        assertEquals("hat_5000", alice.unknown().get(0).rawKey());
        assertTrue(alice.unknown().get(0).rawValue().contains("carved_pumpkin"), "исходная запись сохранена");
        assertTrue(plan.problems().stream().anyMatch(p -> p.contains("not-a-uuid")));
    }

    @Test
    void emptyFileFromOldPlugin() {
        LegacyParser.Plan plan = LegacyParser.parse("{}\n", id -> java.util.Optional.empty());
        assertEquals(0, plan.players().size());
    }
}
