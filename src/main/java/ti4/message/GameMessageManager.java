package ti4.message;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.helpers.StringHelper;
import ti4.json.PersistenceManager;
import ti4.logging.BotLogger;
import ti4.settings.GlobalSettings;
import ti4.settings.GlobalSettings.ImplementedSettings;
import ti4.spring.context.SpringContext;
import ti4.spring.service.gamemessage.GameMessageService;

@UtilityClass
public class GameMessageManager {

    @Deprecated(forRemoval = true, since = "2026-10")
    private static final String LEGACY_GAME_MESSAGES_FILE = "GameMessages.json";

    private static final int WRITE_LOCK_EXPIRE_AFTER_ACCESS_MINUTES = 20;
    private static final Cache<String, ReentrantLock> gameWriteLocks = Caffeine.newBuilder()
            .expireAfterAccess(WRITE_LOCK_EXPIRE_AFTER_ACCESS_MINUTES, TimeUnit.MINUTES)
            .build();

    public static void add(String gameName, GameMessage gameMessage) {
        write(gameName, "add", service -> service.add(gameName, gameMessage));
    }

    @Nullable
    public static String replace(String gameName, GameMessage gameMessage) {
        return write(gameName, "replace", service -> service.replace(gameName, gameMessage), Optional.<String>empty())
                .orElse(null);
    }

    public static void remove(Collection<String> gameNames) {
        run("remove games from", service -> service.removeGames(gameNames));
    }

    public static void removeAfter(String gameName, long gameSaveTime) {
        write(gameName, "roll back", service -> service.removeSavedAfter(gameName, gameSaveTime));
    }

    public static Optional<String> remove(String gameName, GameMessageType type) {
        return remove(gameName, type, null);
    }

    public static Optional<String> remove(String gameName, GameMessageType type, @Nullable String key) {
        return write(gameName, "remove", service -> service.remove(gameName, type, key), Optional.empty());
    }

    public static void remove(String gameName, String messageId) {
        write(gameName, "remove", service -> service.remove(gameName, messageId));
    }

    public static Optional<GameMessage> getOne(String gameName, GameMessageType type) {
        return getOne(gameName, type, null);
    }

    public static Optional<GameMessage> getOne(String gameName, GameMessageType type, @Nullable String key) {
        return call("read", service -> service.getOne(gameName, type, key), Optional.empty());
    }

    public static Optional<GameMessage> getOne(String gameName, String messageId) {
        return call("read", service -> service.getOne(gameName, messageId), Optional.empty());
    }

    public static Map<String, List<GameMessage>> getAllByGame(GameMessageType type) {
        return call("read", service -> service.getAllByGame(type), Collections.emptyMap());
    }

    public static void cleanupStaleEntries() {
        run("clean up", GameMessageService::cleanupStaleEntries);
    }

    public static List<GameMessage> getAll(String gameName, GameMessageType type) {
        return call("read", service -> service.getAll(gameName, type), Collections.emptyList());
    }

    public static void addReaction(String gameName, String faction, GameMessageType type) {
        addReaction(gameName, faction, type, null);
    }

    public static void addReaction(String gameName, String faction, GameMessageType type, String key) {
        write(gameName, "add a reaction to", service -> service.addReaction(gameName, faction, type, key));
    }

    public static void addReaction(String gameName, String faction, String messageId) {
        write(gameName, "add a reaction to", service -> service.addReaction(gameName, faction, messageId));
    }

    // TODO: Remove this one-time GameMessages.json import (run via /developer custom_command on 2026-10-04) and
    // GAME_MESSAGES_IMPORTED_TO_DATABASE once every environment has run it; then delete pm_json/GameMessages.json.
    @Deprecated(forRemoval = true, since = "2026-10")
    public static String importLegacyFile() {
        if (ImplementedSettings.GAME_MESSAGES_IMPORTED_TO_DATABASE.getAsBoolean(false)) {
            return LEGACY_GAME_MESSAGES_FILE + " was already imported into the database. Nothing to do.";
        }
        try {
            LegacyGameMessages legacy =
                    PersistenceManager.readObjectFromJsonFile(LEGACY_GAME_MESSAGES_FILE, LegacyGameMessages.class);
            int imported = legacy == null || legacy.gameNameToMessages() == null
                    ? 0
                    : SpringContext.getBean(GameMessageService.class).importMissing(legacy.gameNameToMessages());
            GlobalSettings.setSetting(ImplementedSettings.GAME_MESSAGES_IMPORTED_TO_DATABASE, true);
            String result = "Imported " + StringHelper.pluralize(imported, "game message") + " from "
                    + LEGACY_GAME_MESSAGES_FILE + " into the database.";
            BotLogger.info(result);
            return result;
        } catch (Exception e) {
            BotLogger.error("Failed to import " + LEGACY_GAME_MESSAGES_FILE + " into the database.", e);
            return "Failed to import " + LEGACY_GAME_MESSAGES_FILE + " into the database: " + e.getMessage()
                    + ". See the bot log; it is safe to run again.";
        }
    }

    private static void write(String gameName, String action, Consumer<GameMessageService> operation) {
        write(
                gameName,
                action,
                service -> {
                    operation.accept(service);
                    return null;
                },
                null);
    }

    private static <T> T write(String gameName, String action, Function<GameMessageService, T> operation, T fallback) {
        if (gameName == null) {
            return call(action, operation, fallback);
        }
        ReentrantLock lock = gameWriteLocks.get(gameName, _ -> new ReentrantLock());
        lock.lock();
        try {
            return call(action, operation, fallback);
        } finally {
            lock.unlock();
        }
    }

    private static void run(String action, Consumer<GameMessageService> operation) {
        write(null, action, operation);
    }

    private static <T> T call(String action, Function<GameMessageService, T> operation, T fallback) {
        try {
            return operation.apply(SpringContext.getBean(GameMessageService.class));
        } catch (Exception e) {
            BotLogger.error("Failed to " + action + " game messages.", e);
            return fallback;
        }
    }

    @Deprecated(forRemoval = true, since = "2026-10")
    private record LegacyGameMessages(Map<String, List<GameMessage>> gameNameToMessages) {}
}
