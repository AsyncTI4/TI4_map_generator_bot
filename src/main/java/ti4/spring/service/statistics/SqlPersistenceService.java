package ti4.spring.service.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.map.Game;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.map.persistence.ManagedPlayer;
import ti4.message.logging.BotLogger;

@Service
@RequiredArgsConstructor
public class SqlPersistenceService {

    private final GameEntityRepository gameEntityRepository;
    private final UserEntityRepository userEntityRepository;

    @Transactional
    public void persistAll() {
        persistAllUsers();
        persistAllGames();
    }

    private void persistAllUsers() {
        userEntityRepository.deleteAll();

        int persistedRows = 0;
        for (ManagedPlayer managedPlayer : GameManager.getManagedPlayers()) {
            try {
                UserEntity row = new UserEntity(managedPlayer.getId(), managedPlayer.getName());
                userEntityRepository.save(row);
                persistedRows++;
            } catch (Exception e) {
                BotLogger.error(String.format("Failed to persist user: `%s`", managedPlayer.getId()), e);
            }
        }

        BotLogger.info(String.format("Persisted %,d user rows to SQLite.", persistedRows));
    }

    private void persistAllGames() {
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
