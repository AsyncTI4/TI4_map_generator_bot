package ti4.spring.service.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
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
    private final GameParticipantEntityRepository gameParticipantEntityRepository;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = WARMUP_READ_TIMEOUT_SECONDS)
    public Map<String, PersistedManagedGame> loadAll() {
        Map<String, List<ManagedGameState.Participant>> participantsByGame =
                gameParticipantEntityRepository.findAllRows().stream()
                        .collect(Collectors.groupingBy(
                                GameParticipantRow::gameName,
                                Collectors.mapping(GameParticipantRow::toParticipant, Collectors.toList())));

        Map<String, PersistedManagedGame> persistedGames = new HashMap<>();
        for (GameEntity game : gameEntityRepository.findAll()) {
            if (!Objects.equals(game.getMappingVersion(), GameEntityMapper.MAPPING_VERSION)) continue;
            String gameName = game.getGameName();
            persistedGames.put(
                    gameName,
                    GameEntityMapper.toPersistedManagedGame(
                            game, participantsByGame.getOrDefault(gameName, List.of())));
        }
        return persistedGames;
    }

    public static PersistedManagedGameService getBean() {
        return SpringContext.getBean(PersistedManagedGameService.class);
    }
}
