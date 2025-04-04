package ti4.message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import lombok.experimental.UtilityClass;
import ti4.json.PersistenceManager;

@UtilityClass
public class GameMessageManager {

    private static final String GAME_MESSAGES_FILE = "GameMessages.json";

    public static synchronized void add(String gameName, String messageId, GameMessageType type, long gameSaveTime) {
        GameMessages allGameMessages = readFile();
        if (allGameMessages == null) {
            allGameMessages = new GameMessages(new HashMap<>());
        }

        List<GameMessage> messages = allGameMessages.gameNameToMessages.computeIfAbsent(gameName, k -> new ArrayList<>());
        if (messages.stream().anyMatch(message -> message.messageId.equals(messageId))) {
            return;
        }

        messages.add(new GameMessage(messageId, type, new LinkedHashSet<>(), gameSaveTime));

        persistFile(allGameMessages);
    }

    public static synchronized String replace(String gameName, String messageId, GameMessageType type, long gameSaveTime) {
        GameMessages allGameMessages = readFile();
        if (allGameMessages == null) {
            allGameMessages = new GameMessages(new HashMap<>());
        }

        List<GameMessage> messages = allGameMessages.gameNameToMessages.computeIfAbsent(gameName, k -> new ArrayList<>());

        String replacedMessageId = null;
        if (!messages.isEmpty()) {
            for (int i = 0; i < messages.size(); i++) {
                GameMessage message = messages.get(i);
                if (message.type == type) {
                    replacedMessageId = messages.remove(i).messageId();
                }
            }
        }

        messages.add(new GameMessage(messageId, type, new LinkedHashSet<>(), gameSaveTime));

        persistFile(allGameMessages);

        return replacedMessageId;
    }

    public static synchronized void remove(List<String> gameNames) {
        GameMessages allGameMessages = readFile();
        if (allGameMessages == null) {
            return;
        }

        gameNames.forEach(allGameMessages.gameNameToMessages::remove);

        persistFile(allGameMessages);
    }

    public static synchronized void removeAfter(String gameName, long gameSaveTime) {
        GameMessages allGameMessages = readFile();
        if (allGameMessages == null) {
            return;
        }

        List<GameMessage> messages = allGameMessages.gameNameToMessages.get(gameName);
        if (messages == null) {
            return;
        }

        messages.removeIf(message -> message.gameSaveTime > gameSaveTime);

        persistFile(allGameMessages);
    }

    public static synchronized Optional<String> remove(String gameName, GameMessageType type) {
        GameMessages allGameMessages = readFile();
        if (allGameMessages == null) {
            return Optional.empty();
        }

        List<GameMessage> messages = allGameMessages.gameNameToMessages.get(gameName);
        if (messages == null) {
            return Optional.empty();
        }

        GameMessage message = messages.stream()
            .filter(m -> m.type == type)
            .findFirst()
            .orElse(null);
        if (message == null) {
            return Optional.empty();
        }

        messages.remove(message);

        persistFile(allGameMessages);

        return Optional.of(message.messageId);
    }

    public static synchronized void remove(String gameName, String messageId) {
        GameMessages allGameMessages = readFile();
        if (allGameMessages == null) {
            return;
        }

        List<GameMessage> messages = allGameMessages.gameNameToMessages.get(gameName);
        if (messages == null) {
            return;
        }

        messages.removeIf(message -> message.messageId.equals(messageId));

        persistFile(allGameMessages);
    }

    public static synchronized Optional<GameMessage> getOne(String gameName, GameMessageType type) {
        return getOne(gameName, message -> message.type == type);
    }

    public static synchronized Optional<GameMessage> getOne(String gameName, String messageId) {
        return getOne(gameName, message -> message.messageId.equals(messageId));
    }

    private static synchronized Optional<GameMessage> getOne(String gameName, Predicate<GameMessage> filter) {
        GameMessages allGameMessages = readFile();
        if (allGameMessages == null) {
            return Optional.empty();
        }

        List<GameMessage> messages = allGameMessages.gameNameToMessages.computeIfAbsent(gameName, k -> new ArrayList<>());
        return messages.stream()
            .filter(filter)
            .findFirst();
    }

    public static synchronized List<GameMessage> getAll(String gameName, GameMessageType type) {
        GameMessages allGameMessages = readFile();
        if (allGameMessages == null) {
            return Collections.emptyList();
        }

        List<GameMessage> messages = allGameMessages.gameNameToMessages.computeIfAbsent(gameName, k -> new ArrayList<>());
        return messages.stream()
            .filter(m -> m.type == type)
            .toList();
    }

    public static synchronized void addReaction(String gameName, String faction, GameMessageType type) {
        addReaction(gameName, faction, message -> message.type == type);
    }

    public static synchronized void addReaction(String gameName, String faction, String messageId) {
        addReaction(gameName, faction, message -> message.messageId.equals(messageId));
    }

    private static void addReaction(String gameName, String faction, Predicate<GameMessage> filter) {
        GameMessages allGameMessages = readFile();
        if (allGameMessages == null) {
            return;
        }

        List<GameMessage> messages = allGameMessages.gameNameToMessages.get(gameName);
        if (messages == null) {
            return;
        }

        messages.stream()
            .filter(filter)
            .findFirst()
            .ifPresent(message -> {
                message.factionsThatReacted.add(faction);
                persistFile(allGameMessages);
            });
    }

    private static GameMessages readFile() {
        try {
            GameMessages gameMessages = PersistenceManager.readObjectFromJsonFile(GAME_MESSAGES_FILE, GameMessages.class);
            return gameMessages != null ? gameMessages : new GameMessages(new HashMap<>());
        } catch (IOException e) {
            BotLogger.error("Failed to read json data for GameMessages.", e);
            return null;
        }
    }

    private static void persistFile(GameMessages toPersist) {
        try {
            PersistenceManager.writeObjectToJsonFile(GAME_MESSAGES_FILE, toPersist);
        } catch (Exception e) {
            BotLogger.error("Failed to write json data for GameMessages.", e);
        }
    }

    private record GameMessages(Map<String, List<GameMessage>> gameNameToMessages) {}

    public record GameMessage(String messageId, GameMessageType type, LinkedHashSet<String> factionsThatReacted, long gameSaveTime) {}
}
