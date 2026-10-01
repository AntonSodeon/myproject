package ru.qw.qwhatcase.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.api.CaseOpenedEvent;
import ru.qw.qwhatcase.config.CaseDef;
import ru.qw.qwhatcase.config.CaseReward;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.gui.ResultMenu;
import ru.qw.qwhatcase.gui.RollMenu;
import ru.qw.qwhatcase.storage.CasePoint;
import ru.qw.qwhatcase.storage.HistoryEntry;
import ru.qw.qwhatcase.world.OpenContext;
import ru.qw.qwhatcase.storage.OpeningRequest;
import ru.qw.qwhatcase.storage.OpeningResult;
import ru.qw.qwhatcase.util.Format;
import ru.qw.qwhatcase.util.Placeholders;
import ru.qw.qwhatcase.util.Text;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Открытие кейса:
 * проверки → уникальный ID операции → розыгрыш → одна транзакция (ключи + результат + награда)
 * → только после успешного сохранения анимация, которая показывает уже сохранённый результат.
 */
public final class OpeningService {
    private final QWHatCasePlugin plugin;
    private final RewardRoller roller = new RewardRoller(new SecureRandom());
    /** Одно активное открытие на игрока: от нажатия «Открыть» до показа результата. */
    private final Map<UUID, String> busy = new ConcurrentHashMap<>();
    private final Map<UUID, RollMenu> rolling = new HashMap<>();

    public OpeningService(QWHatCasePlugin plugin) {
        this.plugin = plugin;
    }

    public RewardRoller roller() {
        return roller;
    }

    public boolean isBusy(UUID uuid) {
        return busy.containsKey(uuid);
    }

    /** Освободить игрока, только если занятость принадлежит этой операции (повторный вход не затрагивается). */
    public void releaseBusy(UUID uuid, String token) {
        busy.remove(uuid, token);
    }

    /** Проверка без побочных эффектов: можно ли открыть кейс (для меню и команд). */
    public String denyReason(Player player, CaseDef def) {
        if (!player.hasPermission("qwhatcase.open")) {
            return "error.no-permission";
        }
        if (def == null || !def.enabled()) {
            return "cases.disabled";
        }
        if (!def.permission().isEmpty() && !player.hasPermission(def.permission())) {
            return "cases.no-case-permission";
        }
        if (!plugin.packs().canOpenCases(player)) {
            return "pack.required-for-cases";
        }
        Profile profile = plugin.profiles().get(player);
        if (profile == null) {
            return "error.profile-loading";
        }
        if (profile.keys(def.keyType()) < def.keyCost()) {
            return "cases.not-enough-keys";
        }
        return null;
    }

    public void open(Player player, String caseId, boolean skipAnimation) {
        open(player, caseId, skipAnimation, OpenContext.COMMAND);
    }

