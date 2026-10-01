package ru.qw.qwhatcase.storage;

/** Результат изменения баланса: сколько реально изменилось и новый баланс. */
public record BalanceChange(long changed, long balance) {
}
