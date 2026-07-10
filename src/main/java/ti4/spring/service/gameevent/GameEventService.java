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
        try {
            if (DatabasePersistenceGate.isDisabled()) return;
            SpringContext.getBean(GameEventService.class).commitEvent(game, archetype, player, payload);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Failed to commit game event.", e);
        }
    }

    @Transactional
    void commitEvent(Game game, String archetype, @Nullable Player player, Map<String, Object> payload) {
        long counter = game.getEventSequenceCounter();
        // Undo restores an older counter; rows above it are future events and must not survive the next append.
        gameEventRepository.deleteByGameNameAndSeqGreaterThan(game.getName(), counter);
        long seq = counter + 1;
        String faction = player == null ? null : player.getFaction();
        String serializedPayload = JsonMapperManager.basic().writeValueAsString(payload);
        String serializedMapState = CompactMapState.serialize(game);
        String previousMapState = gameEventRepository
                .findFirstByGameNameAndSeqLessThanEqualAndMapStateIsNotNullOrderBySeqDesc(game.getName(), counter)
                .map(GameEventEntity::getMapState)
                .orElse(null);
        if (serializedMapState.equals(previousMapState)) {
            serializedMapState = null;
        }
        var record = new GameEventEntity(
                null,
                game.getName(),
                seq,
                archetype,
                game.getRound(),
                game.getPhaseOfGame(),
                faction,
                System.currentTimeMillis(),
                serializedPayload,
                serializedMapState);
        gameEventRepository.save(record);
        game.setEventSequenceCounter(seq);
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
                        event.getMapState()))
                .toList();
    }

    List<GameEventEntity> getEventsForGame(Game game) {
        return gameEventRepository.findByGameNameAndSeqLessThanEqualOrderBySeqAsc(
                game.getName(), game.getEventSequenceCounter());
    }
}
