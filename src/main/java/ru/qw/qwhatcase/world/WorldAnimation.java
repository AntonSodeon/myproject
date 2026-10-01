package ru.qw.qwhatcase.world;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.CaseDef;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.config.Rarity;
import ru.qw.qwhatcase.service.HatItems;
import ru.qw.qwhatcase.storage.CasePoint;
import ru.qw.qwhatcase.storage.OpeningResult;
import ru.qw.qwhatcase.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Анимация открытия над блоком. Показывает УЖЕ СОХРАНЁННЫЙ результат (или случайный — в предпросмотре):
 * никаких выдач, списаний и повторных розыгрышей здесь нет.
 *
 * Лента — ограниченный пул ItemDisplay (видимые модели + 2), переиспользуемый всю прокрутку:
 * модель с индексом k ленты всегда показывается сущностью k mod P. Сущность «переезжает» на другой край,
 * только когда её масштаб равен нулю, поэтому переезд не виден.
 */
public final class WorldAnimation {
    private enum Phase { SPIN, RESULT, DONE }

    private final QWHatCasePlugin plugin;
    private final WorldAnimationService service;
    final String operation;
    final CasePoint point;
    final CaseDef caseDef;
    final WorldAnimSettings s;
    final Hat winner;
    final OpeningResult result;
    final UUID owner;
    final String ownerName;
    final boolean preview;
    final String busyToken;
    private final List<Hat> reel = new ArrayList<>();

    private World world;
    private Chunk ticketChunk;
    private Location center;
    private Vector axis;
    /** Горизонтальное направление «вправо» для зрителя (для боковых меток центра). */
    private Vector side;
    private float yaw;
    private ItemDisplay[] pool = new ItemDisplay[0];
    private int[] assigned = new int[0];
    private final List<TextDisplay> markers = new ArrayList<>();
    private TextDisplay resultLabel;
    private BukkitTask task;
    private Phase phase = Phase.SPIN;
    private int tick;
    private int resultTick;
    private int lastCenter;
    private boolean slowdownPlayed;
    private boolean revealed;
    boolean ownerLeft;
    private float spin;

    WorldAnimation(QWHatCasePlugin plugin, WorldAnimationService service, String operation, CasePoint point, CaseDef caseDef,
                   Hat winner, OpeningResult result, UUID owner, String ownerName, boolean preview, String busyToken) {
        this.plugin = plugin;
        this.service = service;
        this.operation = operation;
        this.point = point;
        this.caseDef = caseDef;
        this.s = caseDef.worldAnimation();
        this.winner = winner;
        this.result = result;
        this.owner = owner;
        this.ownerName = ownerName;
        this.preview = preview;
        this.busyToken = busyToken;
    }

    // ------------------------------------------------------------------ старт

    /** @return false, если анимацию запустить нельзя (мир/чанк недоступен) — вызывающий сам показывает итог. */
    boolean start(Location viewer) {
        world = Bukkit.getWorld(point.world());
        if (world == null || !world.isChunkLoaded(point.x() >> 4, point.z() >> 4)) {
            return false;
        }
        ticketChunk = world.getChunkAt(point.x() >> 4, point.z() >> 4);
        // Удерживаем чанк на время короткой анимации; тикет снимается в cleanup().
        ticketChunk.addPluginChunkTicket(plugin);
        center = new Location(world, point.x() + 0.5, point.y() + 1.0 + s.height(), point.z() + 0.5);

        // Ориентация фиксируется один раз: направление от блока к игроку.
        Vector facing = viewer != null && viewer.getWorld() == world
                ? viewer.toVector().subtract(center.toVector()).setY(0) : new Vector(0, 0, 1);
        if (facing.lengthSquared() < 1.0E-4) {
            facing = new Vector(0, 0, 1);
        }
        facing.normalize();
        side = new Vector(facing.getZ(), 0, -facing.getX());
        // Вертикальная лента: ось вверх, модели движутся сверху вниз. Горизонтальная: ось вправо от зрителя.
        axis = s.vertical() ? new Vector(0, 1, 0) : side.clone();
        yaw = (float) Math.toDegrees(Math.atan2(-facing.getX(), facing.getZ())) + s.modelYawOffset();

        buildReel();
        try {
            spawnPool();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось создать анимацию на точке " + point.key(), e);
            cleanup();
            return false;
        }
        double raise = plugin.catalog().caseDef(point.caseId()).map(d -> d.label().raise()).orElse(0.9);
        if (s.vertical()) {
            // Название поднимается над верхом полосы, чтобы не перекрывать модели.
            double labelHeight = plugin.catalog().caseDef(point.caseId()).map(d -> d.label().height()).orElse(0.45);
            double stripTop = s.height() + ((s.visibleModels() - 1) / 2 + 1) * s.spacing() + 0.35;
            raise = Math.max(raise, stripTop - labelHeight);
        }
        plugin.labels().raise(point, raise);
        sound(s.startSound(), center, 1f);
        particles(s.startParticles(), new Location(world, point.x() + 0.5, point.y() + 0.5, point.z() + 0.5), null);
        update(0);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        return true;
    }

