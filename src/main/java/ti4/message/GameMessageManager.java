package ti4.message;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.logging.BotLogger;
import ti4.spring.service.gamemessage.GameMessageService;

@UtilityClass
public class GameMessageManager {

    public static void add(String gameName, GameMessage gameMessage) {
        run("add", service -> service.add(gameName, gameMessage));
    }

    @Nullable
    public static String replace(String gameName, GameMessage gameMessage) {
        return call("replace", service -> service.replace(gameName, gameMessage), Optional.<String>empty())
                .orElse(null);
    }

    public static void remove(Collection<String> gameNames) {
        run("remove games from", service -> service.removeGames(gameNames));
    }

    public static void removeAfter(String gameName, long gameSaveTime) {
        run("roll back", service -> service.removeSavedAfter(gameName, gameSaveTime));
    }

    public static Optional<String> remove(String gameName, GameMessageType type) {
        return remove(gameName, type, null);
    }

    public static Optional<String> remove(String gameName, GameMessageType type, @Nullable String key) {
        return call("remove", service -> service.remove(gameName, type, key), Optional.empty());
    }

    public static void remove(String gameName, String messageId) {
        run("remove", service -> service.remove(gameName, messageId));
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
        run("add a reaction to", service -> service.addReaction(gameName, faction, type, key));
    }

    public static void addReaction(String gameName, String faction, String messageId) {
        run("add a reaction to", service -> service.addReaction(gameName, faction, messageId));
    }

    private static void run(String action, Consumer<GameMessageService> operation) {
        call(
                action,
                service -> {
                    operation.accept(service);
                    return null;
                },
                null);
    }

    private static <T> T call(String action, Function<GameMessageService, T> operation, T fallback) {
        try {
            return operation.apply(GameMessageService.getBean());
        } catch (Exception e) {
            BotLogger.error("Failed to " + action + " game messages.", e);
            return fallback;
        }
    }
}
