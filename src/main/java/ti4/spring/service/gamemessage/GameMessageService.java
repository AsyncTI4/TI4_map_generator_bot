package ti4.spring.service.gamemessage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.logging.BotLogger;
import ti4.message.GameMessage;
import ti4.message.GameMessageType;
import ti4.spring.context.SpringContext;

@Service
@RequiredArgsConstructor
public class GameMessageService {

    private static final long STALE_AFTER_MILLIS = TimeUnit.DAYS.toMillis(14);
    private static final int GAME_LOCK_EXPIRE_AFTER_ACCESS_MINUTES = 20;

    private final GameMessageEntityRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final Cache<String, ReentrantLock> gameLocks = Caffeine.newBuilder()
            .expireAfterAccess(GAME_LOCK_EXPIRE_AFTER_ACCESS_MINUTES, TimeUnit.MINUTES)
            .build();

    public void add(String gameName, GameMessage gameMessage) {
        writeGame(gameName, () -> {
            if (load(gameName).stream().noneMatch(sameMessageId(gameMessage.messageId()))) {
                repository.save(GameMessageEntity.from(gameName, gameMessage));
            }
        });
    }

    public Optional<String> replace(String gameName, GameMessage gameMessage) {
        return writeGameAndGet(gameName, () -> {
            Optional<GameMessageEntity> existing = load(gameName).stream()
                    .filter(sameTypeAndKey(gameMessage.type(), gameMessage.key()))
                    .findFirst();
            if (existing.isEmpty()) {
                repository.save(GameMessageEntity.from(gameName, gameMessage));
                return Optional.empty();
            }
            String replacedMessageId = existing.get().getMessageId();
            existing.get().replaceWith(gameMessage);
            return Optional.of(replacedMessageId);
        });
    }

    public void removeGames(Collection<String> gameNames) {
        gameNames.stream()
                .distinct()
                .forEach(gameName -> writeGame(gameName, () -> repository.deleteByGameName(gameName)));
    }

    public void removeSavedAfter(String gameName, long gameSaveTime) {
        writeGame(gameName, () -> repository.deleteSavedAfter(gameName, gameSaveTime));
    }

    public Optional<String> remove(String gameName, GameMessageType type, String key) {
        return writeGameAndGet(gameName, () -> {
            Optional<GameMessageEntity> message =
                    load(gameName).stream().filter(sameTypeAndKey(type, key)).findFirst();
            message.ifPresent(repository::delete);
            return message.map(GameMessageEntity::getMessageId);
        });
    }

    public void remove(String gameName, String messageId) {
        writeGame(gameName, () -> repository.deleteByGameNameAndMessageId(gameName, messageId));
    }

    public Optional<GameMessage> getOne(String gameName, GameMessageType type, String key) {
        return findFirst(gameName, sameTypeAndKey(type, key));
    }

    public Optional<GameMessage> getOne(String gameName, String messageId) {
        return findFirst(gameName, sameMessageId(messageId));
    }

    public List<GameMessage> getAll(String gameName, GameMessageType type) {
        return repository.findByGameNameAndTypeOrderByIdAsc(gameName, type).stream()
                .map(GameMessageEntity::toGameMessage)
                .toList();
    }

    public Map<String, List<GameMessage>> getAllByGame(GameMessageType type) {
        Map<String, List<GameMessage>> messagesByGame = new LinkedHashMap<>();
        for (GameMessageEntity message : repository.findByTypeOrderByIdAsc(type)) {
            messagesByGame
                    .computeIfAbsent(message.getGameName(), _ -> new ArrayList<>())
                    .add(message.toGameMessage());
        }
        return Collections.unmodifiableMap(messagesByGame);
    }

    public void addReaction(String gameName, String faction, GameMessageType type, String key) {
        addReaction(gameName, faction, sameTypeAndKey(type, key));
    }

    public void addReaction(String gameName, String faction, String messageId) {
        addReaction(gameName, faction, sameMessageId(messageId));
    }

