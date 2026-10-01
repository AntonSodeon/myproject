package ru.qw.qwhatcase.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.service.HatItems;
import ru.qw.qwhatcase.storage.PlayerData;
import ru.qw.qwhatcase.util.Placeholders;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Админ-меню выдачи шляп из PTrap (/hat grant): выбор игрока (поиск по нику через чат),
 * выдача шляпы, удаление отдельных шляп и всей коллекции с подтверждением.
 */
public final class AdminMenus {
    private static final int PER_PAGE = 45;

    private AdminMenus() {
    }

    private static String name(UUID uuid) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        return p.getName() == null ? uuid.toString() : p.getName();
    }

    private static void notify(QWHatCasePlugin plugin, UUID uuid, String key, Map<String, Object> ph) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            plugin.messages().send(online, key, ph);
        }
    }

    // ------------------------------------------------------------------ выбор игрока

    public static final class Players extends Menu {
        private final int page;
        private final String filter;

        public Players(QWHatCasePlugin plugin, Player viewer, int page, String filter) {
            super(plugin, viewer, 6, plugin.messages().raw("menu.admin.players-title"));
            this.page = page;
            this.filter = filter == null || filter.isBlank() ? null : filter;
        }

        private List<OfflinePlayer> list() {
            String query = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
            return Arrays.stream(Bukkit.getOfflinePlayers())
                    .filter(p -> p.getName() != null && p.getName().toLowerCase(Locale.ROOT).contains(query))
                    .sorted(Comparator.comparing(OfflinePlayer::isOnline).reversed()
                            .thenComparing(OfflinePlayer::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }

        @Override
        protected void render() {
            fill(Material.BLACK_STAINED_GLASS_PANE);
            List<OfflinePlayer> list = list();
            int pages = pages(list.size(), PER_PAGE);
            int current = Math.max(0, Math.min(page, pages - 1));
            for (int i = 0; i < PER_PAGE; i++) {
                int index = current * PER_PAGE + i;
                if (index >= list.size()) {
                    set(i, null);
                    continue;
                }
                OfflinePlayer target = list.get(index);
                Map<String, Object> ph = Placeholders.of("player", target.getName());
                ItemStack head = button(Material.PLAYER_HEAD, target.isOnline() ? "menu.admin.player-online-name" : "menu.admin.player-offline-name",
                        target.isOnline() ? "menu.admin.player-online-lore" : "menu.admin.player-offline-lore", ph);
                head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(target));
                set(i, head, click -> new Target(plugin, viewer, target.getUniqueId()).open());
            }
            Map<String, Object> ph = Placeholders.of("count", list.size(), "page", current + 1, "pages", pages,
                    "filter", filter == null ? msg().raw("menu.admin.filter-none") : filter);
            if (current > 0) {
                set(45, button(Material.ARROW, "menu.common.prev-name", "menu.common.page-lore", ph),
                        click -> new Players(plugin, viewer, current - 1, filter).open());
            }
            set(47, button(Material.NAME_TAG, "menu.admin.search-name", "menu.admin.search-lore", ph), click -> {
                plugin.chatInput().await(viewer, text -> new Players(plugin, viewer, 0,
                        text.equalsIgnoreCase("cancel") ? null : text).open());
                viewer.closeInventory();
                msg().send(viewer, "admin.search-prompt");
            });
            set(49, button(Material.COMPASS, "menu.admin.players-info-name", "menu.admin.players-info-lore", ph));
            if (current + 1 < pages) {
                set(53, button(Material.LIME_CONCRETE, "menu.common.next-name", "menu.common.page-lore", ph),
                        click -> new Players(plugin, viewer, current + 1, filter).open());
            }
        }
    }

    // ------------------------------------------------------------------ действия с игроком

    public static final class Target extends Menu {
        private final UUID target;

        public Target(QWHatCasePlugin plugin, Player viewer, UUID target) {
            super(plugin, viewer, 3, plugin.messages().raw("menu.admin.target-title", Placeholders.of("player", name(target))));
            this.target = target;
        }

        @Override
        protected void render() {
            fill(Material.BLACK_STAINED_GLASS_PANE);
            Map<String, Object> ph = Placeholders.of("player", name(target));
            set(11, button(Material.LIME_CONCRETE, "menu.admin.give-name", "menu.admin.give-lore", ph),
                    click -> new GrantHats(plugin, viewer, target, 0).open());
            set(15, button(Material.CHEST, "menu.admin.manage-name", "menu.admin.manage-lore", ph),
                    click -> RemoveHats.open(plugin, viewer, target, 0));
            set(22, button(Material.ARROW, "menu.common.back-name", "menu.admin.back-players-lore", ph),
                    click -> new Players(plugin, viewer, 0, null).open());
        }
    }

    // ------------------------------------------------------------------ выдача

    public static final class GrantHats extends Menu {
        private final UUID target;
        private final int page;

        public GrantHats(QWHatCasePlugin plugin, Player viewer, UUID target, int page) {
            super(plugin, viewer, 6, plugin.messages().raw("menu.admin.grant-title", Placeholders.of("player", name(target))));
            this.target = target;
            this.page = page;
        }

        @Override
        protected void render() {
            fill(Material.BLACK_STAINED_GLASS_PANE);
            List<Hat> hats = DonateShopMenu.hats(plugin);
            int pages = pages(hats.size(), PER_PAGE);
            int current = Math.max(0, Math.min(page, pages - 1));
            for (int i = 0; i < PER_PAGE; i++) {
                int index = current * PER_PAGE + i;
                if (index >= hats.size()) {
                    set(i, null);
                    continue;
                }
                Hat hat = hats.get(index);
                set(i, HatItems.icon(hat, hat.coloredName(), concat(hat.lore(), msg().lines("menu.admin.grant-hat-lore",
                        Placeholders.of("id", hat.id()))), false), click -> {
                    String targetName = name(target);
                    plugin.api().grantHat(target, hat.id(), viewer.getName()).whenComplete((added, error) ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                Map<String, Object> ph = Placeholders.of("player", targetName, "hat", hat.coloredName());
                                if (error != null) {
                                    msg().send(viewer, "error.database");
                                    return;
                                }
                                msg().send(viewer, added ? "admin.hat-given" : "admin.hat-already", ph);
                                if (added) {
                                    AdminMenus.notify(plugin, target, "hat.received", ph);
                                }
                                if (viewer.isOnline()) {
                                    new Target(plugin, viewer, target).open();
                                }
                            }));
                });
            }
            Map<String, Object> ph = Placeholders.of("page", current + 1, "pages", pages);
            if (current > 0) {
                set(45, button(Material.ARROW, "menu.common.prev-name", "menu.common.page-lore", ph),
                        click -> new GrantHats(plugin, viewer, target, current - 1).open());
            }
            set(49, button(Material.PAPER, "menu.admin.grant-info-name", "menu.admin.grant-info-lore", ph),
                    click -> new Target(plugin, viewer, target).open());
            if (current + 1 < pages) {
                set(53, button(Material.LIME_CONCRETE, "menu.common.next-name", "menu.common.page-lore", ph),
                        click -> new GrantHats(plugin, viewer, target, current + 1).open());
            }
        }
    }

    // ------------------------------------------------------------------ удаление

    public static final class RemoveHats extends Menu {
        private final UUID target;
        private final int page;
        private final PlayerData data;

        private RemoveHats(QWHatCasePlugin plugin, Player viewer, UUID target, int page, PlayerData data) {
            super(plugin, viewer, 6, plugin.messages().raw("menu.admin.remove-title", Placeholders.of("player", name(target))));
            this.target = target;
            this.page = page;
            this.data = data;
        }

        /** Коллекция читается из БД (игрок может быть офлайн). */
        public static void open(QWHatCasePlugin plugin, Player viewer, UUID target, int page) {
            plugin.storage().run(db -> db.load(target), data -> {
                if (viewer.isOnline()) {
                    new RemoveHats(plugin, viewer, target, page, data).open();
                }
            }, error -> plugin.messages().send(viewer, "error.database"));
        }

        @Override
        protected void render() {
            fill(Material.BLACK_STAINED_GLASS_PANE);
            List<String> ids = List.copyOf(data.owned().keySet());
            int pages = pages(ids.size(), PER_PAGE);
            int current = Math.max(0, Math.min(page, pages - 1));
            for (int i = 0; i < PER_PAGE; i++) {
                int index = current * PER_PAGE + i;
                if (index >= ids.size()) {
                    set(i, null);
                    continue;
                }
                String id = ids.get(index);
                Hat hat = plugin.catalog().hat(id).orElse(null);
                ItemStack icon = hat == null
                        ? button(Material.BARRIER, "menu.admin.unknown-hat-name", "menu.admin.remove-hat-lore", Placeholders.of("id", id))
                        : HatItems.icon(hat, hat.coloredName(), concat(hat.lore(), msg().lines("menu.admin.remove-hat-lore",
                        Placeholders.of("id", id))), false, plugin.display().baseStats().with(data.enchants().get(id)));
                set(i, icon, click -> plugin.api().revokeHat(target, id, viewer.getName()).whenComplete((removed, error) ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Map<String, Object> ph = Placeholders.of("player", name(target), "hat", hat == null ? id : hat.coloredName());
                            if (error != null) {
                                msg().send(viewer, "error.database");
                                return;
                            }
                            msg().send(viewer, "admin.hat-revoked", ph);
                            AdminMenus.notify(plugin, target, "hat.revoked-by-admin", ph);
                            if (viewer.isOnline()) {
                                RemoveHats.open(plugin, viewer, target, current);
                            }
                        })));
            }
            Map<String, Object> ph = Placeholders.of("page", current + 1, "pages", pages, "count", ids.size(), "player", name(target));
            if (current > 0) {
                set(45, button(Material.ARROW, "menu.common.prev-name", "menu.common.page-lore", ph),
                        click -> RemoveHats.open(plugin, viewer, target, current - 1));
            }
            set(48, button(Material.ARROW, "menu.common.back-name", "menu.admin.back-target-lore", ph),
                    click -> new Target(plugin, viewer, target).open());
            set(49, button(Material.BARRIER, "menu.admin.remove-all-name", "menu.admin.remove-all-lore", ph),
                    click -> new ConfirmDeleteAll(plugin, viewer, target, ids.size()).open());
            if (current + 1 < pages) {
                set(53, button(Material.LIME_CONCRETE, "menu.common.next-name", "menu.common.page-lore", ph),
                        click -> RemoveHats.open(plugin, viewer, target, current + 1));
            }
        }
    }

    public static final class ConfirmDeleteAll extends Menu {
        private final UUID target;
        private final int count;

        public ConfirmDeleteAll(QWHatCasePlugin plugin, Player viewer, UUID target, int count) {
            super(plugin, viewer, 3, plugin.messages().raw("menu.admin.confirm-title"));
            this.target = target;
            this.count = count;
        }

        @Override
        protected void render() {
            fill(Material.BLACK_STAINED_GLASS_PANE);
            Map<String, Object> ph = Placeholders.of("player", name(target), "count", count);
            set(13, button(Material.PAPER, "menu.admin.confirm-info-name", "menu.admin.confirm-info-lore", ph));
            set(11, button(Material.RED_CONCRETE, "menu.admin.confirm-yes-name", "menu.admin.confirm-yes-lore", ph), click -> {
                viewer.closeInventory();
                plugin.api().revokeAll(target, viewer.getName()).whenComplete((removed, error) ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (error != null) {
                                msg().send(viewer, "error.database");
                                return;
                            }
                            msg().send(viewer, "admin.all-revoked", Placeholders.of("player", name(target), "count", removed));
                            AdminMenus.notify(plugin, target, "hat.all-revoked-by-admin", Map.of());
                        }));
            });
            set(15, button(Material.ARROW, "menu.admin.confirm-no-name", "menu.admin.confirm-no-lore", ph),
                    click -> new Target(plugin, viewer, target).open());
        }
    }
}
