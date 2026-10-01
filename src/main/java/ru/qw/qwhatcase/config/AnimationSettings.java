package ru.qw.qwhatcase.config;

/**
 * Настройки анимации кейса.
 *
 * @param enabled    false = режим без анимации
 * @param durationMs общая длительность прокрутки
 * @param steps      количество сдвигов ленты
 */
public record AnimationSettings(boolean enabled, long durationMs, int steps, boolean sounds,
                                String tickSound, String finishSound, String duplicateSound,
                                String frameMaterial, String pointerMaterial) {
}
