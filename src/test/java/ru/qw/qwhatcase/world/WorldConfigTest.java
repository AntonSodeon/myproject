package ru.qw.qwhatcase.world;

import org.junit.jupiter.api.Test;
import ru.qw.qwhatcase.TestCatalogs;
import ru.qw.qwhatcase.config.CaseDef;
import ru.qw.qwhatcase.config.Catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldConfigTest {

    @Test
    void bundledDefaultsAndPerCaseOverride() throws Exception {
        Catalog catalog = TestCatalogs.bundled();
        CaseDef basic = catalog.caseDef("basic").orElseThrow();
        CaseDef premium = catalog.caseDef("premium").orElseThrow();
        WorldAnimSettings b = basic.worldAnimation();
        assertTrue(b.enabled());
        assertEquals(6000, b.spinMs());
        assertEquals(3000, b.resultMs());
        assertEquals(5, b.visibleModels());
        assertEquals("minecraft:custom.mystery_crate.scroll", b.tickSound().sound());
        assertEquals("minecraft:block.note_block.bell", b.slowdownSound().sound());
        WorldAnimSettings p = premium.worldAnimation();
        assertEquals(7000, p.spinMs(), "переопределено в кейсе");
        assertEquals(7, p.visibleModels());
        assertEquals(60, p.resultParticles().count(), "частично переопределённая секция");
        assertEquals("DUST", p.resultParticles().type());
        assertEquals(3000, p.resultMs(), "остальное — из общего раздела");
        assertEquals(List.of("{case}", "&7ПКМ — открыть кейс"), basic.label().lines());
        assertEquals(3, premium.label().lines().size());
        assertEquals("CENTER", basic.label().billboard());
    }

    @Test
    void invalidValuesFallBackWithProblem() {
        List<String> problems = new ArrayList<>();
        var global = TestCatalogs.yaml("""
                visible-models: 4
                spacing: -3
                sounds: {tick: {sound: 'minecraft:block.note_block.hat', volume: 0.3}}
                """);
        WorldAnimSettings s = WorldConfig.animation(global, null, problems, "world-animation");
        assertEquals(5, s.visibleModels(), "чётное число исправлено на нечётное");
        assertEquals(0.7, s.spacing(), 1e-9);
        assertEquals(0.3f, s.tickSound().volume());
        assertEquals("minecraft:block.note_block.hat", s.tickSound().sound());
        assertEquals(2, problems.size(), problems.toString());
        List<String> lp = new ArrayList<>();
        LabelSettings l = WorldConfig.label(TestCatalogs.yaml("billboard: SIDEWAYS\nbackground: red"), null, lp, "case-labels");
        assertEquals("CENTER", l.billboard());
        assertEquals("default", l.background());
        assertEquals(2, lp.size());
    }

    @Test
    void easingIsMonotonicAndStopsExactlyOnTarget() {
        WorldAnimSettings s = WorldConfig.animation(TestCatalogs.yaml("scroll-items: 30\nspin-duration-ms: 6000"), null,
                new ArrayList<>(), "w");
        assertEquals(120, s.spinTicks());
        double previous = -1;
        double previousSpeed = Double.MAX_VALUE;
        for (int t = 0; t <= s.spinTicks(); t++) {
            double o = s.offsetAt(t);
            assertTrue(o >= previous, "лента не движется назад");
            if (t > 0) {
                double speed = o - previous;
                assertTrue(speed <= previousSpeed + 1e-9, "скорость только падает (замедление)");
                previousSpeed = speed;
            }
            previous = o;
        }
        assertEquals(30.0, s.offsetAt(s.spinTicks()), 1e-12, "остановка ровно на выигранной модели");
        assertEquals(30.0, s.offsetAt(10_000), 1e-12);
        // Интервалы между прохождениями центра растут к концу (звук «тиков» замедляется).
        List<Integer> passTicks = new ArrayList<>();
        int last = 0;
        for (int t = 1; t <= s.spinTicks(); t++) {
            int c = (int) Math.floor(s.offsetAt(t) + 0.5);
            if (c > last) {
                last = c;
                passTicks.add(t);
            }
        }
        assertEquals(30, passTicks.size(), "ровно 30 прохождений через центр");
        int firstGap = passTicks.get(1) - passTicks.get(0);
        int lastGap = passTicks.get(passTicks.size() - 1) - passTicks.get(passTicks.size() - 2);
        assertTrue(lastGap > firstGap * 3, firstGap + " → " + lastGap);
    }

    @Test
    void pointLocksAreExclusivePerPoint() {
        PointLocks locks = new PointLocks();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(locks.tryLock("world;1;64;1", a, "t1"));
        assertFalse(locks.tryLock("world;1;64;1", b, "t2"), "вторая операция на той же точке");
        assertTrue(locks.tryLock("world;5;64;5", b, "t2"), "другая точка независима");
        locks.unlock("world;1;64;1", "wrong");
        assertTrue(locks.isLocked("world;1;64;1"), "чужой токен не освобождает");
        locks.setOperation("world;1;64;1", a, "op-1");
        locks.unlock("world;1;64;1", "op-1");
        assertFalse(locks.isLocked("world;1;64;1"));
    }
}