    private void buildReel() {
        int half = (s.visibleModels() - 1) / 2;
        int length = s.scrollItems() + half + 3;
        for (int i = 0; i < length; i++) {
            reel.add(plugin.openings().roller().roll(caseDef).hat());
        }
        // Визуальная лента случайна, но гарантированно останавливается на сохранённой награде.
        reel.set(s.scrollItems(), winner);
    }

    private void spawnPool() {
        int size = s.visibleModels() + 2;
        pool = new ItemDisplay[size];
        assigned = new int[size];
        ItemDisplay.ItemDisplayTransform transform;
        try {
            transform = ItemDisplay.ItemDisplayTransform.valueOf(s.modelTransform());
        } catch (IllegalArgumentException e) {
            transform = ItemDisplay.ItemDisplayTransform.HEAD;
        }
        Location at = center.clone();
        at.setYaw(yaw);
        for (int i = 0; i < size; i++) {
            ItemDisplay.ItemDisplayTransform t = transform;
            pool[i] = world.spawn(at, ItemDisplay.class, d -> {
                WorldEntities.mark(d, WorldEntities.ANIMATION, point.key(), operation);
                d.setItemDisplayTransform(t);
                d.setBillboard(Display.Billboard.FIXED);
                d.setTransformation(WorldEntities.scaled(0f));
                d.setViewRange((float) (s.viewRange() / 64.0));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTeleportDuration(2);
                d.setInterpolationDuration(2);
            });
            assigned[i] = -1;
        }
        if (s.vertical()) {
            // Метки центра по бокам полосы: ▶ слева и ◀ справа от зрителя.
            double gap = Math.max(0.45, s.centerScale() * 0.6 + 0.2);
            spawnMarker(center.clone().add(side.clone().multiply(-gap)).add(0, -0.12, 0), s.centerMarker());
            spawnMarker(center.clone().add(side.clone().multiply(gap)).add(0, -0.12, 0), s.centerMarkerRight());
        } else {
            spawnMarker(center.clone().add(0, Math.max(0.45, s.centerScale() * 0.75), 0), s.centerMarker());
        }
    }

    private void spawnMarker(Location at, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        markers.add(world.spawn(at, TextDisplay.class, td -> {
            WorldEntities.mark(td, WorldEntities.ANIMATION, point.key(), operation);
            td.text(Text.chat(text));
            td.setBillboard(Display.Billboard.CENTER);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            td.setShadowed(true);
            td.setViewRange((float) (s.viewRange() / 64.0));
            td.setBrightness(new Display.Brightness(15, 15));
        }));
    }

    // ------------------------------------------------------------------ кадр

    private boolean entitiesValid() {
        for (ItemDisplay d : pool) {
            if (d == null || !d.isValid()) {
                return false;
            }
        }
        return true;
    }

