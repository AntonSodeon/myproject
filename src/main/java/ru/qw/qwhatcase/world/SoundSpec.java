package ru.qw.qwhatcase.world;

/**
 * Звук: идентификатор (кастомное событие ресурс-пака, например minecraft:custom.mystery_crate.scroll,
 * или обычный звук Minecraft), громкость и высота тона. Пустой sound или enabled=false — без звука.
 */
public record SoundSpec(boolean enabled, String sound, float volume, float pitch) {
    public static final SoundSpec NONE = new SoundSpec(false, "", 1f, 1f);

    public boolean active() {
        return enabled && sound != null && !sound.isBlank();
    }
}
