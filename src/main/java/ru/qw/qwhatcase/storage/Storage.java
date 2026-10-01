package ru.qw.qwhatcase.storage;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Однопоточная очередь запросов к БД. Основной поток сервера не блокируется,
 * а все изменения выполняются строго последовательно — одновременная выдача ключей
 * и открытие кейса не могут «перемешаться».
 */
public final class Storage {
    @FunctionalInterface
    public interface DbCall<T> {
        T apply(Database db) throws Exception;
    }

    private final Plugin plugin;
    private final Database database;
    private final ExecutorService executor;

    public Storage(Plugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "QWHatCase-DB");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Выполнить запрос в потоке БД. */
    public <T> CompletableFuture<T> submit(DbCall<T> call) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    future.complete(call.apply(database));
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.SEVERE, "Ошибка базы данных", t);
                    future.completeExceptionally(t);
                }
            });
        } catch (RuntimeException rejected) {
            future.completeExceptionally(rejected);
        }
        return future;
    }

    /** Выполнить запрос в потоке БД, результат обработать в основном потоке сервера. */
    public <T> void run(DbCall<T> call, Consumer<T> onMain, Consumer<Throwable> onError) {
        submit(call).whenComplete((value, error) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    if (onError != null) {
                        onError.accept(error);
                    }
                } else if (onMain != null) {
                    onMain.accept(value);
                }
            });
        });
    }

    /** Синхронный вызов — только вне основного потока (AsyncPlayerPreLoginEvent, консольные задачи). */
    public <T> T blocking(DbCall<T> call) throws Exception {
        return submit(call).get(30, TimeUnit.SECONDS);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Очередь БД не завершилась за 15 секунд");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            database.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка закрытия БД", e);
        }
    }
}
