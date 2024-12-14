package ti4.model.metadata;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.experimental.UtilityClass;
import ti4.json.PersistenceManager;
import ti4.map.Game;
import ti4.message.BotLogger;

@UtilityClass
public class MessageReactionsMetadataManager {

    private static final String MESSAGE_REACTIONS_FILE = "MessageReactions.json";

    public static synchronized void addReaction(Game game, String messageId, String userId) {
        AllMessageReactions allMessageReactions = readFile();
        if (allMessageReactions == null) {
            return;
        }

        GameMessageReactions gameMessageReactions = allMessageReactions.gameNameToGameMessageReactions
            .computeIfAbsent(game.getName(), k -> new GameMessageReactions(new HashMap<>()));

        MessageReaction messageReaction = gameMessageReactions.messageIdToMessageReaction
            .computeIfAbsent(messageId, k -> new MessageReaction(null, new HashSet<>()));

        messageReaction.reactorUserIds.add(userId);

        persistFile(allMessageReactions);
    }

    public static synchronized void remove(String gameName, List<String> messageIds) {
        AllMessageReactions allMessageReactions = readFile();
        if (allMessageReactions == null) {
            return;
        }

        GameMessageReactions gameMessageReactions = allMessageReactions.gameNameToGameMessageReactions.get(gameName);
        if (gameMessageReactions != null) {
            messageIds.forEach(gameMessageReactions.messageIdToMessageReaction::remove);
        }

        persistFile(allMessageReactions);
    }

    public static synchronized void remove(String gameName) {
        AllMessageReactions allMessageReactions = readFile();
        if (allMessageReactions == null) {
            return;
        }

        allMessageReactions.gameNameToGameMessageReactions.remove(gameName);

        persistFile(allMessageReactions);
    }

    public static boolean hasReacted(String gameName, String messageId, List<String> userIdsToCheck) {
        AllMessageReactions messageReactions = readFile();
        if (messageReactions == null) {
            return false;
        }

        GameMessageReactions gameMessageReactions = messageReactions.gameNameToGameMessageReactions.get(gameName);
        if (gameMessageReactions == null) {
            return false;
        }

        MessageReaction messageReaction = gameMessageReactions.messageIdToMessageReaction.get(messageId);
        if (messageReaction == null) {
            return false;
        }

        return messageReaction.reactorUserIds.containsAll(userIdsToCheck);
    }

    public static String getMessageOwnerUserId(String gameName, String messageId) {
        AllMessageReactions messageReactions = readFile();
        if (messageReactions == null) {
            return null;
        }

        GameMessageReactions gameMessageReactions = messageReactions.gameNameToGameMessageReactions.get(gameName);
        if (gameMessageReactions == null) {
            return null;
        }

        MessageReaction messageReaction = gameMessageReactions.messageIdToMessageReaction.get(messageId);
        if (messageReaction == null) {
            return null;
        }

        return messageReaction.messageOwnerUserId;
    }

    private static AllMessageReactions readFile() {
        try {
            var allMessageReactions = PersistenceManager.readObjectFromJsonFile(MESSAGE_REACTIONS_FILE, AllMessageReactions.class);
            return allMessageReactions != null ? allMessageReactions : new AllMessageReactions(new HashMap<>());
        } catch (IOException e) {
            BotLogger.log("Failed to read json data for MessageReactions.", e);
            return null;
        }
    }

    private static void persistFile(AllMessageReactions toPersist) {
        try {
            PersistenceManager.writeObjectToJsonFile(MESSAGE_REACTIONS_FILE, toPersist);
        } catch (Exception e) {
            BotLogger.log("Failed to write json data for MessageReactions.", e);
        }
    }

    private record AllMessageReactions(Map<String, GameMessageReactions> gameNameToGameMessageReactions) {}

    private record GameMessageReactions(Map<String, MessageReaction> messageIdToMessageReaction) {}

    private record MessageReaction(String messageOwnerUserId, Set<String> reactorUserIds) {}
}
