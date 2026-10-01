package ru.qw.qwhatcase.world;

/**
 * Настройки анимации открытия в игровом мире. Снимок фиксируется при старте открытия:
 * перезагрузка конфигурации не влияет на уже идущую анимацию.
 */
public record WorldAnimSettings(
        boolean enabled,
        long spinMs, long resultMs,
        int visibleModels, int scrollItems,
        double height, double spacing,
        float modelScale, float centerScale, float winnerScale, double winnerRise,
        double rotationSpeed, double decelerationPower, double viewRange,
        String modelTransform, float modelYawOffset,
        boolean glowCenter, String centerMarker, double slowdownAt,
        SoundSpec startSound, SoundSpec tickSound, float tickPitchEnd, SoundSpec slowdownSound,
        SoundSpec winSound, SoundSpec duplicateSound,
        ParticleSpec startParticles, ParticleSpec spinParticles, ParticleSpec resultParticles, int maxParticlesPerTick,
        java.util.List<String> resultLines, java.util.List<String> duplicateLines,
        float resultLabelScale, double resultLabelOffset, String resultLabelBackground, boolean resultLabelShadow,
        String direction, String centerMarkerRight) {

    /** Вертикальная лента: модели едут сверху вниз через центр. */
    public boolean vertical() {
        return !"HORIZONTAL".equals(direction);
    }

    public int spinTicks() {
        return (int) Math.max(20, Math.round(spinMs / 50.0));
    }

    public int resultTicks() {
        return (int) Math.max(10, Math.round(resultMs / 50.0));
    }

    /**
     * Смещение ленты (в «моделях») к моменту tick: ease-out 1 - (1 - x)^p.
     * Скорость монотонно падает до нуля, лента останавливается ровно на scrollItems.
     */
    public double offsetAt(int tick) {
        double x = Math.min(1.0, Math.max(0.0, tick / (double) spinTicks()));
        return scrollItems * (1.0 - Math.pow(1.0 - x, decelerationPower));
    }
}