    public void cleanupStaleEntries() {
        long staleBefore = System.currentTimeMillis() - STALE_AFTER_MILLIS;
        Map<String, List<GameMessage>> messagesByGame = repository.findAll().stream()
                .collect(Collectors.groupingBy(
                        GameMessageEntity::getGameName,
                        LinkedHashMap::new,
                        Collectors.mapping(GameMessageEntity::toGameMessage, Collectors.toList())));

        List<String> inactiveGames = new ArrayList<>();
        messagesByGame.forEach((gameName, messages) -> {
            ManagedGame game = GameManager.getManagedGame(gameName);
            if (game == null || game.isHasEnded()) {
                inactiveGames.add(gameName);
                return;
            }
            int playerCount = game.getRealPlayers().size();
            if (messages.stream().noneMatch(message -> isStale(message, playerCount, staleBefore))) {
                return;
            }
            int removed = writeGameAndGet(gameName, () -> removeStale(gameName, playerCount, staleBefore));
            if (removed > 0) {
                BotLogger.info("GameMessageCleanupCron removed GameMessages for " + gameName);
            }
        });

        if (!inactiveGames.isEmpty()) {
            removeGames(inactiveGames);
            BotLogger.info("GameMessageCleanupCron removed the following games " + inactiveGames);
        }
    }

    @Deprecated(forRemoval = true, since = "2026-10")
    public int importMissing(Map<String, List<GameMessage>> messagesByGame) {
        int imported = 0;
        for (Map.Entry<String, List<GameMessage>> game : messagesByGame.entrySet()) {
            imported += writeGameAndGet(game.getKey(), () -> importMissing(game.getKey(), game.getValue()));
        }
        return imported;
    }

    static boolean isStale(GameMessage message, int playerCount, long staleBefore) {
        boolean everyoneReacted =
                playerCount > 0 && message.factionsThatReacted().size() >= playerCount;
        return everyoneReacted || message.gameSaveTime() <= staleBefore;
    }

    private int importMissing(String gameName, List<GameMessage> messages) {
        Set<String> knownMessageIds = new HashSet<>();
        load(gameName).forEach(message -> knownMessageIds.add(message.getMessageId()));
        int imported = 0;
        for (GameMessage message : messages) {
            if (knownMessageIds.add(message.messageId())) {
                repository.save(GameMessageEntity.from(gameName, message));
                imported++;
            }
        }
        return imported;
    }

    private int removeStale(String gameName, int playerCount, long staleBefore) {
        List<GameMessageEntity> staleMessages = load(gameName).stream()
                .filter(message -> isStale(message.toGameMessage(), playerCount, staleBefore))
                .toList();
        repository.deleteAllInBatch(staleMessages);
        return staleMessages.size();
    }

    private void addReaction(String gameName, String faction, Predicate<GameMessageEntity> filter) {
        writeGame(
                gameName,
                () -> load(gameName).stream()
                        .filter(filter)
                        .findFirst()
                        .ifPresent(message -> message.addReaction(faction)));
    }

    private void writeGame(String gameName, Runnable write) {
        writeGameAndGet(gameName, () -> {
            write.run();
            return null;
        });
    }

    private <T> T writeGameAndGet(String gameName, Supplier<T> write) {
        ReentrantLock lock = gameLocks.get(gameName, _ -> new ReentrantLock());
        lock.lock();
        try {
            return transactionTemplate.execute(_ -> write.get());
        } finally {
            lock.unlock();
        }
    }

    private List<GameMessageEntity> load(String gameName) {
        return repository.findByGameNameOrderByIdAsc(gameName);
    }

    private Optional<GameMessage> findFirst(String gameName, Predicate<GameMessageEntity> filter) {
        return load(gameName).stream().filter(filter).findFirst().map(GameMessageEntity::toGameMessage);
    }

    private static Predicate<GameMessageEntity> sameMessageId(String messageId) {
        return message -> message.getMessageId().equals(messageId);
    }

    private static Predicate<GameMessageEntity> sameTypeAndKey(GameMessageType type, String key) {
        return message -> message.getType() == type && Objects.equals(message.getMessageKey(), key);
    }

    public static GameMessageService getBean() {
        return SpringContext.getBean(GameMessageService.class);
    }
}
