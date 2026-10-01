package ru.qw.qwhatcase.service;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.util.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Создание предметов шляп. Все служебные предметы помечаются ключами PDC —
 * плагин никогда не определяет свои предметы по названию, описанию или модели.
 */
public final class HatItems {
    /** Косметическая шляпа в слоте шлема: значение = ID шляпы. */
    public static final NamespacedKey COSMETIC = new NamespacedKey("qwhatcase", "cosmetic");
    /** Иконка меню. */
    public static final NamespacedKey GUI = new NamespacedKey("qwhatcase", "gui");
    /** Метки предметов старого плагина PTrap (и его предшественников). */
    public static final List<NamespacedKey> LEGACY_KEYS = List.of(
            new NamespacedKey("ptrap", "hat-id"),
            new NamespacedKey("qwhats", "hat-id"),
            new NamespacedKey("f3tahats", "hat"));

    private HatItems() {
    }

    public static Material material(String name, Material fallback) {
        Material material = name == null ? null : Material.matchMaterial(name);
        return material == null || !material.isItem() || material.isAir() ? fallback : material;
    }

    private static void applyModel(ItemMeta meta, String itemModel, int customModelData) {
        if (itemModel != null && !itemModel.isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(itemModel.trim());
            if (key != null) {
                meta.setItemModel(key);
            }
        }
        if (customModelData > 0) {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of((float) customModelData));
            meta.setCustomModelDataComponent(cmd);
        }
    }

    /** Чары и атрибуты шляпы (как в PTrap): базовые из config.yml + личные чары игрока поверх них. */
    public record Stats(Map<String, Integer> enchantments, Map<String, Double> attributes, boolean glint) {
        public static final Stats NONE = new Stats(Map.of(), Map.of(), false);

        /** Личные чары перекрывают базовые (как addUnsafeEnchantment в PTrap). */
        public Stats with(Map<String, Integer> personal) {
            if (personal == null || personal.isEmpty()) {
                return this;
            }
            Map<String, Integer> merged = new LinkedHashMap<>(enchantments);
            merged.putAll(personal);
            return new Stats(merged, attributes, glint);
        }
    }

    public static Enchantment enchantment(String key) {
        NamespacedKey nk = NamespacedKey.fromString(key);
        return nk == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(nk);
    }

    public static Attribute attribute(String key) {
        NamespacedKey nk = NamespacedKey.fromString(key);
        return nk == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE).get(nk);
    }

    private static void applyStats(ItemMeta meta, Hat hat, Stats stats) {
        for (Map.Entry<String, Integer> e : stats.enchantments().entrySet()) {
            Enchantment enchantment = enchantment(e.getKey());
            if (enchantment != null) {
                meta.addEnchant(enchantment, e.getValue(), true);
            }
        }
        for (Map.Entry<String, Double> e : stats.attributes().entrySet()) {
            Attribute attribute = attribute(e.getKey());
            if (attribute != null) {
                String path = ("hat_" + e.getKey().replace(':', '_')).toLowerCase(java.util.Locale.ROOT);
                meta.addAttributeModifier(attribute, new AttributeModifier(new NamespacedKey("qwhatcase", path),
                        e.getValue(), AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
            }
        }
        meta.setEnchantmentGlintOverride(stats.glint());
    }

    /** Предмет, который надевается в слот шлема. Снять его вручную нельзя — только через меню/команду. */
    public static ItemStack cosmetic(Hat hat, Stats stats) {
        ItemStack item = new ItemStack(material(hat.material(), Material.CARVED_PUMPKIN));
        item.editMeta(meta -> {
            meta.displayName(Text.item(hat.coloredName()));
            meta.lore(Text.itemLines(hat.lore()));
            applyModel(meta, hat.itemModel(), hat.customModelData());
            EquippableComponent equippable = meta.getEquippable();
            equippable.setSlot(EquipmentSlot.HEAD);
            equippable.setSwappable(false);
            equippable.setDispensable(false);
            equippable.setDamageOnHurt(false);
            equippable.setCameraOverlay(null);
            meta.setEquippable(equippable);
            meta.setMaxStackSize(1);
            applyStats(meta, hat, stats);
            meta.getPersistentDataContainer().set(COSMETIC, PersistentDataType.STRING, hat.id());
        });
        return item;
    }

    /** Иконка шляпы для меню: та же модель, чары и характеристики, но надеть её нельзя. */
    public static ItemStack icon(Hat hat, String name, List<String> lore, boolean glint, Stats stats) {
        ItemStack item = new ItemStack(material(hat.material(), Material.CARVED_PUMPKIN));
        item.editMeta(meta -> {
            meta.displayName(Text.item(name));
            meta.lore(Text.itemLines(lore));
            applyModel(meta, hat.itemModel(), hat.customModelData());
            applyStats(meta, hat, stats);
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            if (stats.enchantments().isEmpty() && stats.attributes().isEmpty()) {
                meta.addItemFlags(ItemFlag.values());
            }
            meta.getPersistentDataContainer().set(GUI, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public static ItemStack icon(Hat hat, String name, List<String> lore, boolean glint) {
        return icon(hat, name, lore, glint, Stats.NONE);
    }

    /** Чары предмета в виде "minecraft:key" → уровень. */
    public static Map<String, Integer> enchantKeys(Map<Enchantment, Integer> enchants) {
        Map<String, Integer> result = new LinkedHashMap<>();
        enchants.forEach((e, level) -> result.put(e.getKey().asString(), level));
        return result;
    }

    /** Модель шляпы для ItemDisplay в мире: только модель, без чар и подписей (это не предмет игрока). */
    public static ItemStack displayItem(Hat hat) {
        ItemStack item = new ItemStack(material(hat.material(), Material.CARVED_PUMPKIN));
        item.editMeta(meta -> {
            applyModel(meta, hat.itemModel(), hat.customModelData());
            meta.getPersistentDataContainer().set(GUI, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    /** Простая кнопка меню. */
    public static ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Text.item(name));
            meta.lore(Text.itemLines(lore));
            meta.addItemFlags(ItemFlag.values());
            meta.getPersistentDataContainer().set(GUI, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public static ItemStack button(String material, Material fallback, String itemModel, int cmd, String name, List<String> lore, boolean glint) {
        ItemStack item = button(material(material, fallback), name, lore);
        item.editMeta(meta -> {
            applyModel(meta, itemModel, cmd);
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        return item;
    }

    public static ItemStack filler(Material material) {
        return button(material, " ", new ArrayList<>());
    }

    public static String cosmeticId(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(COSMETIC, PersistentDataType.STRING);
    }

    public static boolean isCosmetic(ItemStack item) {
        return cosmeticId(item) != null;
    }

    public static boolean isGui(ItemStack item) {
        return item != null && !item.isEmpty() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(GUI, PersistentDataType.BYTE);
    }

    /** Любой служебный предмет плагина, который не должен оказаться в инвентаре игрока. */
    public static boolean isService(ItemStack item) {
        return isCosmetic(item) || isGui(item);
    }

    /** ID шляпы старого плагина (hat_90 и т. п.) или null. */
    public static String legacyId(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        for (NamespacedKey key : LEGACY_KEYS) {
            if (pdc.has(key, PersistentDataType.STRING)) {
                return pdc.get(key, PersistentDataType.STRING);
            }
            if (pdc.has(key)) {
                // f3tahats:hat мог храниться не строкой — тогда ID восстанавливается по custom_model_data, как в старом плагине.
                ItemMeta meta = item.getItemMeta();
                List<Float> floats = meta.getCustomModelDataComponent().getFloats();
                return floats.isEmpty() ? null : "hat_" + Math.round(floats.get(0));
            }
        }
        return null;
    }
}
