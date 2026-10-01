package ru.qw.qwhatcase.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.AnimationSettings;
import ru.qw.qwhatcase.config.CaseDef;
import ru.qw.qwhatcase.config.CaseReward;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.service.HatItems;
import ru.qw.qwhatcase.storage.OpeningResult;
import ru.qw.qwhatcase.util.Placeholders;

import java.util.ArrayList;
import java.util.List;

/**
 * Анимация прокрутки. Показывает УЖЕ СОХРАНЁННЫЙ результат: лента собирается так,
 * что в центральном слоте (13) останавливается выигранная шляпа.
 */
public final class RollMenu extends Menu {
    private static final int FIRST = 9;
    private static final int CENTER_INDEX = 4;

    private final CaseDef caseDef;
    private final CaseReward reward;
    private final OpeningResult result;
    private final List<Hat> reel;
    private final int[] delays;
    private BukkitTask task;
    private boolean finished;

    public RollMenu(QWHatCasePlugin plugin, Player viewer, CaseDef caseDef, CaseReward reward, OpeningResult result) {
        super(plugin, viewer, 3, plugin.messages().raw("menu.roll.title", Placeholders.of("case", caseDef.name())));
        this.caseDef = caseDef;
        this.reward = reward;
        this.result = result;
        AnimationSettings animation = caseDef.animation();
        int steps = animation.steps();
        this.reel = new ArrayList<>(steps + 9);
        for (int i = 0; i < steps + 9; i++) {
            reel.add(plugin.openings().roller().roll(caseDef).hat());
        }
        reel.set(steps + CENTER_INDEX, reward.hat());
        this.delays = delays(steps, animation.durationMs());
    }

    /** Задержки между сдвигами (в тиках) с плавным замедлением; сумма ≈ заданной длительности. */
    static int[] delays(int steps, long durationMs) {
        double totalTicks = Math.max(steps, durationMs / 50.0);
        double[] weights = new double[steps];
        double sum = 0;
        for (int i = 0; i < steps; i++) {
            double t = steps == 1 ? 1 : (double) i / (steps - 1);
            weights[i] = 1 + 7 * t * t * t;
            sum += weights[i];
        }
        int[] result = new int[steps];
        double carry = 0;
        for (int i = 0; i < steps; i++) {
            double exact = totalTicks * weights[i] / sum + carry;
            int ticks = Math.max(1, (int) Math.round(exact));
            carry = exact - ticks;
            result[i] = ticks;
        }
        return result;
    }

    public CaseDef caseDef() {
        return caseDef;
    }

    public CaseReward reward() {
        return reward;
    }

    public OpeningResult result() {
        return result;
    }

    @Override
    public boolean acceptsClicks() {
        return false;
    }

    @Override
    protected void render() {
        fill(HatItems.material(caseDef.animation().frameMaterial(), Material.BLACK_STAINED_GLASS_PANE));
        ItemStack pointer = HatItems.button(HatItems.material(caseDef.animation().pointerMaterial(), Material.LIME_STAINED_GLASS_PANE),
                msg().raw("menu.roll.pointer"), List.of());
        set(4, pointer);
        set(22, pointer);
        showWindow(0);
    }

    private void showWindow(int offset) {
        for (int i = 0; i < 9; i++) {
            Hat hat = reel.get(offset + i);
            set(FIRST + i, HatItems.icon(hat, hat.coloredName(),
                    List.of(hat.rarity().color() + hat.rarity().name()), i == CENTER_INDEX));
        }
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int step = 0;
            int wait = delays.length == 0 ? 0 : delays[0];

            @Override
            public void run() {
                if (finished) {
                    return;
                }
                if (--wait > 0) {
                    return;
                }
                step++;
                showWindow(step);
                if (caseDef.animation().sounds()) {
                    plugin.openings().playSound(viewer, caseDef.animation().tickSound(), 0.8f + 0.8f * step / delays.length);
                }
                if (step >= delays.length) {
                    finish();
                    return;
                }
                wait = delays[step];
            }
        }, 1L, 1L);
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        stopTask();
        // Небольшая пауза, чтобы игрок увидел остановку ленты.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (viewer.isOnline()) {
                plugin.openings().reveal(viewer, caseDef, reward.hat(), result, true);
            }
        }, 15L);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Остановка без показа (выход игрока, выключение сервера). */
    public void stop() {
        finished = true;
        stopTask();
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        if (finished) {
            return;
        }
        finished = true;
        stopTask();
        boolean disconnected = event.getReason() == InventoryCloseEvent.Reason.DISCONNECT;
        plugin.openings().rollInterrupted(viewer, this, disconnected);
    }
}
