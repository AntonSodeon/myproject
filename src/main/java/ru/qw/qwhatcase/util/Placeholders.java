package ru.qw.qwhatcase.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Placeholders {
    private Placeholders() {
    }

    /** of("player", name, "amount", 5) */
    public static Map<String, Object> of(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }
}
