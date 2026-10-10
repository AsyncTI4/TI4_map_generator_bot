package ti4.spring.service.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.spring.context.SpringContext;

@Service
@RequiredArgsConstructor
public class PersistedManagedGameService {

    private final GameEntityRepository gameEntityRepository;
    private final GameParticipantEntityRepository gameParticipantEntityRepository;

    @Transactional(readOnly = true)
    public Map<String, PersistedManagedGame> loadAll() {
        Map<String, List<GameParticipantEntity>> participantsByGame =
                gameParticipantEntityRepository.findAllWithGames().stream()
                        .collect(Collectors.groupingBy(
                                participant -> participant.getGame().getGameName()));

        Map<String, PersistedManagedGame> persistedGames = new HashMap<>();
        for (GameEntity game : gameEntityRepository.findAll()) {
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
