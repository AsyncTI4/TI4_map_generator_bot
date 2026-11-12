package ti4.spring.service.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ti4.game.persistence.ManagedGameState;
import ti4.spring.context.SpringContext;

@Service
@RequiredArgsConstructor
public class PersistedManagedGameService {

    private static final int WARMUP_READ_TIMEOUT_SECONDS = 120;

    private final GameEntityRepository gameEntityRepository;
    private final PlayerEntityRepository playerEntityRepository;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = WARMUP_READ_TIMEOUT_SECONDS)
    public Map<String, ManagedGameState> loadAll() {
        return toManagedGameStates(
                gameEntityRepository.findAll(), playerEntityRepository.findAllParticipantsByGameName());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = WARMUP_READ_TIMEOUT_SECONDS)
    public Map<String, ManagedGameState> loadAll(Collection<String> gameNames) {
        if (gameNames.isEmpty()) return Map.of();
        return toManagedGameStates(
                gameEntityRepository.findAllById(gameNames),
                playerEntityRepository.findParticipantsByGameName(gameNames));
    }

    private static Map<String, ManagedGameState> toManagedGameStates(
            List<GameEntity> games, Map<String, List<ManagedGameState.Participant>> participantsByGame) {
        Map<String, ManagedGameState> persistedGames = new HashMap<>();
        for (GameEntity game : games) {
            if (!Objects.equals(game.getMappingVersion(), GameEntityMapper.MAPPING_VERSION)) continue;
            String gameName = game.getGameName();
            persistedGames.put(
                    gameName,
                    GameEntityMapper.toManagedGameState(game, participantsByGame.getOrDefault(gameName, List.of())));
        }
        return persistedGames;
    }

    public Map<String, String> loadRevisionKeys() {
        Map<String, String> revisionKeys = new HashMap<>();
        for (GameRevisionRow row : gameEntityRepository.findRevisionRows(GameEntityMapper.MAPPING_VERSION)) {
            revisionKeys.put(row.gameName(), row.revisionKey());
        }
        return revisionKeys;
    }

    public static PersistedManagedGameService getBean() {
        return SpringContext.getBean(PersistedManagedGameService.class);
    }
}