    private void tick() {
        try {
            if (phase == Phase.DONE) {
                return;
            }
            if (!entitiesValid()) {
                service.abort(this, "entities-removed");
                return;
            }
            if (phase == Phase.SPIN) {
                tick++;
                update(s.offsetAt(tick));
                if (!slowdownPlayed && tick >= s.slowdownAt() * s.spinTicks()) {
                    slowdownPlayed = true;
                    sound(s.slowdownSound(), center, 1f);
                }
                if (tick >= s.spinTicks()) {
                    enterResult();
                }
            } else {
                resultTick++;
                if (resultTick % 4 == 0 && s.rotationSpeed() > 0) {
                    spin += (float) Math.toRadians(s.rotationSpeed() * 4 / 20.0);
                    ItemDisplay w = winnerEntity();
                    w.setInterpolationDelay(0);
                    w.setInterpolationDuration(4);
                    w.setTransformation(WorldEntities.scaledRotated(s.winnerScale(), spin, 0));
                }
                if (resultTick == s.resultTicks() / 2) {
                    particles(s.resultParticles(), center.clone().add(0, s.winnerRise(), 0), winner.rarity());
                }
                if (resultTick >= s.resultTicks()) {
                    service.finish(this);
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка анимации " + operation + " — анимация остановлена, награда сохранена", e);
            service.abort(this, "error");
        }
    }

    /** Расстановка ленты при смещении o (в моделях). */
    private void update(double o) {
        int half = (s.visibleModels() - 1) / 2;
        int size = pool.length;
        boolean[] used = new boolean[size];
        int kMin = (int) Math.ceil(o - half - 1);
        int kMax = (int) Math.floor(o + half + 1);
        for (int k = kMin; k <= kMax; k++) {
            if (k < 0 || k >= reel.size()) {
                continue;
            }
            int j = Math.floorMod(k, size);
            used[j] = true;
            ItemDisplay d = pool[j];
            if (assigned[j] != k) {
                assigned[j] = k;
                d.setItemStack(HatItems.displayItem(reel.get(k)));
            }
            double t = k - o;
            Location at = center.clone().add(axis.clone().multiply(t * s.spacing()));
            at.setYaw(yaw);
            d.teleport(at);
            double visible = Math.max(0, Math.min(1, half + 1 - Math.abs(t)));
            double centerBoost = Math.max(0, 1 - Math.abs(t));
            float scale = (float) ((s.modelScale() + (s.centerScale() - s.modelScale()) * centerBoost) * visible);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(2);
            d.setTransformation(WorldEntities.scaled(scale));
            boolean glow = s.glowCenter() && Math.abs(t) < 0.5;
            if (d.isGlowing() != glow) {
                d.setGlowing(glow);
                if (glow) {
                    d.setGlowColorOverride(rarityColor(reel.get(k).rarity()));
                }
            }
        }
        for (int j = 0; j < size; j++) {
            if (!used[j]) {
                pool[j].setTransformation(WorldEntities.scaled(0f));
                pool[j].setGlowing(false);
                assigned[j] = -1;
            }
        }
        int centerIndex = (int) Math.floor(o + 0.5);
        if (centerIndex > lastCenter) {
            lastCenter = centerIndex;
            float progress = Math.min(1f, tick / (float) s.spinTicks());
            SoundSpec ts = s.tickSound();
            float pitch = ts.pitch() + (s.tickPitchEnd() - ts.pitch()) * progress;
            sound(ts, center, pitch / Math.max(0.01f, ts.pitch()));
            particles(s.spinParticles(), center, null);
        }
    }

    private ItemDisplay winnerEntity() {
        return pool[Math.floorMod(s.scrollItems(), pool.length)];
    }

    // ------------------------------------------------------------------ результат

    private void enterResult() {
        phase = Phase.RESULT;
        update(s.scrollItems());
        ItemDisplay w = winnerEntity();
        for (ItemDisplay d : pool) {
            if (d != w) {
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(6);
                d.setTransformation(WorldEntities.scaled(0f));
                d.setGlowing(false);
            }
        }
        for (TextDisplay m : markers) {
            if (m.isValid()) {
                m.remove();
            }
        }
        markers.clear();
        Location up = center.clone().add(0, s.winnerRise(), 0);
        up.setYaw(yaw);
        w.setTeleportDuration(8);
        w.teleport(up);
        w.setInterpolationDelay(0);
        w.setInterpolationDuration(8);
        w.setTransformation(WorldEntities.scaled(s.winnerScale()));
        w.setGlowing(true);
        w.setGlowColorOverride(rarityColor(winner.rarity()));

        boolean duplicate = result != null && result.outcome() == OpeningResult.Outcome.DUPLICATE;
        Map<String, Object> ph = placeholders();
        List<String> lines = duplicate ? s.duplicateLines() : s.resultLines();
        String text = String.join("\n", lines.stream().map(l -> Text.apply(l, ph)).toList());
        resultLabel = world.spawn(center.clone().add(0, s.winnerRise() + s.resultLabelOffset() + s.winnerScale() * 0.35, 0),
                TextDisplay.class, td -> {
                    WorldEntities.mark(td, WorldEntities.ANIMATION, point.key(), operation);
                    td.text(Text.chat((preview ? plugin.messages().raw("world.preview-tag") : "") + text));
                    td.setBillboard(Display.Billboard.CENTER);
                    td.setShadowed(s.resultLabelShadow());
                    Color bg = WorldEntities.argb(s.resultLabelBackground());
                    if (bg == null) {
                        td.setDefaultBackground(true);
                    } else {
                        td.setBackgroundColor(bg);
                    }
                    td.setTransformation(WorldEntities.scaled(s.resultLabelScale()));
                    td.setViewRange((float) (s.viewRange() / 64.0));
                    td.setBrightness(new Display.Brightness(15, 15));
                });
        SoundSpec finalSound = duplicate ? s.duplicateSound() : s.winSound();
        if (!duplicate && !winner.rarity().winSound().isBlank() && finalSound.active()) {
            // Особый звук редкости (например, легендарной) с громкостью/тоном из настроек анимации.
            finalSound = new SoundSpec(true, winner.rarity().winSound(), finalSound.volume(), finalSound.pitch());
        }
        sound(finalSound, up, 1f);
        particles(s.resultParticles(), up, winner.rarity());
        reveal();
    }

    Map<String, Object> placeholders() {
        Map<String, Object> ph = new java.util.LinkedHashMap<>();
        ph.put("hat", winner.coloredName());
        ph.put("rarity", winner.rarity().color() + winner.rarity().name());
        ph.put("case", caseDef.name());
        ph.put("player", ownerName);
        ph.put("tokens", result == null ? caseDef.compensationFor(winner) : result.tokensAwarded());
        ph.put("balance", result == null ? "-" : result.tokensBalance());
        return ph;
    }

    /** Сообщение владельцу о результате (один раз). Награда уже выдана транзакцией — здесь её нет. */
    void reveal() {
        if (revealed) {
            return;
        }
        revealed = true;
        service.revealToOwner(this);
    }

    boolean revealed() {
        return revealed;
    }

    // ------------------------------------------------------------------ эффекты

    private void sound(SoundSpec spec, Location at, float pitchFactor) {
        if (spec == null || !spec.active() || world == null) {
            return;
        }
        try {
            // Звук мира: слышат только игроки рядом (дальность зависит от громкости), не весь сервер.
            world.playSound(at, spec.sound(), SoundCategory.BLOCKS, spec.volume(), Math.max(0.5f, Math.min(2f, spec.pitch() * pitchFactor)));
        } catch (RuntimeException ignored) {
            // Неверный звук не должен ломать анимацию.
        }
    }

    private int particlesThisTick = -1;
    private int particlesTick = -1;

    private void particles(ParticleSpec spec, Location at, Rarity rarity) {
        if (spec == null || !spec.enabled() || spec.count() <= 0 || world == null) {
            return;
        }
        int now = tick * 1000 + resultTick;
        if (particlesTick != now) {
            particlesTick = now;
            particlesThisTick = 0;
        }
        int count = Math.min(spec.count(), s.maxParticlesPerTick() - particlesThisTick);
        if (count <= 0) {
            return;
        }
        particlesThisTick += count;
        try {
            Particle particle = Particle.valueOf(spec.type());
            Object data = null;
            if (particle.getDataType() == Particle.DustOptions.class) {
                Color color = rarity != null && spec.color().isBlank() ? rarityBukkitColor(rarity) : parseColor(spec.color(), Color.WHITE);
                data = new Particle.DustOptions(color, spec.size());
            } else if (particle.getDataType() == Color.class) {
                data = rarity != null ? rarityBukkitColor(rarity) : parseColor(spec.color(), Color.WHITE);
            } else if (particle.getDataType() != Void.class) {
                particle = Particle.END_ROD;
            }
            // force=false: частицы видят только игроки поблизости.
            world.spawnParticle(particle, at, count, spec.offsetX(), spec.offsetY(), spec.offsetZ(), spec.speed(), data, false);
        } catch (RuntimeException ignored) {
            // Неверный тип частиц не должен ломать анимацию.
        }
    }

    static TextColor rarityTextColor(Rarity rarity) {
        String c = rarity.color();
        java.util.regex.Matcher hex = java.util.regex.Pattern.compile("&#([0-9a-fA-F]{6})").matcher(c);
        if (hex.find()) {
            return TextColor.fromHexString("#" + hex.group(1));
        }
        for (int i = 0; i + 1 < c.length(); i++) {
            if (c.charAt(i) == '&') {
                NamedTextColor named = switch (Character.toLowerCase(c.charAt(i + 1))) {
                    case '0' -> NamedTextColor.BLACK;
                    case '1' -> NamedTextColor.DARK_BLUE;
                    case '2' -> NamedTextColor.DARK_GREEN;
                    case '3' -> NamedTextColor.DARK_AQUA;
                    case '4' -> NamedTextColor.DARK_RED;
                    case '5' -> NamedTextColor.DARK_PURPLE;
                    case '6' -> NamedTextColor.GOLD;
                    case '7' -> NamedTextColor.GRAY;
                    case '8' -> NamedTextColor.DARK_GRAY;
                    case '9' -> NamedTextColor.BLUE;
                    case 'a' -> NamedTextColor.GREEN;
                    case 'b' -> NamedTextColor.AQUA;
                    case 'c' -> NamedTextColor.RED;
                    case 'd' -> NamedTextColor.LIGHT_PURPLE;
                    case 'e' -> NamedTextColor.YELLOW;
                    case 'f' -> NamedTextColor.WHITE;
                    default -> null;
                };
                if (named != null) {
                    return named;
                }
            }
        }
        return NamedTextColor.WHITE;
    }

    static Color rarityBukkitColor(Rarity rarity) {
        return Color.fromRGB(rarityTextColor(rarity).value());
    }

    private static Color rarityColor(Rarity rarity) {
        return rarityBukkitColor(rarity);
    }

    private static Color parseColor(String value, Color fallback) {
        if (value != null && value.matches("#?[0-9a-fA-F]{6}")) {
            return Color.fromRGB(Integer.parseInt(value.replace("#", ""), 16));
        }
        return fallback;
    }

    // ------------------------------------------------------------------ завершение

    /** Удалить все временные сущности, отменить задачу, снять тикет чанка и вернуть надпись. Идемпотентно. */
    void cleanup() {
        phase = Phase.DONE;
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (ItemDisplay d : pool) {
            if (d != null && d.isValid()) {
                d.remove();
            }
        }
        for (TextDisplay m : markers) {
            if (m.isValid()) {
                m.remove();
            }
        }
        markers.clear();
        if (resultLabel != null && resultLabel.isValid()) {
            resultLabel.remove();
        }
        // Страховка: всё, что помечено ID этой операции, в чанке точки.
        if (ticketChunk != null && ticketChunk.isLoaded()) {
            for (Entity e : ticketChunk.getEntities()) {
                if (operation.equals(WorldEntities.operation(e))) {
                    e.remove();
                }
            }
            ticketChunk.removePluginChunkTicket(plugin);
        }
        plugin.labels().lower(point);
    }

    boolean inChunk(String world, int cx, int cz) {
        return point.world().equals(world) && point.x() >> 4 == cx && point.z() >> 4 == cz;
    }

    Player ownerPlayer() {
        Player p = Bukkit.getPlayer(owner);
        return p != null && p.isConnected() ? p : null;
    }

    int activeEntities() {
        int n = 0;
        for (ItemDisplay d : pool) {
            if (d != null && d.isValid()) {
                n++;
            }
        }
        return n;
    }
}
