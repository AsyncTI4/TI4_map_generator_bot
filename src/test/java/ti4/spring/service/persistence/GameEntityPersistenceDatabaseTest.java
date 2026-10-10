package ti4.spring.service.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameFileStamp;
import ti4.game.persistence.ManagedGameState;
import ti4.testUtils.BaseTi4Test;

// Runs against a real Postgres, e.g. a throwaway database in a local container:
//   TI4_TEST_POSTGRES_URL=jdbc:postgresql://localhost:5432/tibot_test
// Skipped when the variable is unset (CI has no database).
@EnabledIfEnvironmentVariable(named = "TI4_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(
        classes = GameEntityPersistenceDatabaseTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class GameEntityPersistenceDatabaseTest extends BaseTi4Test {

    private static final List<String> COLORS = List.of("red", "blue", "green", "yellow", "purple", "black");

    @Autowired
    private GameEntityPersistenceService persistenceService;

    @Autowired
    private PersistedManagedGameService persistedManagedGameService;

    @Autowired
    private PersistedGameStateService persistedGameStateService;

    @Autowired
    private GameEntityRepository gameRepository;

    @Autowired
    private PlayerEntityRepository playerRepository;

    @Autowired
    private TitleEntityRepository titleRepository;

    @Autowired
    private UserEntityRepository userRepository;

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TI4_TEST_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> envOrDefault("TI4_TEST_POSTGRES_USER", "tibot"));
        registry.add("spring.datasource.password", () -> envOrDefault("TI4_TEST_POSTGRES_PASSWORD", ""));
    }

    @BeforeEach
    void clearTables() {
        titleRepository.deleteAll();
        gameRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void savedGameLoadsBackAsTheSameManagedGameState() {
        Game game = gameWithRealPlayers("pbd-db-round-trip", 3);
        game.addPlayer("spectator-id", "Spectator");
        game.setFowMode(true);
        game.setRound(5);
        game.setActivePlayerID("user-1");

        persistenceService.replace(GameEntityMapper.toSnapshot(game, new GameFileStamp(1234, 56)));

        PersistedManagedGame persisted = persistedManagedGameService.loadAll().get(game.getName());
        assertThat(persisted.state()).isEqualTo(ManagedGameState.of(game));
        assertThat(persisted.gameFileStamp().matches(new GameFileStamp(1234, 56)))
                .isTrue();
    }

    @Test
    void resavingAGameReplacesItsParticipants() {
        Game game = gameWithRealPlayers("pbd-db-resave", 3);
        game.addPlayer("spectator-id", "Spectator");
        persistenceService.replace(GameEntityMapper.toSnapshot(game, new GameFileStamp(1, 1)));

        game.removePlayer("spectator-id");
        game.addPlayer("newcomer-id", "Newcomer");
        persistenceService.replace(GameEntityMapper.toSnapshot(game, new GameFileStamp(2, 1)));

        assertThat(persistedManagedGameService
                        .loadAll()
                        .get(game.getName())
                        .state()
                        .participants())
                .extracting(ManagedGameState.Participant::userId)
                .containsExactly("newcomer-id", "user-1", "user-2", "user-3");
    }

    @Test
    void statisticsIgnoredGameIsStoredWithoutPlayerRows() {
        Game game = gameWithRealPlayers("pbd-db-ignored", 2);

        persistenceService.replace(GameEntityMapper.toSnapshot(game, new GameFileStamp(1, 1)));

        assertThat(gameRepository.findById(game.getName()))
                .get()
                .extracting(GameEntity::isStatisticsIgnored)
                .isEqualTo(true);
        assertThat(playerRepository.findAllWithUsers()).isEmpty();
        assertThat(persistedManagedGameService.loadAll().get(game.getName()).state())
                .isEqualTo(ManagedGameState.of(game));
    }

    @Test
    void ongoingTiglQueryLeavesOutStatisticsIgnoredGames() {
        gameRepository.save(tiglGame("pbd-db-tigl-counted", false));
        gameRepository.save(tiglGame("pbd-db-tigl-ignored", true));

        assertThat(
                        gameRepository
                                .findByTwilightImperiumGlobalLeagueTrueAndStatisticsIgnoredFalseAndEndedEpochMillisecondsIsNull())
                .extracting(GameEntity::getGameName)
                .containsExactly("pbd-db-tigl-counted");
    }

    @Test
    void freshlySavedGameHasNoReconciliationDifferences() {
        Game game = gameWithRealPlayers("pbd-db-reconcile", 4);
        game.addPlayer("spectator-id", "Spectator");
        game.setStoredValue("TitlesForuser-1", "Kingmaker");
        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game, new GameFileStamp(99, 1));

        persistenceService.replace(snapshot);

        PersistedGameState persisted = persistedGameStateService.loadAll().get(game.getName());
        assertThat(PersistedGameState.of(snapshot).describeDifferencesFrom(persisted))
                .isEmpty();
    }

    @Test
    void previousReleaseCanStillDeleteGamesThatHaveParticipants() {
        Game game = gameWithRealPlayers("pbd-db-rollback", 3);
        game.setStoredValue("TitlesForuser-1", "Kingmaker");
        persistenceService.replace(GameEntityMapper.toSnapshot(game, new GameFileStamp(1, 1)));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // The release before game_participant existed deletes title, player and game rows only, so the participant
        // foreign key must cascade or every one of its syncs would fail after a rollback.
        jdbc.update("DELETE FROM title WHERE game_name = ?", game.getName());
        jdbc.update("DELETE FROM player WHERE game_name = ?", game.getName());
        jdbc.update("DELETE FROM game WHERE game_name = ?", game.getName());

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM game_participant WHERE game_name = ?", Integer.class, game.getName()))
                .isZero();
    }

    private static Game gameWithRealPlayers(String gameName, int playerCount) {
        Game game = new Game();
        game.setName(gameName);
        for (int i = 1; i <= playerCount; i++) {
            Player player = game.addPlayer("user-" + i, "User " + i);
            player.setFaction(List.of("sol", "hacan", "xxcha", "jolnar", "muaat", "arborec")
                    .get(i - 1));
            player.setColor(COLORS.get(i - 1));
        }
        return game;
    }

    private static GameEntity tiglGame(String gameName, boolean statisticsIgnored) {
        GameEntity game = new GameEntity();
        game.setGameName(gameName);
        game.setTwilightImperiumGlobalLeague(true);
        game.setStatisticsIgnored(statisticsIgnored);
        return game;
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }

    @Configuration
    @ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        TransactionAutoConfiguration.class
    })
    @EntityScan(basePackageClasses = GameEntity.class)
    @EnableJpaRepositories(basePackageClasses = GameEntityRepository.class)
    @Import({GameEntityPersistenceService.class, PersistedManagedGameService.class, PersistedGameStateService.class})
    static class TestConfig {}
}
