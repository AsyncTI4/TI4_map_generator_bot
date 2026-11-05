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
import ti4.test.PostgresDatabaseTest;

// Runs against a real Postgres. CI provides one (the postgres service in .github/workflows/run-tests.yml).
// Locally, use docker-compose.test.yml; the shared setup rejects unsafe database names and roles:
//   TI4_TEST_POSTGRES_URL=jdbc:postgresql://localhost:55432/tibot_ci_test
// Skipped when the variable is unset.
@EnabledIfEnvironmentVariable(named = "TI4_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(
        classes = GameRevisionBackfillDatabaseTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class GameRevisionBackfillDatabaseTest extends PostgresDatabaseTest {

    @Autowired
    private GameEntityPersistenceService service;

    @Autowired
    private GameEntityRepository gameRepository;

    @Autowired
    private PlayerEntityRepository playerRepository;

    @Autowired
    private TitleEntityRepository titleRepository;

    @Autowired
    private UserEntityRepository userRepository;

    @BeforeEach
    void clearTables() {
        titleRepository.deleteAll();
        playerRepository.deleteAll();
        gameRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void updateRevisionSetsOnlyTheRevisionColumns() {
        UserEntity alice = userRepository.save(new UserEntity("1", "alice"));
        GameEntity legacy = game("pbd1", 3);
        legacy.getPlayers().add(player(legacy, alice));
        gameRepository.save(legacy);

        service.updateRevision("pbd1", "revision", GameEntityMapper.MAPPING_VERSION);

        GameEntity updated = gameRepository.findById("pbd1").orElseThrow();
        assertThat(updated.getRevisionKey()).isEqualTo("revision");
        assertThat(updated.getMappingVersion()).isEqualTo(GameEntityMapper.MAPPING_VERSION);
        assertThat(updated.getRound()).isEqualTo(3);
        assertThat(playerRepository.count()).isEqualTo(1);
    }

    @Test
    void restoreRewritesTheGameWithoutRenamingExistingUsers() {
        userRepository.save(new UserEntity("1", "alice"));
        userRepository.save(new UserEntity("2", GameEntityMapper.UNKNOWN_USER_PREFIX + "2"));
        gameRepository.save(game("pbd1", 1));

        UserEntity staleAlice = new UserEntity("1", "old alice");
        UserEntity bob = new UserEntity("2", "bob");
        GameEntity restored = game("pbd1", 4);
        restored.getPlayers().add(player(restored, staleAlice));
        restored.getPlayers().add(player(restored, bob));
        service.restore(new GameEntitySnapshot(restored, List.of(staleAlice, bob), List.of()));

        assertThat(gameRepository.findById("pbd1").orElseThrow().getRound()).isEqualTo(4);
        assertThat(playerRepository.count()).isEqualTo(2);
        assertThat(userRepository.findById("1").orElseThrow().getName()).isEqualTo("alice");
        assertThat(userRepository.findById("2").orElseThrow().getName()).isEqualTo("bob");
    }

    private static GameEntity game(String gameName, int round) {
        GameEntity game = new GameEntity();
        game.setGameName(gameName);
        game.setRound(round);
        return game;
    }

    private static PlayerEntity player(GameEntity game, UserEntity user) {
        PlayerEntity player = new PlayerEntity();
        player.setGame(game);
        player.setStatsUser(user);
        player.setFactionName("sol" + user.getId());
        return player;
    }

    @Configuration
    @ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        TransactionAutoConfiguration.class
    })
    @EntityScan(basePackageClasses = GameEntity.class)
    @EnableJpaRepositories(basePackageClasses = GameEntityRepository.class)
    @Import(GameEntityPersistenceService.class)
    static class TestConfig {}
}
