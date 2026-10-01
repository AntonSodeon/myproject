package ru.qw.qwhatcase.storage;

public record CasePoint(String world, int x, int y, int z, String caseId) {
    public String key() {
        return world + ";" + x + ";" + y + ";" + z;
    }
}
