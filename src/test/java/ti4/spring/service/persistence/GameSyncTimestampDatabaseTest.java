package ti4.spring.service.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// Runs against a real Postgres, e.g. a throwaway database in a local container:
//   TI4_TEST_POSTGRES_URL=jdbc:postgresql://localhost:5432/tibot_test
// Skipped when the variable is unset (CI has no database).
@EnabledIfEnvironmentVariable(named = "TI4_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(
        classes = GameSyncTimestampDatabaseTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class GameSyncTimestampDatabaseTest {

    @Autowired
    private GameEntityPersistenceService persistenceService;

    @Autowired
    private PersistedGameStateService persistedGameStateService;

    @Autowired
    private TitleEntityRepository titleRepository;

    @Autowired
    private PlayerEntityRepository playerRepository;

    @Autowired
    private GameEntityRepository gameRepository;

    @Autowired
    private UserEntityRepository userRepository;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TI4_TEST_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> envOrDefault("TI4_TEST_POSTGRES_USER", "tibot"));
        registry.add("spring.datasource.password", () -> envOrDefault("TI4_TEST_POSTGRES_PASSWORD", ""));
    }

    @BeforeEach
    void clearTables() {
        titleRepository.deleteAll();
        playerRepository.deleteAll();
        gameRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void syncedRowRemembersTheSaveItCameFrom() {
        persistenceService.replace(snapshot("pbd1", 1_000L));

        PersistedGameState persisted = persistedGameStateService.loadAll().get("pbd1");

        assertThat(persisted.wasSyncedFrom(1_000L)).isTrue();
        assertThat(persisted.wasSyncedFrom(2_000L)).isFalse();
    }

    @Test
    void recordingTheSyncTimeLeavesTheRestOfTheRowAlone() {
        persistenceService.replace(snapshot("pbd1", null));
        PersistedGameState before = persistedGameStateService.loadAll().get("pbd1");

        persistenceService.recordSourceLastModified("pbd1", 3_000L);

        PersistedGameState after = persistedGameStateService.loadAll().get("pbd1");
        assertThat(before.wasSyncedFrom(3_000L)).isFalse();
        assertThat(after.wasSyncedFrom(3_000L)).isTrue();
        assertThat(after.describeDifferencesFrom(before)).isEmpty();
    }

    private static GameEntitySnapshot snapshot(String gameName, Long sourceLastModified) {
        UserEntity user = new UserEntity("1", "alice");
        GameEntity game = new GameEntity();
        game.setGameName(gameName);
        game.setPlayerCount(3);
        game.setSourceLastModifiedEpochMilliseconds(sourceLastModified);
        PlayerEntity player = new PlayerEntity();
        player.setGame(game);
        player.setUser(user);
        player.setFactionName("sol");
        game.getPlayers().add(player);
        return new GameEntitySnapshot(game, List.of(user), List.of());
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
    @Import({GameEntityPersistenceService.class, PersistedGameStateService.class})
    static class TestConfig {}
}
