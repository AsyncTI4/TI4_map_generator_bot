package ti4.spring.service.gameevent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

    public static GameEventService getBean() {
        return SpringContext.getBean(GameEventService.class);
    }

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
            if (DatabasePersistenceGate.isDisabled() || !SpringContext.isInitialized()) return;
            getBean().commitEvent(game, archetype, player, payload, movementState);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Failed to commit game event.", e);
        }
    }

    @Transactional
    void commitEvent(
            Game game,
            String archetype,
            @Nullable Player player,
            Map<String, Object> payload,
            @Nullable String movementState) {
        long counter = game.getEventSequenceCounter();
        // Undo restores an older counter; rows above it are future events and must not survive the next append.
        gameEventRepository.deleteByGameNameAndSeqGreaterThan(game.getName(), counter);
        long seq = counter + 1;
        String faction = player == null ? null : player.getFaction();
        String serializedPayload = JsonMapperManager.basic().writeValueAsString(payload);
        String serializedMapState = CompactMapState.serialize(game);
        String previousMapState =
                firstOrNull(gameEventRepository.findLatestMapStates(game.getName(), counter, PageRequest.of(0, 1)));
        if (serializedMapState.equals(previousMapState)) {
            serializedMapState = null;
        }
        CompactPlayerState.Snapshot playerState = CompactPlayerState.snapshot(game);
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
                serializedMapState,
                movementState,
                playerState.delta());
        gameEventRepository.save(record);
        game.setEventSequenceCounter(seq);
        game.setLastPlayerStateJson(playerState.full());
    }

    private static String firstOrNull(List<String> values) {
        return values.isEmpty() ? null : values.getFirst();
    }

    public List<GameEventDto> getEvents(Game game) {
        if (DatabasePersistenceGate.isDisabled()) return List.of();
        return getEventsForGame(game).stream().map(GameEventService::toDto).toList();
    }

    public List<GameEventDto> getEventsSince(Game game, long sinceEpochMillis) {
        if (DatabasePersistenceGate.isDisabled()) return List.of();
        return gameEventRepository
                .findByGameNameAndSeqLessThanEqualAndTimestampEpochMillisGreaterThanOrderBySeqAsc(
                        game.getName(), game.getEventSequenceCounter(), sinceEpochMillis)
                .stream()
                .map(GameEventService::toDto)
                .toList();
    }

    private static GameEventDto toDto(GameEventEntity event) {
        return new GameEventDto(
                event.getSeq(),
                event.getArchetype(),
                event.getRound(),
                event.getPhase(),
                event.getFaction(),
                event.getTimestampEpochMillis(),
                JsonMapperManager.basic().readTree(event.getPayload()),
                event.getMapState(),
                event.getMovementState(),
                event.getPlayerState());
    }

    List<GameEventEntity> getEventsForGame(Game game) {
        return gameEventRepository.findByGameNameAndSeqLessThanEqualOrderBySeqAsc(
                game.getName(), game.getEventSequenceCounter());
    }

    public String formatEventsAsText(Game game, boolean includeMapState) {
        StringBuilder text = new StringBuilder();
        for (GameEventEntity event : getEventsForGame(game)) {
            text.append("#")
                    .append(event.getSeq())
                    .append(" | ")
                    .append(Instant.ofEpochMilli(event.getTimestampEpochMillis()))
                    .append(" | round ")
                    .append(event.getRound())
                    .append(" | ")
                    .append(event.getPhase())
                    .append(" | ")
                    .append(event.getFaction() == null ? "-" : event.getFaction())
                    .append(" | ")
                    .append(event.getArchetype())
                    .append("\n  payload: ")
                    .append(event.getPayload());
            if (event.getMovementState() != null) {
                text.append("\n  movementState: ").append(event.getMovementState());
            }
            if (includeMapState && event.getMapState() != null) {
                text.append("\n  mapState: ").append(event.getMapState());
            }
            if (includeMapState && event.getPlayerState() != null) {
                text.append("\n  playerState: ").append(event.getPlayerState());
            }
            text.append("\n");
        }
        return text.toString();
    }
}
