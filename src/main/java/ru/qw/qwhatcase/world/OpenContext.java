package ru.qw.qwhatcase.world;

import ru.qw.qwhatcase.storage.CasePoint;

/**
 * Явный контекст открытия меню кейса. Передаётся из места, где меню открыто, до кнопки «Открыть»:
 * источник никогда не определяется по расстоянию до ближайшего блока.
 *
 * @param point точка кейса (только для BLOCK), иначе null
 */
public record OpenContext(Source source, CasePoint point) {
    public enum Source {
        /** /cases open */
        COMMAND,
        /** Меню кейсов / /cases view */
        MENU,
        /** ПКМ по привязанному блоку */
        BLOCK
    }

    public static final OpenContext COMMAND = new OpenContext(Source.COMMAND, null);
    public static final OpenContext MENU = new OpenContext(Source.MENU, null);

    public static OpenContext block(CasePoint point) {
        return new OpenContext(Source.BLOCK, point);
    }

    public boolean isBlock() {
        return source == Source.BLOCK && point != null;
    }

    /** Ключ точки для журнала (null — не через блок). */
    public String pointKey() {
        return isBlock() ? point.key() : null;
    }
}
