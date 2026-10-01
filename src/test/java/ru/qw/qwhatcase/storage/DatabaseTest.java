package ru.qw.qwhatcase.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {
    @TempDir
    File dir;
    Database db;
    final UUID alice = UUID.randomUUID();
    final UUID bob = UUID.randomUUID();

    @BeforeEach
    void open() throws SQLException {
        db = new Database(new File(dir, "test.db"));
    }

    @AfterEach
    void close() throws SQLException {
        db.close();
    }

    private OpeningRequest request(UUID player, String hat, int cost, long dupTokens) {
        return new OpeningRequest(UUID.randomUUID(), player, "p", "basic", "basic", cost, hat, dupTokens, System.currentTimeMillis());
    }

    @Test
    void openingDeductsExactlyCostAndSavesResult() throws SQLException {
        db.addKeys(alice, "Alice", "basic", 5, "test");
        OpeningResult r = db.performOpening(request(alice, "halo", 2, 10));
        assertEquals(OpeningResult.Status.SUCCESS, r.status());
        assertEquals(OpeningResult.Outcome.NEW, r.outcome());
        assertEquals(3, db.getKeys(alice, "basic"));
        assertTrue(db.ownsHat(alice, "halo"));
        assertEquals(0, db.getTokens(alice));
        assertTrue(db.findOpening(r.operationId()).isPresent());
        assertEquals(1, db.unshown(alice).size(), "результат ждёт показа");
        db.markShown(r.operationId());
        assertEquals(0, db.unshown(alice).size());
    }

    @Test
    void notEnoughKeysChangesNothing() throws SQLException {
        db.addKeys(alice, "Alice", "basic", 1, "test");
        OpeningResult r = db.performOpening(request(alice, "halo", 2, 10));
        assertEquals(OpeningResult.Status.NOT_ENOUGH_KEYS, r.status());
        assertEquals(1, r.keysBalance());
        assertEquals(1, db.getKeys(alice, "basic"));
        assertFalse(db.ownsHat(alice, "halo"));
        assertEquals(0, db.historyCount(alice));
    }

    @Test
    void duplicateGivesCompensationExactlyOnce() throws SQLException {
        db.addKeys(alice, "Alice", "basic", 3, "test");
        db.performOpening(request(alice, "halo", 1, 25));
        OpeningResult dup = db.performOpening(request(alice, "halo", 1, 25));
        assertEquals(OpeningResult.Outcome.DUPLICATE, dup.outcome());
        assertEquals(25, dup.tokensAwarded());
        assertEquals(25, dup.tokensBalance());
        assertEquals(25, db.getTokens(alice));
        assertEquals(1, db.getKeys(alice, "basic"));
    }

    @Test
    void sameOperationIdIsNeverProcessedTwice() throws SQLException {
        db.addKeys(alice, "Alice", "basic", 5, "test");
        OpeningRequest req = request(alice, "halo", 1, 25);
        OpeningResult first = db.performOpening(req);
        OpeningResult second = db.performOpening(req);
        OpeningResult third = db.performOpening(req);
        assertEquals(OpeningResult.Status.SUCCESS, first.status());
        assertEquals(OpeningResult.Status.ALREADY_PROCESSED, second.status());
        assertEquals(OpeningResult.Status.ALREADY_PROCESSED, third.status());
        assertEquals(first.outcome(), second.outcome());
        assertEquals(4, db.getKeys(alice, "basic"), "ключ списан один раз");
        assertEquals(0, db.getTokens(alice), "компенсация не начислена повторно");
        assertEquals(1, db.historyCount(alice));
    }

    @Test
    void databaseFailureAtAnyStageRollsBackEverything() throws SQLException {
        db.addKeys(alice, "Alice", "basic", 5, "test");
        db.grantHat(alice, "Alice", "owned", "test", "test");
        for (String stage : List.of("opening.after-keys", "opening.after-reward", "opening.before-commit")) {
            for (String hat : List.of("fresh", "owned")) {
                db.setFaultHook(s -> {
                    if (s.equals(stage)) {
                        throw new SQLException("simulated failure at " + s);
                    }
                });
                OpeningResult r = db.performOpening(request(alice, hat, 2, 30));
                assertEquals(OpeningResult.Status.ERROR, r.status(), stage);
                db.setFaultHook(null);
                assertEquals(5, db.getKeys(alice, "basic"), "ключи не списаны: " + stage);
                assertFalse(db.ownsHat(alice, "fresh"), "шляпа не выдана: " + stage);
                assertEquals(0, db.getTokens(alice), "жетоны не начислены: " + stage);
                assertEquals(0, db.historyCount(alice), "история не записана: " + stage);
            }
        }
        // После сбоя база работает дальше.
        assertEquals(OpeningResult.Status.SUCCESS, db.performOpening(request(alice, "fresh", 2, 30)).status());
    }

    @Test
    void purchaseIsAtomicAndNotRepeatable() throws SQLException {
        db.addTokens(alice, "Alice", 100, "test", null);
        PurchaseResult first = db.purchase(alice, "Alice", "crown", 60);
        PurchaseResult second = db.purchase(alice, "Alice", "crown", 60);
        assertEquals(PurchaseResult.Status.SUCCESS, first.status());
        assertEquals(PurchaseResult.Status.ALREADY_OWNED, second.status());
        assertEquals(40, db.getTokens(alice));
        assertEquals(PurchaseResult.Status.NOT_ENOUGH_TOKENS, db.purchase(alice, "Alice", "halo", 60).status());
        assertFalse(db.ownsHat(alice, "halo"));

        db.setFaultHook(s -> {
            if (s.equals("purchase.after-tokens")) {
                throw new SQLException("boom");
            }
        });
        assertEquals(PurchaseResult.Status.ERROR, db.purchase(alice, "Alice", "cap", 10).status());
        db.setFaultHook(null);
        assertEquals(40, db.getTokens(alice), "жетоны не списаны при сбое");
        assertFalse(db.ownsHat(alice, "cap"));
    }

    @Test
    void balancesNeverNegative() throws SQLException {
        db.addKeys(alice, "Alice", "basic", 3, "test");
        BalanceChange taken = db.takeKeys(alice, "basic", 10, "admin");
        assertEquals(3, taken.changed());
        assertEquals(0, taken.balance());
        db.addTokens(alice, "Alice", 5, "admin", null);
        assertEquals(0, db.takeTokens(alice, 50, "admin").balance());
        assertEquals(0, db.takeTokens(bob, 50, "admin").balance(), "неизвестный игрок — ноль, без ошибок");
        BalanceChange set = db.setKeys(alice, "Alice", "basic", 7, "admin");
        assertEquals(7, set.balance());
    }

    @Test
    void concurrentKeyGrantsAndOpeningsDoNotLoseUpdates() throws Exception {
        // Как в плагине: все запросы идут через одну очередь БД, отправляются из многих потоков.
        ExecutorService dbThread = Executors.newSingleThreadExecutor();
        ExecutorService clients = Executors.newFixedThreadPool(16);
        List<CompletableFuture<OpeningResult>> opens = new ArrayList<>();
        List<CompletableFuture<?>> all = new ArrayList<>();
        int grants = 200;
        int attempts = 300;
        for (int i = 0; i < grants; i++) {
            all.add(CompletableFuture.runAsync(() -> {
                try {
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            return db.addKeys(alice, "Alice", "basic", 1, "shop");
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    }, dbThread).join();
                } catch (RuntimeException e) {
                    throw e;
                }
            }, clients));
        }
        for (int i = 0; i < attempts; i++) {
            UUID player = i % 2 == 0 ? alice : bob;
            String hat = "hat" + (i % 7);
            CompletableFuture<OpeningResult> f = CompletableFuture.supplyAsync(
                    () -> CompletableFuture.supplyAsync(() -> db.performOpening(request(player, hat, 1, 3)), dbThread).join(), clients);
            opens.add(f);
            all.add(f);
        }
        CompletableFuture.allOf(all.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        long aliceSuccess = opens.stream().map(CompletableFuture::join)
                .filter(r -> r.status() == OpeningResult.Status.SUCCESS)
                .filter(r -> {
                    try {
                        return db.historyEntry(r.operationId()).orElseThrow().player().equals(alice);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }).count();
        long bobSuccess = opens.stream().map(CompletableFuture::join)
                .filter(r -> r.status() == OpeningResult.Status.SUCCESS).count() - aliceSuccess;
        assertEquals(0, bobSuccess, "у Боба нет ключей — результаты игроков не смешиваются");
        assertEquals(grants - aliceSuccess, db.getKeys(alice, "basic"), "выдано − списано = баланс");
        assertEquals(aliceSuccess, db.historyCount(alice));
        long dups = db.history(alice, 1000, 0).stream().filter(h -> h.outcome().equals("DUPLICATE")).count();
        assertEquals(dups * 3, db.getTokens(alice), "каждый дубликат дал компенсацию ровно один раз");
        assertEquals(0, db.getKeys(bob, "basic"));
        dbThread.shutdown();
        clients.shutdown();
    }

    @Test
    void dataSurvivesRestart() throws SQLException {
        db.addKeys(alice, "Alice", "basic", 4, "test");
        db.addTokens(alice, "Alice", 9, "test", null);
        db.grantHat(alice, "Alice", "halo", "test", "test");
        db.setSelectedHat(alice, "halo");
        db.close();
        db = new Database(new File(dir, "test.db"));
        PlayerData data = db.load(alice);
        assertEquals(4, data.keys().get("basic"));
        assertEquals(9, data.tokens());
        assertTrue(data.owned().containsKey("halo"));
        assertEquals("halo", data.selectedHat());
        assertEquals(alice, db.findByName("alice").orElseThrow());
    }

    @Test
    void revokeClearsSelection() throws SQLException {
        db.grantHat(alice, "Alice", "halo", "test", "test");
        db.setSelectedHat(alice, "halo");
        assertTrue(db.revokeHat(alice, "halo", "admin"));
        assertEquals(null, db.load(alice).selectedHat());
        assertFalse(db.revokeHat(alice, "halo", "admin"));
    }

    @Test
    void migrationIsIdempotentAndKeepsUnknownRecords() throws SQLException {
        List<LegacyImport> players = List.of(
                new LegacyImport(alice, "Alice", List.of("halo", "cap"), List.of(new LegacyImport.Unknown("hat_999", "hat_999: {}"))),
                new LegacyImport(bob, "Bob", List.of("cap"), List.of()));
        Database.ImportStats first = db.importLegacy("plugins/PTrap/players.yml", players, "console", "backup", null);
        Database.ImportStats second = db.importLegacy("plugins/PTrap/players.yml", players, "console", "backup", null);
        assertEquals(3, first.hatsAdded());
        assertEquals(0, second.hatsAdded(), "повторный запуск не создаёт дубликатов");
        assertEquals(2, db.load(alice).owned().size());
        assertEquals(1, db.load(bob).owned().size());
        assertEquals(2, db.migrationRuns("plugins/PTrap/players.yml"));

        db.setFaultHook(s -> {
            if (s.equals("migration.before-commit")) {
                throw new SQLException("boom");
            }
        });
        UUID carol = UUID.randomUUID();
        try {
            db.importLegacy("x", List.of(new LegacyImport(carol, "Carol", List.of("halo"), List.of())), "c", null, null);
        } catch (SQLException expected) {
            // ok
        }
        db.setFaultHook(null);
        assertTrue(db.load(carol).owned().isEmpty(), "сбой миграции откатывает всё");
    }

    @Test
    void casePoints() throws SQLException {
        db.addPoint(new CasePoint("world", 1, 64, -3, "basic"), "admin");
        db.addPoint(new CasePoint("world", 1, 64, -3, "premium"), "admin");
        assertEquals(1, db.points().size());
        assertEquals("premium", db.points().get(0).caseId());
        assertTrue(db.removePoint("world", 1, 64, -3, "admin"));
        assertTrue(db.points().isEmpty());
    }

    @Test
    void bookEnchantsAreStoredAndOverrideLevels() throws SQLException {
        assertTrue(db.enchantHat(alice, "halo", java.util.Map.of("minecraft:sharpness", 5), "Alice").isEmpty(),
                "нельзя зачаровать шляпу, которой нет в коллекции");
        db.grantHat(alice, "Alice", "halo", "test", "test");
        var first = db.enchantHat(alice, "halo", java.util.Map.of("minecraft:sharpness", 5, "minecraft:thorns", 3), "Alice");
        assertEquals(java.util.Map.of("minecraft:sharpness", 5, "minecraft:thorns", 3), first.orElseThrow());
        var second = db.enchantHat(alice, "halo", java.util.Map.of("minecraft:sharpness", 2), "Alice");
        assertEquals(2, second.orElseThrow().get("minecraft:sharpness"), "уровень с книги заменяет прежний (как в PTrap)");
        assertEquals(3, db.load(alice).enchants().get("halo").get("minecraft:thorns"));
        db.setFaultHook(st -> {
            if (st.equals("enchant.before-commit")) {
                throw new SQLException("boom");
            }
        });
        try {
            db.enchantHat(alice, "halo", java.util.Map.of("minecraft:mending", 1), "Alice");
        } catch (SQLException expected) {
            // ok
        }
        db.setFaultHook(null);
        assertFalse(db.load(alice).enchants().get("halo").containsKey("minecraft:mending"), "сбой — чары не сохранены");
        assertTrue(db.revokeHat(alice, "halo", "admin"));
        assertTrue(db.load(alice).enchants().isEmpty(), "удаление шляпы удаляет её чары");
    }

    @Test
    void legacyItemImportAndRevokeAll() throws SQLException {
        assertTrue(db.importHatItem(alice, "Alice", "halo", java.util.Map.of("minecraft:thorns", 3), "legacy-item"));
        assertFalse(db.importHatItem(alice, "Alice", "halo", java.util.Map.of("minecraft:thorns", 1), "legacy-item"),
                "повторный импорт не дублирует");
        assertEquals(3, db.load(alice).enchants().get("halo").get("minecraft:thorns"), "чары не перезаписаны повторным импортом");
        db.grantHat(alice, "Alice", "cap", "test", "test");
        db.setSelectedHat(alice, "cap");
        assertEquals(2, db.revokeAll(alice, "admin"));
        PlayerData data = db.load(alice);
        assertTrue(data.owned().isEmpty() && data.enchants().isEmpty() && data.selectedHat() == null);
    }

    @Test
    void migrationCarriesEnchants() throws SQLException {
        var players = List.of(new LegacyImport(alice, "Alice", List.of("halo"), List.of(),
                java.util.Map.of("halo", java.util.Map.of("minecraft:thorns", 3))));
        db.importLegacy("src", players, "console", null, null);
        db.importLegacy("src", players, "console", null, null);
        assertEquals(java.util.Map.of("minecraft:thorns", 3), db.load(alice).enchants().get("halo"));
    }
}