    /**
     * Единая точка открытия для всех способов. Розыгрыш, списание ключей и выдача награды одинаковы;
     * контекст определяет только визуализацию: BLOCK — анимация в мире над точкой из контекста,
     * остальное — прежняя анимация в GUI.
     */
    public void open(Player player, String caseId, boolean skipAnimation, OpenContext context) {
        CaseDef def = plugin.catalog().caseDef(caseId).orElse(null);
        if (def == null) {
            plugin.messages().send(player, "cases.unknown", Placeholders.of("case", caseId));
            return;
        }
        UUID uuid = player.getUniqueId();
        String token = UUID.randomUUID().toString();
        if (busy.putIfAbsent(uuid, token) != null) {
            plugin.messages().send(player, "cases.already-opening");
            return;
        }
        String deny = denyReason(player, def);
        if (deny != null) {
            busy.remove(uuid, token);
            Profile profile = plugin.profiles().get(player);
            plugin.messages().send(player, deny, Placeholders.of("case", def.name(), "cost", def.keyCost(),
                    "keys", profile == null ? 0 : profile.keys(def.keyType())));
            if (deny.equals("pack.required-for-cases")) {
                sendPackRetry(player);
            }
            return;
        }
        CasePoint point = null;
        if (context != null && context.isBlock()) {
            String worldDeny = worldDenyReason(player, def, context);
            if (worldDeny != null) {
                busy.remove(uuid, token);
                plugin.messages().send(player, worldDeny, Placeholders.of("case", def.name()));
                return;
            }
            if (def.worldAnimation().enabled() && plugin.packs().hasModels(player)) {
                point = context.point();
                // Захват точки ДО списания ключа: второй игрок не займёт её и не потеряет ключ.
                if (!plugin.worldAnimations().locks().tryLock(point.key(), uuid, token)) {
                    busy.remove(uuid, token);
                    plugin.messages().send(player, "world.point-busy");
                    return;
                }
            } else if (def.worldAnimation().enabled()) {
                // Без ресурс-пака модели в мире не видны — показываем прежнюю анимацию в меню.
                plugin.messages().send(player, "world.no-pack-fallback");
            }
        }
        // Снимок настроек: def и награда — неизменяемые объекты, reload их не затронет.
        CaseReward reward = roller.roll(def);
        Hat hat = reward.hat();
        OpeningRequest request = new OpeningRequest(UUID.randomUUID(), uuid, player.getName(), def.id(),
                def.keyType(), def.keyCost(), hat.id(), def.compensationFor(hat), System.currentTimeMillis(),
                context == null ? null : context.pointKey());
        boolean animate = def.animation().enabled() && !skipAnimation;
        CasePoint worldPoint = point;
        plugin.storage().run(db -> db.performOpening(request),
                result -> onSaved(uuid, def, reward, result, animate, worldPoint, token),
                error -> {
                    busy.remove(uuid, token);
                    if (worldPoint != null) {
                        plugin.worldAnimations().locks().unlock(worldPoint.key(), token);
                    }
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null) {
                        plugin.messages().send(online, "error.database");
                    }
                });
    }

    /** Проверки для открытия через блок: точка существует, привязана к этому же кейсу, мир и чанк загружены. */
    public String worldDenyReason(Player player, CaseDef def, OpenContext context) {
        CasePoint current = plugin.points().byKey(context.point().key());
        if (current == null || !current.caseId().equals(def.id())) {
            return "world.point-changed";
        }
        org.bukkit.World world = Bukkit.getWorld(current.world());
        if (world == null || !world.isChunkLoaded(current.x() >> 4, current.z() >> 4)) {
            return "world.point-unloaded";
        }
        if (def.worldAnimation().enabled() && plugin.worldAnimations().locks().isLocked(current.key())) {
            return "world.point-busy";
        }
        return null;
    }

    private void onSaved(UUID uuid, CaseDef def, CaseReward reward, OpeningResult result, boolean animate,
                         CasePoint worldPoint, String token) {
        Player player = Bukkit.getPlayer(uuid);
        Profile profile = plugin.profiles().get(uuid).orElse(null);
        if (worldPoint != null && result.status() != OpeningResult.Status.SUCCESS) {
            plugin.worldAnimations().locks().unlock(worldPoint.key(), token);
        }
        switch (result.status()) {
            case SUCCESS -> {
                if (profile != null) {
                    profile.setKeys(def.keyType(), result.keysBalance());
                    profile.setTokens(result.tokensBalance());
                    profile.addOwned(result.hatId(), result.createdAt());
                }
                plugin.getLogger().info("Открытие " + result.operationId() + ": " + uuid + " кейс=" + def.id()
                        + " шляпа=" + result.hatId() + " итог=" + result.outcome() + " жетоны=" + result.tokensAwarded());
                if (worldPoint != null) {
                    startWorld(player, uuid, def, reward, result, worldPoint, token);
                    return;
                }
                if (player == null) {
                    // Игрок вышел до сохранения: результат сохранён, уведомление покажется при входе.
                    busy.remove(uuid, token);
                    return;
                }
                Bukkit.getPluginManager().callEvent(new CaseOpenedEvent(player, result.operationId(), def.id(), result.hatId(),
                        result.outcome() == OpeningResult.Outcome.DUPLICATE, result.tokensAwarded()));
                if (animate) {
                    RollMenu menu = new RollMenu(plugin, player, def, reward, result);
                    rolling.put(uuid, menu);
                    menu.open();
                    menu.start();
                } else {
                    reveal(player, def, reward.hat(), result, true);
                }
            }
            case NOT_ENOUGH_KEYS -> {
                busy.remove(uuid, token);
                if (profile != null && result.keysBalance() >= 0) {
                    profile.setKeys(def.keyType(), result.keysBalance());
                }
                if (player != null) {
                    plugin.messages().send(player, "cases.not-enough-keys", Placeholders.of("case", def.name(),
                            "cost", def.keyCost(), "keys", Math.max(0, result.keysBalance())));
                }
            }
            case ALREADY_PROCESSED -> {
                // Невозможно при новом UUID, но обработано: повторно ничего не выдаётся.
                busy.remove(uuid, token);
            }
            case ERROR -> {
                busy.remove(uuid, token);
                if (player != null) {
                    plugin.messages().send(player, "error.database");
                }
            }
        }
    }

    /**
     * Результат сохранён → закрываем меню и запускаем анимацию над точкой. Если анимацию запустить нельзя,
     * награда всё равно остаётся (она уже в БД) — итог показывается в чате.
     */
    private void startWorld(Player player, UUID uuid, CaseDef def, CaseReward reward, OpeningResult result,
                            CasePoint point, String token) {
        if (player != null) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof ru.qw.qwhatcase.gui.Menu) {
                player.closeInventory();
            }
            Bukkit.getPluginManager().callEvent(new CaseOpenedEvent(player, result.operationId(), def.id(), result.hatId(),
                    result.outcome() == OpeningResult.Outcome.DUPLICATE, result.tokensAwarded()));
        }
        boolean started = plugin.worldAnimations().start(token, point, def, reward.hat(), result, uuid,
                player == null ? uuid.toString() : player.getName(), player == null ? null : player.getLocation(),
                false, token);
        if (!started) {
            busy.remove(uuid, token);
            if (player != null) {
                reveal(player, def, reward.hat(), result, false);
            }
        } else if (player == null) {
            plugin.worldAnimations().ownerQuit(uuid);
            busy.remove(uuid, token);
        }
    }

    /** Объявление о выигрыше всем (настройка кейса broadcast) — общее для GUI и анимации в мире. */
    public void broadcast(CaseDef def, Hat hat, Map<String, Object> ph) {
        if (def.broadcastEnabled() && hat.rarity().order() >= def.broadcastMinRarity() && !def.broadcastMessage().isEmpty()) {
            Bukkit.broadcast(Text.chat(Text.apply(def.broadcastMessage(), ph)));
        }
    }

    /** Показ итога: сообщение, звук, объявление; освобождает игрока для следующего открытия. */
    public void reveal(Player player, CaseDef def, Hat hat, OpeningResult result, boolean openResultMenu) {
        rolling.remove(player.getUniqueId());
        busy.remove(player.getUniqueId());
        plugin.storage().submit(db -> {
            db.markShown(result.operationId());
            return null;
        });
        boolean duplicate = result.outcome() == OpeningResult.Outcome.DUPLICATE;
        Map<String, Object> ph = placeholders(player, def, hat, result);
        if (!def.winMessage().isEmpty()) {
            player.sendMessage(Text.chat(plugin.messages().raw("prefix") + Text.apply(def.winMessage(), ph)));
        } else {
            plugin.messages().send(player, "open.win", ph);
        }
        if (duplicate) {
            plugin.messages().send(player, "open.duplicate", ph);
        } else {
            plugin.messages().send(player, "open.new", ph);
        }
        if (def.animation().sounds()) {
            String sound = duplicate ? def.animation().duplicateSound() : hat.rarity().winSound().isEmpty()
                    ? def.animation().finishSound() : hat.rarity().winSound();
            playSound(player, sound, 1f);
        }
        broadcast(def, hat, ph);
        if (openResultMenu && player.isOnline()) {
            new ResultMenu(plugin, player, def, hat, result).open();
        }
    }

    public Map<String, Object> placeholders(Player player, CaseDef def, Hat hat, OpeningResult result) {
        double chance = def.rewards().stream().filter(r -> r.hat().id().equals(hat.id()))
                .mapToDouble(def::chancePercent).findFirst().orElse(0);
        return Placeholders.of("player", player.getName(), "case", def.name(), "hat", hat.coloredName(),
                "rarity", hat.rarity().color() + hat.rarity().name(), "chance", Format.percent(chance),
                "tokens", result.tokensAwarded(), "balance", result.tokensBalance(), "keys", result.keysBalance(),
                "operation", result.operationId().toString().substring(0, 8));
    }

    public void playSound(Player player, String sound, float pitch) {
        if (sound == null || sound.isBlank()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), sound, SoundCategory.MASTER, 0.8f, pitch);
        } catch (IllegalArgumentException ignored) {
            // Неверное имя звука не должно ломать открытие.
        }
    }

    /** Закрытие окна во время анимации: выигрыш уже сохранён, показываем его в чате. */
    public void rollInterrupted(Player player, RollMenu menu, boolean disconnected) {
        rolling.remove(player.getUniqueId());
        if (disconnected) {
            // Результат остаётся непоказанным — уведомление при следующем входе.
            busy.remove(player.getUniqueId());
            return;
        }
        // Причина закрытия окна при отключении зависит от порядка событий сервера,
        // поэтому решение принимается на следующем тике, когда уже известно, вышел ли игрок.
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online == null || !online.isConnected()) {
                busy.remove(player.getUniqueId());
                return;
            }
            reveal(online, menu.caseDef(), menu.reward().hat(), menu.result(), false);
        });
    }

    public void onQuit(Player player) {
        RollMenu menu = rolling.remove(player.getUniqueId());
        if (menu != null) {
            menu.stop();
        }
        busy.remove(player.getUniqueId());
        plugin.worldAnimations().ownerQuit(player.getUniqueId());
    }

    public void shutdown() {
        for (RollMenu menu : List.copyOf(rolling.values())) {
            menu.stop();
        }
        rolling.clear();
        busy.clear();
    }

    /** При входе: показать результаты открытий, итог которых игрок не увидел. */
    public void showPending(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.storage().run(db -> db.unshown(uuid), entries -> {
            if (entries.isEmpty() || !player.isOnline()) {
                return;
            }
            plugin.messages().send(player, "open.pending-header", Placeholders.of("count", entries.size()));
            for (HistoryEntry entry : entries) {
                String hatName = plugin.catalog().hat(entry.hatId()).map(Hat::coloredName).orElse(entry.hatId());
                String caseName = plugin.catalog().caseDef(entry.caseId()).map(CaseDef::name).orElse(entry.caseId());
                String key = "DUPLICATE".equals(entry.outcome()) ? "open.pending-duplicate" : "open.pending-new";
                plugin.messages().sendPlain(player, key, Placeholders.of("hat", hatName, "case", caseName,
                        "tokens", entry.tokensAwarded(), "date", Format.date(entry.createdAt())));
                plugin.storage().submit(db -> {
                    db.markShown(entry.operationId());
                    return null;
                });
            }
        }, null);
    }

    public void sendPackRetry(Player player) {
        player.sendMessage(Text.chat(plugin.messages().raw("pack.retry-button"))
                .clickEvent(ClickEvent.runCommand("/hats pack"))
                .hoverEvent(Component.text(plugin.messages().raw("pack.retry-hover"))));
    }
}
