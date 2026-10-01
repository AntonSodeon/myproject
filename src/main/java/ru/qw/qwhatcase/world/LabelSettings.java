package ru.qw.qwhatcase.world;

import java.util.List;

/**
 * Постоянная надпись над точкой кейса (TextDisplay).
 *
 * @param height     высота над верхней гранью блока (в блоках)
 * @param lines      строки; {case} — название кейса из cases.yml, {keys_hint} не используется
 * @param viewRange  дальность отображения в блоках
 * @param background ARGB-цвет фона в формате #AARRGGBB или "default" (стандартный полупрозрачный)
 * @param billboard  CENTER (поворот к смотрящему), VERTICAL, HORIZONTAL, FIXED
 * @param raise      насколько поднимать надпись во время анимации
 */
public record LabelSettings(boolean enabled, double height, List<String> lines, float scale, double viewRange,
                            String background, boolean shadow, boolean seeThrough, String billboard,
                            int lineWidth, double raise) {
}
