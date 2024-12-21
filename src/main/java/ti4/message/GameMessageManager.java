package ti4.message;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ti4.json.PersistenceManager;

public class GameMessageManager {

    private static final String GAME_MESSAGES_FILE = "GameMessages.json";

    public static synchronized void addMessage(String gameName, String messageId, GameMessageType type, Instant sendTime) {
        GameMessages allGameMessages = readFile();
        if (allGameMessages == null) {
            allGameMessages = new GameMessages(new HashMap<>());
        }

        List<GameMessage> messages = allGameMessages.gameNameToMessages.computeIfAbsent(gameName, k -> new ArrayList<>());
        messages.add(new GameMessage(messageId, type, sendTime));

        persistFile(allGameMessages);
    }

    public static synchronized void remove(List<String> gameNames) {
        GameMessages allGameMessages = readFile();
        if (allGameMessages == null) {
            return;
        }

        gameNames.forEach(allGameMessages.gameNameToMessages::remove);

        persistFile(allGameMessages);
    }

    private static GameMessages readFile() {
        try {
            GameMessages gameMessages = PersistenceManager.readObjectFromJsonFile(GAME_MESSAGES_FILE, GameMessages.class);
            return gameMessages != null ? gameMessages : new GameMessages(new HashMap<>());
        } catch (IOException e) {
            BotLogger.log("Failed to read json data for GameMessages.", e);
            return null;
        }
    }

    private static void persistFile(GameMessages toPersist) {
        try {
            PersistenceManager.writeObjectToJsonFile(GAME_MESSAGES_FILE, toPersist);
        } catch (Exception e) {
            BotLogger.log("Failed to write json data for GameMessages.", e);
        }
    }

    private record GameMessages(Map<String, List<GameMessage>> gameNameToMessages) {}

    public record GameMessage(String messageId, GameMessageType type, Instant sendTime) {}

    public enum GameMessageType {
        GENERIC,
        AGENDA_WHEN,
        AGENDA_AFTER,
        ACTION_CARD
    }
}
