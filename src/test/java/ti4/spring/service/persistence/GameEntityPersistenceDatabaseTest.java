package ti4.spring.service.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
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
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ManagedGameState;
import ti4.test.PostgresDatabaseTest;
import ti4.testUtils.BaseTi4Test;

// Runs against a real Postgres, e.g. a throwaway database in a local container:
//   TI4_TEST_POSTGRES_URL=jdbc:postgresql://localhost:55432/tibot_ci_test
// Skipped when the variable is unset. CI provides a dedicated test database.
@EnabledIfEnvironmentVariable(named = "TI4_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(
        classes = GameEntityPersistenceDatabaseTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class GameEntityPersistenceDatabaseTest extends PostgresDatabaseTest {

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

    // Game construction needs the static game data that BaseTi4Test loads, but the datasource must come from the
    // PostgresDatabaseTest guard, so the shared setup is borrowed instead of inherited.
    @BeforeAll
    static void loadGameData() throws InterruptedException {
        BaseTi4Test.beforeAll();
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

        persistenceService.replace(GameEntityMapper.toSnapshot(game));

        ManagedGameState persisted = persistedManagedGameService.loadAll().get(game.getName());
        assertThat(persisted).isEqualTo(ManagedGameState.of(game));
    }

    @Test
    @SuppressWarnings("removal")
    void currentRevisionKeysLeaveOutRowsFromAnOlderMapping() {
        Game current = gameWithRealPlayers("pbd-db-revision-current", 3);
        current.setRevisionKey("current-revision");
        persistenceService.replace(GameEntityMapper.toSnapshot(current));
        Game older = gameWithRealPlayers("pbd-db-revision-older", 3);
        older.setRevisionKey("older-revision");
        persistenceService.replace(GameEntityMapper.toSnapshot(older));
        persistenceService.updateRevision(older.getName(), "older-revision", GameEntityMapper.MAPPING_VERSION - 1);

        assertThat(persistedManagedGameService.loadCurrentRevisionKeys())
                .containsExactlyEntriesOf(Map.of(current.getName(), "current-revision"));
    }

    @Test
    void seatHolderLinkedOnlyAsThePlayingUserIsNotAnUnreferencedUser() {
        Game game = gameWithRealPlayers("pbd-db-replaced-seat", 3);
        Player replacement = game.getPlayer("user-1");
        game.getPlayers().remove("user-1");
        replacement.setUserID("replacement-id");
        replacement.setUserName("Replacement");
        replacement.setStatsTrackedUserID("user-1");
        game.getPlayers().put("replacement-id", replacement);

        persistenceService.replace(GameEntityMapper.toSnapshot(game));

        assertThat(userRepository.findById("replacement-id"))
                .get()
                .extracting(UserEntity::getName)
                .isEqualTo("Replacement");
        assertThat(userRepository.findUnreferencedUserIds()).doesNotContain("replacement-id", "user-1");
        assertThat(persistedManagedGameService.loadAll().get(game.getName()).participants())
                .extracting(ManagedGameState.Participant::userId)
                .contains("replacement-id")
                .doesNotContain("user-1");
    }

    @Test
    void resavingAGameReplacesItsSeats() {
        Game game = gameWithRealPlayers("pbd-db-resave", 3);
        game.addPlayer("spectator-id", "Spectator");
        persistenceService.replace(GameEntityMapper.toSnapshot(game));

        game.removePlayer("spectator-id");
        game.addPlayer("newcomer-id", "Newcomer");
        persistenceService.replace(GameEntityMapper.toSnapshot(game));

        assertThat(persistedManagedGameService.loadAll().get(game.getName()).participants())
                .extracting(ManagedGameState.Participant::userId)
                .containsExactly("newcomer-id", "user-1", "user-2", "user-3");
    }

    @Test
    void statisticsIgnoredGameIsStoredWithPlayerAndTitleRows() {
        Game game = gameWithRealPlayers("pbd-db-ignored", 2);
        game.setStoredValue("TitlesForuser-1", "Kingmaker");
        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game);

        persistenceService.replace(snapshot);

        assertThat(gameRepository.findById(game.getName()))
                .get()
                .extracting(GameEntity::isStatisticsIgnored)
                .isEqualTo(true);
        assertThat(playerRepository.findAllWithUsersAndGames()).hasSize(2);
        assertThat(titleRepository.findAllWithUsersAndGames()).hasSize(1);
        assertThat(persistedManagedGameService.loadAll().get(game.getName())).isEqualTo(ManagedGameState.of(game));
        assertThat(PersistedGameState.of(snapshot)
                        .describeDifferencesFrom(
                                persistedGameStateService.loadAll().get(game.getName())))
                .isEmpty();
    }

    @Test
    void statisticsQueriesLeaveOutIgnoredGamesThatHaveEnded() {
        storeGameForEveryStatisticsQuery("pbd-db-ended-counted", false, 1L);
        storeGameForEveryStatisticsQuery("pbd-db-ended-ignored", true, 1L);
        List<String> userIds = List.of("user-1");

        assertOnlyCountedGame(playerRepository.findAllWithUsers(), "pbd-db-ended-counted");
        assertOnlyCountedGame(playerRepository.findAllWithUsersByCompletedGame(), "pbd-db-ended-counted");
        assertOnlyCountedGame(playerRepository.findAllWithUsersByUserIdIn(userIds), "pbd-db-ended-counted");
        assertOnlyCountedGame(playerRepository.findAllWithUsersAndGamesByUserIdIn(userIds), "pbd-db-ended-counted");
        assertOnlyCountedGame(playerRepository.findAllWithGamesByUserIdEquals("user-1"), "pbd-db-ended-counted");
        assertOnlyCountedGame(playerRepository.findAllWithUsersAndSixPlayerCompletedGames(), "pbd-db-ended-counted");
        assertOnlyCountedGame(
                playerRepository.findAllWithCompletedGamesByUserIdEquals("user-1"), "pbd-db-ended-counted");
        assertOnlyCountedGame(
                playerRepository.findAllWithUsersAndGamesByCompletedNonAllianceGame(false), "pbd-db-ended-counted");
        assertOnlyCountedGame(
                playerRepository.findAllWithUsersAndGamesByCompletedNonAllianceGame(true), "pbd-db-ended-counted");
        assertThat(titleRepository.findAllWithUsers())
                .extracting(title -> title.getGame().getGameName())
                .containsOnly("pbd-db-ended-counted");
        assertThat(titleRepository.findEndedGameTitlesByUserId("user-1"))
                .extracting(EarnedTitle::source)
                .containsOnly("pbd-db-ended-counted");
    }

    @Test
    void statisticsQueriesLeaveOutSeatsThatAreNotPlaying() {
        storeGameForEveryStatisticsQuery("pbd-db-seated-counted", false, 1L);
        List<String> seatedUserIds = List.of("seated-id");

        assertThat(playerRepository.findAllWithUsersAndGames())
                .filteredOn(player -> !player.isPlaying())
                .extracting(player -> player.getPlayingUser().getId())
                .containsExactly("seated-id");
        assertThat(playerRepository.findAllWithUsers()).allMatch(PlayerEntity::isPlaying);
        assertThat(playerRepository.findAllWithUsersByCompletedGame()).allMatch(PlayerEntity::isPlaying);
        assertThat(playerRepository.findAllWithUsersByUserIdIn(seatedUserIds)).isEmpty();
        assertThat(playerRepository.findAllWithUsersAndGamesByUserIdIn(seatedUserIds))
                .isEmpty();
        assertThat(playerRepository.findAllWithGamesByUserIdEquals("seated-id")).isEmpty();
        assertThat(playerRepository.findAllWithUsersAndSixPlayerCompletedGames())
                .allMatch(PlayerEntity::isPlaying);
        assertThat(playerRepository.findAllWithCompletedGamesByUserIdEquals("seated-id"))
                .isEmpty();
        assertThat(playerRepository.findAllWithUsersAndGamesByCompletedNonAllianceGame(false))
                .allMatch(PlayerEntity::isPlaying);
    }

    @Test
    void activeGameStatisticsLeaveOutIgnoredGames() {
        storeGameForEveryStatisticsQuery("pbd-db-active-counted", false, null);
        storeGameForEveryStatisticsQuery("pbd-db-active-ignored", true, null);

        assertOnlyCountedGame(playerRepository.findAllWithUsersByActiveGame(), "pbd-db-active-counted");
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
        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game);

        persistenceService.replace(snapshot);

        PersistedGameState persisted = persistedGameStateService.loadAll().get(game.getName());
        assertThat(PersistedGameState.of(snapshot).describeDifferencesFrom(persisted))
                .isEmpty();
    }

    @Test
    void playerRowWrittenByThePreviousReleaseCountsAsPlaying() {
        Game game = gameWithRealPlayers("pbd-db-previous-release", 3);
        persistenceService.replace(GameEntityMapper.toSnapshot(game));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // The previous release writes only real and eliminated seats and knows nothing of the seat columns, so a
        // row it inserts during a rolling deploy must default to a playing, non-NPC seat.
        jdbc.update("DELETE FROM player WHERE game_name = ? AND user_id = ?", game.getName(), "user-1");
        jdbc.update(
                "INSERT INTO player (user_id, game_name, faction_name, score, total_number_of_turns, total_turn_time,"
                        + " expected_hits, actual_hits, is_eliminated, is_winner, is_replaced)"
                        + " VALUES (?, ?, 'sol', 0, 0, 0, 0, 0, FALSE, FALSE, FALSE)",
                "user-1",
                game.getName());

        assertThat(playerRepository.findAllWithUsersByUserIdIn(List.of("user-1")))
                .singleElement()
                .satisfies(player -> {
                    assertThat(player.isPlaying()).isTrue();
                    assertThat(player.isNpc()).isFalse();
                    assertThat(player.getPlayingUser()).isNull();
                });
    }

    private void storeGameForEveryStatisticsQuery(
            String gameName, boolean statisticsIgnored, Long endedEpochMilliseconds) {
        Game game = gameWithRealPlayers(gameName, 6);
        game.addPlayer("seated-id", "Seated");
        game.setStoredValue("TitlesForuser-1", "Kingmaker");
        GameEntitySnapshot snapshot = GameEntityMapper.toSnapshot(game);
        // Every other column matches all the statistics queries, so only the ignored flag decides what they return.
        GameEntity entity = snapshot.game();
        entity.setStatisticsIgnored(statisticsIgnored);
        entity.setEndedEpochMilliseconds(endedEpochMilliseconds);
        entity.setCompleted(true);
        entity.setTwilightImperiumGlobalLeague(true);
        persistenceService.replace(snapshot);
    }

    private static void assertOnlyCountedGame(List<PlayerEntity> players, String countedGameName) {
        assertThat(players).isNotEmpty();
        assertThat(players).extracting(player -> player.getGame().getGameName()).containsOnly(countedGameName);
        assertThat(players).allMatch(PlayerEntity::isPlaying);
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
