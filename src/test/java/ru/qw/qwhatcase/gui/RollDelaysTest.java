package ru.qw.qwhatcase.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollDelaysTest {
    @Test
    void delaysSumToDurationAndSlowDown() {
        int[] delays = RollMenu.delays(45, 5000);
        assertEquals(45, delays.length);
        int sum = 0;
        for (int d : delays) {
            assertTrue(d >= 1);
            sum += d;
        }
        assertEquals(100, sum, 2, "5 секунд = 100 тиков");
        assertTrue(delays[44] > delays[0] * 3, "лента замедляется");
    }
}
