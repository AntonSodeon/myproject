package ru.qw.qwhatcase.config;

import java.util.List;

/**
 * Шляпа из каталога. Идентификатор постоянный и не зависит от названия.
 *
 * @param compensation жетоны за повторное выпадение (уже с учётом значения редкости по умолчанию)
 * @param shopPrice    цена в каталоге за жетоны; 0 — не продаётся
 */
public record Hat(String id, String name, List<String> lore, Rarity rarity, Category category,
                  String material, String itemModel, int customModelData, String model,
                  boolean available, long compensation, long shopPrice, List<String> legacyIds) {

    public String coloredName() {
        return rarity.color() + name;
    }
}
