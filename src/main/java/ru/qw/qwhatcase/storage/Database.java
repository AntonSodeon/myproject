package ru.qw.qwhatcase.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Доступ к SQLite. Класс не потокобезопасен: все вызовы выполняются из одного потока
 * {@link Storage}, поэтому операции над одним игроком строго упорядочены.
 * Каждое изменение — отдельная транзакция: либо применяется целиком, либо откатывается.
 */
public final class Database implements AutoCloseable {
    public static final int SCHEMA_VERSION = 1;

    /** Точка внедрения сбоя для тестов: бросает SQLException в заданной стадии транзакции. */
    public interface FaultHook {
        void at(String stage) throws SQLException;
    }

    private final Connection connection;
    private Logger logger = Logger.getLogger("QWHatCase");
    private FaultHook faultHook = stage -> {
    };

    public Database(File file) throws SQLException {
        this("jdbc:sqlite:" + file.getAbsolutePath());
    }

    public Database(String jdbcUrl) throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Драйвер SQLite не найден", e);
        }
        connection = DriverManager.getConnection(jdbcUrl);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=5000");
        }
        createSchema();
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void setFaultHook(FaultHook hook) {
        this.faultHook = hook == null ? stage -> {
        } : hook;
    }

    private void createSchema() throws SQLException {
        String[] ddl = {
                """
                CREATE TABLE IF NOT EXISTS players (
                  uuid TEXT PRIMARY KEY,
                  name TEXT,
                  name_lower TEXT,
                  tokens INTEGER NOT NULL DEFAULT 0 CHECK (tokens >= 0),
                  selected_hat TEXT,
                  first_seen INTEGER NOT NULL,
                  last_seen INTEGER NOT NULL)""",
                "CREATE INDEX IF NOT EXISTS idx_players_name ON players(name_lower)",
                """
                CREATE TABLE IF NOT EXISTS collection (
                  uuid TEXT NOT NULL,
                  hat_id TEXT NOT NULL,
                  obtained_at INTEGER NOT NULL,
                  source TEXT NOT NULL,
                  PRIMARY KEY (uuid, hat_id))""",
                """
                CREATE TABLE IF NOT EXISTS keys (
                  uuid TEXT NOT NULL,
                  key_type TEXT NOT NULL,
                  amount INTEGER NOT NULL CHECK (amount >= 0),
                  PRIMARY KEY (uuid, key_type))""",
                """
                CREATE TABLE IF NOT EXISTS openings (
                  op_id TEXT PRIMARY KEY,
                  uuid TEXT NOT NULL,
                  case_id TEXT NOT NULL,
                  key_type TEXT NOT NULL,
                  hat_id TEXT NOT NULL,
                  keys_spent INTEGER NOT NULL,
                  outcome TEXT NOT NULL,
                  tokens_awarded INTEGER NOT NULL,
                  tokens_balance INTEGER NOT NULL,
                  keys_balance INTEGER NOT NULL,
                  created_at INTEGER NOT NULL,
                  shown INTEGER NOT NULL DEFAULT 0)""",
                "CREATE INDEX IF NOT EXISTS idx_openings_player ON openings(uuid, created_at)",
                """
                CREATE TABLE IF NOT EXISTS audit (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  ts INTEGER NOT NULL,
                  actor TEXT NOT NULL,
                  target TEXT,
                  action TEXT NOT NULL,
                  details TEXT)""",
                "CREATE INDEX IF NOT EXISTS idx_audit_target ON audit(target, ts)",
                """
                CREATE TABLE IF NOT EXISTS case_points (
                  world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL,
                  case_id TEXT NOT NULL,
                  created_by TEXT,
                  created_at INTEGER NOT NULL,
                  PRIMARY KEY (world, x, y, z))""",
                """
                CREATE TABLE IF NOT EXISTS legacy_unknown (
                  source TEXT NOT NULL,
                  uuid TEXT NOT NULL,
                  raw_key TEXT NOT NULL,
                  raw_value TEXT,
                  found_at INTEGER NOT NULL,
                  PRIMARY KEY (source, uuid, raw_key))""",
                """
                CREATE TABLE IF NOT EXISTS migration_runs (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  ts INTEGER NOT NULL,
                  source TEXT NOT NULL,
                  players INTEGER NOT NULL,
                  hats_found INTEGER NOT NULL,
                  hats_added INTEGER NOT NULL,
                  unknown INTEGER NOT NULL,
                  backup TEXT,
                  report TEXT)""",
                "CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v TEXT)"
        };
        try (Statement st = connection.createStatement()) {
            for (String sql : ddl) {
                st.execute(sql);
            }
            st.execute("INSERT OR IGNORE INTO meta(k, v) VALUES ('schema_version', '" + SCHEMA_VERSION + "')");
        }
    }

    // ---------------------------------------------------------------- транзакции

    @FunctionalInterface
    private interface TxBody<T> {
        T run() throws SQLException;
    }

    private <T> T inTransaction(TxBody<T> body) throws SQLException {
        connection.setAutoCommit(false);
        try {
            T result = body.run();
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            try {
                connection.rollback();
            } catch (SQLException rollback) {
                e.addSuppressed(rollback);
            }
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    // ---------------------------------------------------------------- игроки

    private void ensurePlayer(UUID uuid, String name) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO players(uuid, name, name_lower, tokens, first_seen, last_seen) VALUES (?, ?, ?, 0, ?, ?) "
                        + "ON CONFLICT(uuid) DO UPDATE SET name = COALESCE(excluded.name, players.name), "
                        + "name_lower = COALESCE(excluded.name_lower, players.name_lower)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, name == null ? null : name.toLowerCase(Locale.ROOT));
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
    }

    /** Отмечает вход игрока и сохраняет актуальный ник. */
    public void touchPlayer(UUID uuid, String name) throws SQLException {
        ensurePlayer(uuid, name);
        try (PreparedStatement ps = connection.prepareStatement("UPDATE players SET last_seen = ? WHERE uuid = ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    public Optional<UUID> findByName(String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid FROM players WHERE name_lower = ? ORDER BY last_seen DESC LIMIT 1")) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty();
            }
        }
    }

    public boolean playerExists(UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public PlayerData load(UUID uuid) throws SQLException {
        String id = uuid.toString();
        String name = null;
        String selected = null;
        long tokens = 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name, tokens, selected_hat FROM players WHERE uuid = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    name = rs.getString(1);
                    tokens = rs.getLong(2);
                    selected = rs.getString(3);
                }
            }
        }
        Map<String, Long> owned = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT hat_id, obtained_at FROM collection WHERE uuid = ? ORDER BY obtained_at")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    owned.put(rs.getString(1), rs.getLong(2));
                }
            }
        }
        Map<String, Long> keys = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT key_type, amount FROM keys WHERE uuid = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    keys.put(rs.getString(1), rs.getLong(2));
                }
            }
        }
        return new PlayerData(uuid, name, tokens, selected, owned, keys);
    }

    public void setSelectedHat(UUID uuid, String hatId) throws SQLException {
        ensurePlayer(uuid, null);
        try (PreparedStatement ps = connection.prepareStatement("UPDATE players SET selected_hat = ? WHERE uuid = ?")) {
            ps.setString(1, hatId);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- ключи

    private long keyBalance(UUID uuid, String keyType) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT amount FROM keys WHERE uuid = ? AND key_type = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, keyType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public long getKeys(UUID uuid, String keyType) throws SQLException {
        return keyBalance(uuid, keyType);
    }

    public BalanceChange addKeys(UUID uuid, String name, String keyType, long amount, String actor) throws SQLException {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return inTransaction(() -> {
            ensurePlayer(uuid, name);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO keys(uuid, key_type, amount) VALUES (?, ?, ?) "
                            + "ON CONFLICT(uuid, key_type) DO UPDATE SET amount = amount + excluded.amount")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, keyType);
                ps.setLong(3, amount);
                ps.executeUpdate();
            }
            long balance = keyBalance(uuid, keyType);
            audit(actor, uuid, "keys.give", "key=" + keyType + " amount=" + amount + " balance=" + balance);
            return new BalanceChange(amount, balance);
        });
    }

    /** Списывает до amount ключей; баланс не опускается ниже нуля. */
    public BalanceChange takeKeys(UUID uuid, String keyType, long amount, String actor) throws SQLException {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return inTransaction(() -> {
            long before = keyBalance(uuid, keyType);
            long taken = Math.min(before, amount);
            if (taken > 0) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE keys SET amount = amount - ? WHERE uuid = ? AND key_type = ? AND amount >= ?")) {
                    ps.setLong(1, taken);
                    ps.setString(2, uuid.toString());
                    ps.setString(3, keyType);
                    ps.setLong(4, taken);
                    ps.executeUpdate();
                }
            }
            long balance = before - taken;
            audit(actor, uuid, "keys.take", "key=" + keyType + " requested=" + amount + " taken=" + taken + " balance=" + balance);
            return new BalanceChange(taken, balance);
        });
    }

    public BalanceChange setKeys(UUID uuid, String name, String keyType, long amount, String actor) throws SQLException {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        return inTransaction(() -> {
            ensurePlayer(uuid, name);
            long before = keyBalance(uuid, keyType);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO keys(uuid, key_type, amount) VALUES (?, ?, ?) "
                            + "ON CONFLICT(uuid, key_type) DO UPDATE SET amount = excluded.amount")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, keyType);
                ps.setLong(3, amount);
                ps.executeUpdate();
            }
            audit(actor, uuid, "keys.set", "key=" + keyType + " before=" + before + " balance=" + amount);
            return new BalanceChange(amount - before, amount);
        });
    }

    // ---------------------------------------------------------------- жетоны

    private long tokenBalance(UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT tokens FROM players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public long getTokens(UUID uuid) throws SQLException {
        return tokenBalance(uuid);
    }

    public BalanceChange addTokens(UUID uuid, String name, long amount, String actor, String reason) throws SQLException {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return inTransaction(() -> {
            ensurePlayer(uuid, name);
            try (PreparedStatement ps = connection.prepareStatement("UPDATE players SET tokens = tokens + ? WHERE uuid = ?")) {
                ps.setLong(1, amount);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
            long balance = tokenBalance(uuid);
            audit(actor, uuid, "tokens.give", "amount=" + amount + " balance=" + balance + (reason == null ? "" : " reason=" + reason));
            return new BalanceChange(amount, balance);
        });
    }

    public BalanceChange takeTokens(UUID uuid, long amount, String actor) throws SQLException {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return inTransaction(() -> {
            long before = tokenBalance(uuid);
            long taken = Math.min(before, amount);
            if (taken > 0) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE players SET tokens = tokens - ? WHERE uuid = ? AND tokens >= ?")) {
                    ps.setLong(1, taken);
                    ps.setString(2, uuid.toString());
                    ps.setLong(3, taken);
                    ps.executeUpdate();
                }
            }
            long balance = before - taken;
            audit(actor, uuid, "tokens.take", "requested=" + amount + " taken=" + taken + " balance=" + balance);
            return new BalanceChange(taken, balance);
        });
    }

    // ---------------------------------------------------------------- коллекция

    private boolean insertHat(UUID uuid, String hatId, long at, String source) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO collection(uuid, hat_id, obtained_at, source) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, hatId);
            ps.setLong(3, at);
            ps.setString(4, source);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean ownsHat(UUID uuid, String hatId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM collection WHERE uuid = ? AND hat_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, hatId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** @return true, если шляпа добавлена; false — уже была в коллекции. */
    public boolean grantHat(UUID uuid, String name, String hatId, String source, String actor) throws SQLException {
        return inTransaction(() -> {
            ensurePlayer(uuid, name);
            boolean added = insertHat(uuid, hatId, System.currentTimeMillis(), source);
            audit(actor, uuid, "hat.give", "hat=" + hatId + " added=" + added + " source=" + source);
            return added;
        });
    }

    /** Убирает шляпу из коллекции; если она была надета — снимает выбор. */
    public boolean revokeHat(UUID uuid, String hatId, String actor) throws SQLException {
        return inTransaction(() -> {
            int removed;
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM collection WHERE uuid = ? AND hat_id = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, hatId);
                removed = ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE players SET selected_hat = NULL WHERE uuid = ? AND selected_hat = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, hatId);
                ps.executeUpdate();
            }
            audit(actor, uuid, "hat.revoke", "hat=" + hatId + " removed=" + (removed > 0));
            return removed > 0;
        });
    }

    // ---------------------------------------------------------------- открытие кейса

    /**
     * Атомарно: списывает ключи, сохраняет результат и начисляет шляпу либо компенсацию.
     * Повторный вызов с тем же ID операции ничего не меняет и возвращает сохранённый результат.
     */
    public OpeningResult performOpening(OpeningRequest request) {
        try {
            return inTransaction(() -> {
                Optional<OpeningResult> existing = findOpening(request.operationId());
                if (existing.isPresent()) {
                    OpeningResult old = existing.get();
                    return new OpeningResult(OpeningResult.Status.ALREADY_PROCESSED, old.operationId(), old.caseId(),
                            old.hatId(), old.outcome(), old.tokensAwarded(), old.tokensBalance(), old.keysBalance(), old.createdAt());
                }
                ensurePlayer(request.player(), request.playerName());
                int updated;
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE keys SET amount = amount - ? WHERE uuid = ? AND key_type = ? AND amount >= ?")) {
                    ps.setInt(1, request.keyCost());
                    ps.setString(2, request.player().toString());
                    ps.setString(3, request.keyType());
                    ps.setInt(4, request.keyCost());
                    updated = ps.executeUpdate();
                }
                if (updated != 1) {
                    return OpeningResult.failure(OpeningResult.Status.NOT_ENOUGH_KEYS, request,
                            keyBalance(request.player(), request.keyType()));
                }
                faultHook.at("opening.after-keys");
                boolean isNew = insertHat(request.player(), request.hatId(), request.createdAt(), "case:" + request.caseId());
                long awarded = 0;
                if (!isNew && request.duplicateTokens() > 0) {
                    awarded = request.duplicateTokens();
                    try (PreparedStatement ps = connection.prepareStatement("UPDATE players SET tokens = tokens + ? WHERE uuid = ?")) {
                        ps.setLong(1, awarded);
                        ps.setString(2, request.player().toString());
                        ps.executeUpdate();
                    }
                }
                faultHook.at("opening.after-reward");
                long tokens = tokenBalance(request.player());
                long keys = keyBalance(request.player(), request.keyType());
                OpeningResult.Outcome outcome = isNew ? OpeningResult.Outcome.NEW : OpeningResult.Outcome.DUPLICATE;
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO openings(op_id, uuid, case_id, key_type, hat_id, keys_spent, outcome, tokens_awarded, "
                                + "tokens_balance, keys_balance, created_at, shown) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)")) {
                    ps.setString(1, request.operationId().toString());
                    ps.setString(2, request.player().toString());
                    ps.setString(3, request.caseId());
                    ps.setString(4, request.keyType());
                    ps.setString(5, request.hatId());
                    ps.setInt(6, request.keyCost());
                    ps.setString(7, outcome.name());
                    ps.setLong(8, awarded);
                    ps.setLong(9, tokens);
                    ps.setLong(10, keys);
                    ps.setLong(11, request.createdAt());
                    ps.executeUpdate();
                }
                audit(request.playerName() == null ? request.player().toString() : request.playerName(), request.player(),
                        "case.open", "op=" + request.operationId() + " case=" + request.caseId() + " hat=" + request.hatId()
                                + " keys=" + request.keyCost() + " outcome=" + outcome + " tokens=" + awarded);
                faultHook.at("opening.before-commit");
                return new OpeningResult(OpeningResult.Status.SUCCESS, request.operationId(), request.caseId(),
                        request.hatId(), outcome, awarded, tokens, keys, request.createdAt());
            });
        } catch (SQLException | RuntimeException e) {
            logger.log(Level.SEVERE, "Ошибка сохранения открытия " + request.operationId() + " игрока " + request.player()
                    + " (кейс " + request.caseId() + "): транзакция откатана, ключи не списаны", e);
            return OpeningResult.failure(OpeningResult.Status.ERROR, request, -1);
        }
    }

    public Optional<OpeningResult> findOpening(UUID operationId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT case_id, hat_id, outcome, tokens_awarded, tokens_balance, keys_balance, created_at FROM openings WHERE op_id = ?")) {
            ps.setString(1, operationId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new OpeningResult(OpeningResult.Status.SUCCESS, operationId, rs.getString(1), rs.getString(2),
                        OpeningResult.Outcome.valueOf(rs.getString(3)), rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7)));
            }
        }
    }

    public void markShown(UUID operationId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE openings SET shown = 1 WHERE op_id = ?")) {
            ps.setString(1, operationId.toString());
            ps.executeUpdate();
        }
    }

    public List<HistoryEntry> unshown(UUID uuid) throws SQLException {
        return history("WHERE uuid = ? AND shown = 0 ORDER BY created_at", uuid, 1000, 0);
    }

    public List<HistoryEntry> history(UUID uuid, int limit, int offset) throws SQLException {
        return history("WHERE uuid = ? ORDER BY created_at DESC", uuid, limit, offset);
    }

    public int historyCount(UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM openings WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public Optional<HistoryEntry> historyEntry(UUID operationId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(HISTORY_SELECT + " WHERE op_id = ?")) {
            ps.setString(1, operationId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readHistory(rs)) : Optional.empty();
            }
        }
    }

    private static final String HISTORY_SELECT = "SELECT op_id, uuid, case_id, hat_id, keys_spent, outcome, tokens_awarded, "
            + "tokens_balance, created_at, shown FROM openings";

    private List<HistoryEntry> history(String where, UUID uuid, int limit, int offset) throws SQLException {
        List<HistoryEntry> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(HISTORY_SELECT + " " + where + " LIMIT ? OFFSET ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(readHistory(rs));
                }
            }
        }
        return result;
    }

    private static HistoryEntry readHistory(ResultSet rs) throws SQLException {
        return new HistoryEntry(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getString(3),
                rs.getString(4), rs.getInt(5), rs.getString(6), rs.getLong(7), rs.getLong(8), rs.getLong(9), rs.getInt(10) != 0);
    }

    // ---------------------------------------------------------------- каталог за жетоны

    /** Атомарно: списывает жетоны и открывает шляпу. Уже открытая шляпа не продаётся. */
    public PurchaseResult purchase(UUID uuid, String name, String hatId, long price) {
        try {
            return inTransaction(() -> {
                ensurePlayer(uuid, name);
                if (ownsHat(uuid, hatId)) {
                    return new PurchaseResult(PurchaseResult.Status.ALREADY_OWNED, tokenBalance(uuid));
                }
                int updated;
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE players SET tokens = tokens - ? WHERE uuid = ? AND tokens >= ?")) {
                    ps.setLong(1, price);
                    ps.setString(2, uuid.toString());
                    ps.setLong(3, price);
                    updated = ps.executeUpdate();
                }
                if (updated != 1) {
                    return new PurchaseResult(PurchaseResult.Status.NOT_ENOUGH_TOKENS, tokenBalance(uuid));
                }
                faultHook.at("purchase.after-tokens");
                if (!insertHat(uuid, hatId, System.currentTimeMillis(), "shop")) {
                    throw new SQLException("hat already owned (race)");
                }
                long balance = tokenBalance(uuid);
                audit(name == null ? uuid.toString() : name, uuid, "shop.buy", "hat=" + hatId + " price=" + price + " balance=" + balance);
                faultHook.at("purchase.before-commit");
                return new PurchaseResult(PurchaseResult.Status.SUCCESS, balance);
            });
        } catch (SQLException | RuntimeException e) {
            logger.log(Level.SEVERE, "Ошибка покупки шляпы " + hatId + " игроком " + uuid + ": транзакция откатана", e);
            return new PurchaseResult(PurchaseResult.Status.ERROR, -1);
        }
    }

    // ---------------------------------------------------------------- точки кейсов

    public List<CasePoint> points() throws SQLException {
        List<CasePoint> result = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT world, x, y, z, case_id FROM case_points ORDER BY world, x, y, z")) {
            while (rs.next()) {
                result.add(new CasePoint(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getString(5)));
            }
        }
        return result;
    }

    public void addPoint(CasePoint point, String actor) throws SQLException {
        inTransaction(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO case_points(world, x, y, z, case_id, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?) "
                            + "ON CONFLICT(world, x, y, z) DO UPDATE SET case_id = excluded.case_id")) {
                ps.setString(1, point.world());
                ps.setInt(2, point.x());
                ps.setInt(3, point.y());
                ps.setInt(4, point.z());
                ps.setString(5, point.caseId());
                ps.setString(6, actor);
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            }
            audit(actor, null, "point.add", point.key() + " case=" + point.caseId());
            return null;
        });
    }

    public boolean removePoint(String world, int x, int y, int z, String actor) throws SQLException {
        return inTransaction(() -> {
            int removed;
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM case_points WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                ps.setString(1, world);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                removed = ps.executeUpdate();
            }
            audit(actor, null, "point.remove", world + ";" + x + ";" + y + ";" + z + " removed=" + (removed > 0));
            return removed > 0;
        });
    }

    // ---------------------------------------------------------------- миграция

    public record ImportStats(int players, int hatsFound, int hatsAdded, int unknown) {
    }

    /**
     * Переносит коллекции старого плагина в одной транзакции. INSERT OR IGNORE гарантирует,
     * что повторный запуск не создаёт дубликаты.
     */
    public ImportStats importLegacy(String source, List<LegacyImport> players, String actor, String backup,
                                    String report) throws SQLException {
        return inTransaction(() -> {
            int found = 0;
            int added = 0;
            int unknown = 0;
            long now = System.currentTimeMillis();
            for (LegacyImport player : players) {
                ensurePlayer(player.uuid(), player.name());
                for (String hat : player.hatIds()) {
                    found++;
                    if (insertHat(player.uuid(), hat, now, "migration:" + source)) {
                        added++;
                    }
                }
                for (LegacyImport.Unknown u : player.unknown()) {
                    unknown++;
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT OR IGNORE INTO legacy_unknown(source, uuid, raw_key, raw_value, found_at) VALUES (?, ?, ?, ?, ?)")) {
                        ps.setString(1, source);
                        ps.setString(2, player.uuid().toString());
                        ps.setString(3, u.rawKey());
                        ps.setString(4, u.rawValue());
                        ps.setLong(5, now);
                        ps.executeUpdate();
                    }
                }
            }
            faultHook.at("migration.before-commit");
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO migration_runs(ts, source, players, hats_found, hats_added, unknown, backup, report) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setLong(1, now);
                ps.setString(2, source);
                ps.setInt(3, players.size());
                ps.setInt(4, found);
                ps.setInt(5, added);
                ps.setInt(6, unknown);
                ps.setString(7, backup);
                ps.setString(8, report);
                ps.executeUpdate();
            }
            audit(actor, null, "migration.run", "source=" + source + " players=" + players.size() + " found=" + found
                    + " added=" + added + " unknown=" + unknown);
            return new ImportStats(players.size(), found, added, unknown);
        });
    }

    public int migrationRuns(String source) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM migration_runs WHERE source = ?")) {
            ps.setString(1, source);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ---------------------------------------------------------------- журнал

    public void audit(String actor, UUID target, String action, String details) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO audit(ts, actor, target, action, details) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, actor == null ? "?" : actor);
            ps.setString(3, target == null ? null : target.toString());
            ps.setString(4, action);
            ps.setString(5, details);
            ps.executeUpdate();
        }
    }

    public record AuditEntry(long ts, String actor, String action, String details) {
    }

    public List<AuditEntry> auditFor(UUID target, int limit) throws SQLException {
        List<AuditEntry> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT ts, actor, action, details FROM audit WHERE target = ? ORDER BY id DESC LIMIT ?")) {
            ps.setString(1, target.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new AuditEntry(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)));
                }
            }
        }
        return result;
    }

    /** Резервная копия БД средствами SQLite (консистентный снимок). */
    public void backupTo(File file) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("VACUUM INTO '" + file.getAbsolutePath().replace("'", "''") + "'");
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
