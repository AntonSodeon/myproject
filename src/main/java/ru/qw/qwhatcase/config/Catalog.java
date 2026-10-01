package ru.qw.qwhatcase.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Неизменяемый снимок всей загруженной конфигурации. */
public record Catalog(Settings settings,
                      Map<String, Rarity> rarities,
                      Map<String, Category> categories,
                      Map<String, Hat> hats,
                      Map<String, String> legacyIndex,
                      Map<String, CaseDef> cases,
                      List<String> problems) {

    public Catalog {
        rarities = Collections.unmodifiableMap(rarities);
        categories = Collections.unmodifiableMap(categories);
        hats = Collections.unmodifiableMap(hats);
        legacyIndex = Collections.unmodifiableMap(legacyIndex);
        cases = Collections.unmodifiableMap(cases);
        problems = List.copyOf(problems);
    }

    public Optional<Hat> hat(String id) {
        return Optional.ofNullable(id == null ? null : hats.get(id));
    }

    /** Поиск шляпы по старому ID (hat_90 и т. п.). */
    public Optional<Hat> byLegacy(String legacyId) {
        String id = legacyIndex.get(legacyId);
        return id == null ? Optional.empty() : hat(id);
    }

    public Optional<CaseDef> caseDef(String id) {
        return Optional.ofNullable(id == null ? null : cases.get(id));
    }

    public List<CaseDef> enabledCases() {
        return cases.values().stream().filter(CaseDef::enabled).toList();
    }

    public List<Hat> shopHats() {
        return hats.values().stream().filter(h -> h.available() && h.shopPrice() > 0).toList();
    }
}
