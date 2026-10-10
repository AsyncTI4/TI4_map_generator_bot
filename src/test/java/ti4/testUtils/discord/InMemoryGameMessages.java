package ti4.testUtils.discord;

import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.mockito.invocation.InvocationOnMock;
import ti4.message.GameMessage;
import ti4.message.GameMessageType;
import ti4.spring.service.gamemessage.GameMessageService;

/**
 * A GameMessageService backed by memory instead of the database. The game's "everyone has reacted" bookkeeping
 * (strategy card follows, scoring, when/after windows) runs through it, so in-process games need a working one.
 */
public final class InMemoryGameMessages {

    private final Map<String, List<GameMessage>> byGame = new LinkedHashMap<>();
    private final GameMessageService service = mock(GameMessageService.class, this::answer);

    public GameMessageService service() {
        return service;
    }

    public synchronized List<GameMessage> messages(String gameName) {
        return List.copyOf(byGame.getOrDefault(gameName, List.of()));
    }

    private synchronized Object answer(InvocationOnMock invocation) {
        Object[] args = invocation.getArguments();
        return switch (invocation.getMethod().getName()) {
            case "add" -> {
                GameMessage message = (GameMessage) args[1];
                List<GameMessage> messages = game((String) args[0]);
                if (messages.stream().noneMatch(sameId(message.messageId()))) messages.add(message);
                yield null;
            }
            case "replace" -> {
                GameMessage message = (GameMessage) args[1];
                List<GameMessage> messages = game((String) args[0]);
                Optional<GameMessage> existing = messages.stream()
                        .filter(sameTypeAndKey(message.type(), message.key()))
                        .findFirst();
                existing.ifPresent(messages::remove);
                messages.add(message);
                yield existing.map(GameMessage::messageId);
            }
            case "removeGames" -> {
                ((Collection<?>) args[0]).forEach(byGame::remove);
                yield null;
            }
            case "removeSavedAfter" -> {
                long time = (Long) args[1];
                game((String) args[0]).removeIf(message -> message.gameSaveTime() > time);
                yield null;
            }
            case "remove" -> {
                List<GameMessage> messages = game((String) args[0]);
                if (args.length == 2) {
                    messages.removeIf(sameId((String) args[1]));
                    yield null;
                }
                Optional<GameMessage> removed = messages.stream()
                        .filter(sameTypeAndKey((GameMessageType) args[1], (String) args[2]))
                        .findFirst();
                removed.ifPresent(messages::remove);
                yield removed.map(GameMessage::messageId);
            }
            case "getOne" ->
                args.length == 2
                        ? first((String) args[0], sameId((String) args[1]))
                        : first((String) args[0], sameTypeAndKey((GameMessageType) args[1], (String) args[2]));
            case "getAll" ->
                game((String) args[0]).stream()
                        .filter(message -> message.type() == args[1])
                        .toList();
            case "getAllByGame" -> {
                Map<String, List<GameMessage>> all = new LinkedHashMap<>();
                byGame.forEach((gameName, messages) -> all.put(
                        gameName,
                        messages.stream()
                                .filter(message -> message.type() == args[0])
                                .toList()));
                yield all;
            }
            case "addReaction" -> {
                Predicate<GameMessage> filter = args.length == 3
                        ? sameId((String) args[2])
                        : sameTypeAndKey((GameMessageType) args[2], (String) args[3]);
                first((String) args[0], filter)
                        .ifPresent(message -> message.factionsThatReacted().add((String) args[1]));
                yield null;
            }
            case "importMissing" -> 0;
            default -> null;
        };
    }

    private List<GameMessage> game(String gameName) {
        return byGame.computeIfAbsent(gameName, ignored -> new ArrayList<>());
    }

    private Optional<GameMessage> first(String gameName, Predicate<GameMessage> filter) {
        return game(gameName).stream().filter(filter).findFirst();
    }

    private static Predicate<GameMessage> sameId(String messageId) {
        return message -> message.messageId().equals(messageId);
    }

    private static Predicate<GameMessage> sameTypeAndKey(GameMessageType type, String key) {
        return message -> message.type() == type && Objects.equals(message.key(), key);
    }
}
