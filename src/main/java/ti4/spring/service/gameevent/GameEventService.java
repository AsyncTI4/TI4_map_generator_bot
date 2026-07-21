package ti4.spring.service.gameevent;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.game.Game;
import ti4.game.Player;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.spring.context.SpringContext;
import ti4.website.model.CompactMapState;

@AllArgsConstructor
@Service
public class GameEventService {

    private final GameEventRepository gameEventRepository;

    public static void commit(Game game, String archetype, @Nullable Player player, Map<String, Object> payload) {
        commit(game, archetype, player, payload, null);
    }

    public static void commit(
            Game game,
            String archetype,
            @Nullable Player player,
            Map<String, Object> payload,
            @Nullable String movementState) {
        try {
            if (DatabasePersistenceGate.isDisabled()) return;
            String serializedPayload = JsonMapperManager.basic().writeValueAsString(payload);
            String serializedMapState = CompactMapState.serialize(game);
            GameEventService service = SpringContext.getBean(GameEventService.class);
            long counter = game.getEventSequenceCounter();
            String previousMapState = service.getPreviousMapState(game.getName(), counter);
            if (serializedMapState.equals(previousMapState)) {
                serializedMapState = null;
            }
            long seq = counter + 1;
            var record = new GameEventEntity(
                    null,
                    game.getName(),
                    seq,
                    archetype,
                    game.getRound(),
                    game.getPhaseOfGame(),
                    player == null ? null : player.getFaction(),
                    System.currentTimeMillis(),
                    serializedPayload,
                    serializedMapState,
                    movementState);
            service.commitEvent(game.getName(), counter, record);
            game.setEventSequenceCounter(seq);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Failed to commit game event.", e);
        }
    }

    @Transactional
    void commitEvent(String gameName, long counter, GameEventEntity record) {
        // Undo restores an older counter; rows above it are future events and must not survive the next append.
        // This direct DELETE must remain the first SQL statement so SQLite reserves the writer before any later work.
        gameEventRepository.deleteFutureEvents(gameName, counter);
        gameEventRepository.save(record);
    }

    String getPreviousMapState(String gameName, long counter) {
        return gameEventRepository
                .findFirstByGameNameAndSeqLessThanEqualAndMapStateIsNotNullOrderBySeqDesc(gameName, counter)
                .map(GameEventEntity::getMapState)
                .orElse(null);
    }

    public List<GameEventDto> getEvents(Game game) {
        if (DatabasePersistenceGate.isDisabled()) return List.of();
        return getEventsForGame(game).stream()
                .map(event -> new GameEventDto(
                        event.getSeq(),
                        event.getArchetype(),
                        event.getRound(),
                        event.getPhase(),
                        event.getFaction(),
                        event.getTimestampEpochMillis(),
                        JsonMapperManager.basic().readTree(event.getPayload()),
                        event.getMapState(),
                        event.getMovementState()))
                .toList();
    }

    List<GameEventEntity> getEventsForGame(Game game) {
        return gameEventRepository.findByGameNameAndSeqLessThanEqualOrderBySeqAsc(
                game.getName(), game.getEventSequenceCounter());
    }
}
