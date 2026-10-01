package ru.qw.qwhatcase.world;

/**
 * Частицы: тип (имя Particle, например END_ROD, DUST, TOTEM_OF_UNDYING), количество, разброс и скорость.
 * Для DUST цвет берётся из редкости (результат) или из color.
 */
public record ParticleSpec(boolean enabled, String type, int count, double offsetX, double offsetY, double offsetZ,
                           double speed, String color, float size) {
    public static final ParticleSpec NONE = new ParticleSpec(false, "END_ROD", 0, 0, 0, 0, 0, "", 1f);
}
