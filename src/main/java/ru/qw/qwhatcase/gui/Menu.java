package ru.qw.qwhatcase.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.Messages;
import ru.qw.qwhatcase.service.HatItems;
import ru.qw.qwhatcase.util.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Базовое меню. Все клики по меню отменяются в {@link MenuListener} до вызова обработчика,
 * поэтому забрать предмет из меню невозможно ни одним способом.
 */
public abstract class Menu implements InventoryHolder {
    @FunctionalInterface
    public interface ClickHandler {
        void click(ClickType type);
    }

    protected final QWHatCasePlugin plugin;
    protected final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, ClickHandler> handlers = new HashMap<>();

    protected Menu(QWHatCasePlugin plugin, Player viewer, int rows, String title) {
        this.plugin = plugin;
        this.viewer = viewer;
        Component component = Text.chat(title);
        this.inventory = Bukkit.createInventory(this, rows * 9, component);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    protected Messages msg() {
        return plugin.messages();
    }

    protected abstract void render();

    public void open() {
        redraw();
        viewer.openInventory(inventory);
    }

    public void redraw() {
        handlers.clear();
        inventory.clear();
        render();
    }

    protected void fill(Material material) {
        ItemStack filler = HatItems.filler(material);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    protected void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
        handlers.remove(slot);
    }

    protected void set(int slot, ItemStack item, ClickHandler handler) {
        inventory.setItem(slot, item);
        handlers.put(slot, handler);
    }

    protected ItemStack button(String materialKey, Material fallback, String nameKey, String loreKey, Map<String, ?> ph) {
        return HatItems.button(HatItems.material(msg().raw(materialKey), fallback),
                msg().raw(nameKey, ph), msg().lines(loreKey, ph));
    }

    protected ItemStack button(Material material, String nameKey, String loreKey, Map<String, ?> ph) {
        return HatItems.button(material, msg().raw(nameKey, ph), msg().lines(loreKey, ph));
    }

    public void handleClick(int slot, ClickType type) {
        ClickHandler handler = handlers.get(slot);
        if (handler != null) {
            handler.click(type);
        }
    }

    /** Меню анимации запрещает навигацию, пока лента крутится. */
    public boolean acceptsClicks() {
        return true;
    }

    public void onClose(InventoryCloseEvent event) {
    }

    protected static int pages(int total, int perPage) {
        return Math.max(1, (total + perPage - 1) / perPage);
    }

    protected static List<String> concat(List<String> a, List<String> b) {
        java.util.ArrayList<String> list = new java.util.ArrayList<>(a);
        list.addAll(b);
        return list;
    }
}
