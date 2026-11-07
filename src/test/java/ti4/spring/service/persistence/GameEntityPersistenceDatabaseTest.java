package ti4.spring.service.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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
    void replaceDoesNotOverwriteAKnownUserNameWithAnUnknownPlaceholder() {
        persistenceService.replace(snapshotWithUsers("pbd-names-1", new UserEntity("2", "bob")));

        persistenceService.replace(snapshotWithUsers(
                "pbd-names-2",
                new UserEntity("1", "alice"),
                new UserEntity("2", GameEntityMapper.UNKNOWN_USER_PREFIX + "2"),
                new UserEntity("3", GameEntityMapper.UNKNOWN_USER_PREFIX + "3")));

        // An existing user keeps its real name; a brand new unknown user still gets a row so its players can
        // reference it.
        assertThat(userNames("1", "2", "3"))
                .containsExactly("alice", "bob", GameEntityMapper.UNKNOWN_USER_PREFIX + "3");
    }

    @Test
    void replaceUpdatesAnExistingUserNameWhenTheNewNameIsKnown() {
        persistenceService.replace(snapshotWithUsers("pbd-rename-1", new UserEntity("1", "alice")));

        persistenceService.replace(snapshotWithUsers("pbd-rename-2", new UserEntity("1", "alicia")));

        assertThat(userNames("1")).containsExactly("alicia");
    }

    @Test
    void restoreKeepsExistingUserNamesButReplacesUnknownPlaceholders() {
        persistenceService.replace(snapshotWithUsers(
                "pbd-restore-1",
                new UserEntity("1", "alice"),
                new UserEntity("2", GameEntityMapper.UNKNOWN_USER_PREFIX + "2"),
                new UserEntity("3", GameEntityMapper.UNKNOWN_USER_PREFIX + "3")));

        // An old game file can hold a name the user has since changed; restoring it must not roll the name back.
        persistenceService.restore(snapshotWithUsers(
                "pbd-restore-2",
                new UserEntity("1", "old alice"),
                new UserEntity("2", "bob"),
                new UserEntity("3", GameEntityMapper.UNKNOWN_USER_PREFIX + "3"),
                new UserEntity("4", "carol")));

        assertThat(userNames("1", "2", "3", "4"))
                .containsExactly("alice", "bob", GameEntityMapper.UNKNOWN_USER_PREFIX + "3", "carol");
    }

    @Test
    void aSyncWhoseRevisionTheGameFileHasMovedPastIsSkipped() {
        // During a rollover the old process can still be draining a sync for a state the new process has already
        // saved over. That stale write must not replace the newer rows.
        assertThat(persistenceService.replaceIfCurrent(snapshotAtRevision("pbd-stale", "newer"), fileAt("newer")))
                .isTrue();

        assertThat(persistenceService.replaceIfCurrent(snapshotAtRevision("pbd-stale", "older"), fileAt("newer")))
                .isFalse();
        assertThat(persistenceService.restoreIfCurrent(snapshotAtRevision("pbd-stale", "older"), fileAt("newer")))
                .isFalse();

        assertThat(revisionOf("pbd-stale")).contains("newer");
    }

    @Test
    void aRestoreWhoseRevisionMatchesTheGameFileIsWritten() {
        assertThat(persistenceService.restoreIfCurrent(snapshotAtRevision("pbd-restore", "current"), fileAt("current")))
                .isTrue();

        assertThat(revisionOf("pbd-restore")).contains("current");
    }

    @Test
    void aGameFileWithoutARevisionKeyStillSyncsWhileTheFileExists() {
        // Games not saved since revision keys were introduced have none in their file. Their rows must still be
        // written, or warmup and reconciliation could never repair them.
        assertThat(persistenceService.restoreIfCurrent(snapshotAtRevision("pbd-legacy", null), fileAt(null)))
                .isTrue();

        assertThat(gameRepository.existsById("pbd-legacy")).isTrue();
    }

    @Test
    void aSyncForAGameWhoseFileIsGoneIsSkipped() {
        // A stale sync landing after the other process deleted the game must not bring its rows back.
        assertThat(persistenceService.restoreIfCurrent(snapshotAtRevision("pbd-gone", null), GameFileRevision::missing))
                .isFalse();
        assertThat(persistenceService.replaceIfCurrent(
                        snapshotAtRevision("pbd-gone", "key"), GameFileRevision::missing))
                .isFalse();

        assertThat(gameRepository.existsById("pbd-gone")).isFalse();
    }

    @Test
    void aSnapshotWithoutARevisionKeyDoesNotReplaceAKeyedGameFile() {
        assertThat(persistenceService.restoreIfCurrent(snapshotAtRevision("pbd-keyed", null), fileAt("key")))
                .isFalse();
    }

    @Test
    void aSyncMappedByOlderCodeDoesNotReplaceARowMappedByNewerCode() {
        // A deploy that bumps the mapping version: the new process rebuilds the row, then the old process drains a
        // sync for the same save. The older format must not win.
        GameEntitySnapshot newer = snapshotAtRevision("pbd-mapping", "same-save");
        newer.game().setMappingVersion(3);
        assertThat(persistenceService.restoreIfCurrent(newer, fileAt("same-save")))
                .isTrue();

        GameEntitySnapshot older = snapshotAtRevision("pbd-mapping", "same-save");
        older.game().setMappingVersion(2);
        assertThat(persistenceService.replaceIfCurrent(older, fileAt("same-save")))
                .isFalse();

        assertThat(gameRepository.findById("pbd-mapping").map(GameEntity::getMappingVersion))
                .contains(3);
    }

    @Test
    void aDeleteIsSkippedWhileTheGameFileExists() {
        persistenceService.replace(snapshotAtRevision("pbd-delete", "key"));

        assertThat(persistenceService.deleteIfGameFileMissing("pbd-delete", fileAt("key")))
                .isFalse();
        assertThat(gameRepository.existsById("pbd-delete")).isTrue();

        assertThat(persistenceService.deleteIfGameFileMissing("pbd-delete", GameFileRevision::missing))
                .isTrue();
        assertThat(gameRepository.existsById("pbd-delete")).isFalse();
    }

    @Test
    void guardedWritesOfOneGameWaitForEachOther() throws Exception {
        // The revision check and the write happen under one per-game lock, so a write that checks after another
        // process saved the game file cannot land after that process's own sync.
        CountDownLatch firstHoldsTheLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        Thread first = Thread.ofVirtual()
                .start(() -> persistenceService.replaceIfCurrent(snapshotAtRevision("pbd-locked", "first"), () -> {
                    firstHoldsTheLock.countDown();
                    awaitQuietly(releaseFirst);
                    return GameFileRevision.of("first");
                }));
        assertThat(firstHoldsTheLock.await(10, TimeUnit.SECONDS)).isTrue();

        CountDownLatch secondFinished = new CountDownLatch(1);
        Thread second = Thread.ofVirtual().start(() -> {
            persistenceService.replaceIfCurrent(snapshotAtRevision("pbd-locked", "second"), fileAt("second"));
            secondFinished.countDown();
        });

        assertThat(secondFinished.await(500, TimeUnit.MILLISECONDS))
                .as("second write waits while the first holds the game's lock")
                .isFalse();
        releaseFirst.countDown();
        first.join(10_000);
        second.join(10_000);
        assertThat(revisionOf("pbd-locked")).contains("second");
    }

    private static GameEntitySnapshot snapshotAtRevision(String gameName, String revisionKey) {
        GameEntitySnapshot snapshot = snapshotWithUsers(gameName);
        snapshot.game().setRevisionKey(revisionKey);
        return snapshot;
    }

    private static Supplier<GameFileRevision> fileAt(String revisionKey) {
        return () -> GameFileRevision.of(revisionKey);
    }

    private Optional<String> revisionOf(String gameName) {
        return gameRepository.findById(gameName).map(GameEntity::getRevisionKey);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static GameEntitySnapshot snapshotWithUsers(String gameName, UserEntity... users) {
        GameEntity game = new GameEntity();
        game.setGameName(gameName);
        return new GameEntitySnapshot(game, List.of(users), List.of());
    }

    private List<String> userNames(String... userIds) {
        return Arrays.stream(userIds)
                .map(id -> userRepository.findById(id).map(UserEntity::getName).orElse(null))
                .toList();
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
    void revisionKeysAreReadForEveryGameWithoutLoadingTheRows() {
        Game first = gameWithRealPlayers("pbd-db-revision-first", 3);
        Game second = gameWithRealPlayers("pbd-db-revision-second", 3);
        first.setRevisionKey("revision-first");
        second.setRevisionKey("revision-second");
        persistenceService.replace(GameEntityMapper.toSnapshot(first));
        persistenceService.replace(GameEntityMapper.toSnapshot(second));

        assertThat(persistedManagedGameService.loadRevisionKeys())
                .containsEntry(first.getName(), first.getRevisionKey())
                .containsEntry(second.getName(), second.getRevisionKey());
    }

    @Test
    void replacedPlayerWhoStaysSeatedIsStoredBesideTheSeatThatStillTracksThem() {
        // pbd23770b: user-1 was replaced on their seat but stayed in the game without a faction, so their old seat
        // and their own seat both name user-1 as the stats user.
        Game game = gameWithRealPlayers("pbd-db-replaced-and-seated", 3);
        Player replacedSeat = game.getPlayer("user-1");
        game.getPlayers().remove("user-1");
        replacedSeat.setUserID("replacement-id");
        replacedSeat.setUserName("Replacement");
        replacedSeat.setStatsTrackedUserID("user-1");
        game.getPlayers().put("replacement-id", replacedSeat);
        game.addPlayer("user-1", "User 1");

        persistenceService.replace(GameEntityMapper.toSnapshot(game));

        assertThat(playerRepository.findAllWithUsersAndGames())
                .filteredOn(player -> "user-1".equals(player.getStatsUser().getId()))
                .extracting(player -> player.getPlayingUser().getId(), PlayerEntity::isPlaying)
                .containsExactlyInAnyOrder(tuple("replacement-id", true), tuple("user-1", false));
        assertThat(persistedManagedGameService.loadAll().get(game.getName())).isEqualTo(ManagedGameState.of(game));
    }

    @Test
    void playerRowsAreUniquePerSeatHolder() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForList(
                        "SELECT pg_get_constraintdef(oid) FROM pg_constraint"
                                + " WHERE conrelid = 'player'::regclass AND contype = 'u'",
                        String.class))
                .containsExactly("UNIQUE (game_name, playing_user_id)");
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
