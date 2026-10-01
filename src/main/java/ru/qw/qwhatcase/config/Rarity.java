package ru.qw.qwhatcase.config;

/**
 * Редкость шляпы.
 *
 * @param order        порядок (чем больше, тем реже)
 * @param compensation жетоны за дубликат по умолчанию
 * @param pane         материал рамки/стекла для оформления
 * @param winSound     звук выигрыша ("" = без звука)
 */
public record Rarity(String id, String name, String color, int order, long compensation,
                     String pane, String winSound) {
}
