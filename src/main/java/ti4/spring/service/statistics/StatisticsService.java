package ti4.spring.service.statistics;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.logging.BotLogger;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final GameEntityRepository gameEntityRepository;

    @Transactional
    public void persistAllGames() {
        gameEntityRepository.deleteAll();

        int persistedRows = 0;
        for (ManagedGame managedGame : GameManager.getManagedGames()) {
            try {
                Game game = managedGame.getGame();
                GameEntity row = new GameEntity(game);
                gameEntityRepository.save(row);
                persistedRows++;
            } catch (Exception e) {
                BotLogger.error(String.format("Failed to persist game: `%s`", managedGame.getName()), e);
            }
        }

        BotLogger.info(String.format("Persisted %,d game rows to SQLite.", persistedRows));
    }
}
