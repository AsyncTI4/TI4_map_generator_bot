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
public class PersistedGameStateService {

    private final GameEntityRepository gameEntityRepository;
    private final PlayerEntityRepository playerEntityRepository;
    private final GameParticipantEntityRepository gameParticipantEntityRepository;
    private final TitleEntityRepository titleEntityRepository;

    @Transactional(readOnly = true)
    public Map<String, PersistedGameState> loadAll() {
        Map<String, List<PlayerEntity>> playersByGame = playerEntityRepository.findAllWithUsersAndGames().stream()
                .collect(Collectors.groupingBy(player -> player.getGame().getGameName()));
        Map<String, List<GameParticipantEntity>> participantsByGame =
                gameParticipantEntityRepository.findAllWithGames().stream()
                        .collect(Collectors.groupingBy(
                                participant -> participant.getGame().getGameName()));
        Map<String, List<TitleEntity>> titlesByGame = titleEntityRepository.findAllWithUsersAndGames().stream()
                .collect(Collectors.groupingBy(title -> title.getGame().getGameName()));

        Map<String, PersistedGameState> states = new HashMap<>();
        for (GameEntity game : gameEntityRepository.findAll()) {
            String gameName = game.getGameName();
            states.put(
                    gameName,
                    PersistedGameState.of(
                            game,
                            playersByGame.getOrDefault(gameName, List.of()),
                            participantsByGame.getOrDefault(gameName, List.of()),
                            titlesByGame.getOrDefault(gameName, List.of())));
        }
        return states;
    }

    public static PersistedGameStateService getBean() {
        return SpringContext.getBean(PersistedGameStateService.class);
    }
}
