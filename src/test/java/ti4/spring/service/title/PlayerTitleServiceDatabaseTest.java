package ti4.spring.service.title;

import static org.assertj.core.api.Assertions.assertThat;

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
import ti4.spring.service.persistence.EarnedTitle;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.GameEntityRepository;
import ti4.spring.service.persistence.StandaloneTitleEntity;
import ti4.spring.service.persistence.StandaloneTitleEntityRepository;
import ti4.spring.service.persistence.TitleEntity;
import ti4.spring.service.persistence.TitleEntityRepository;
import ti4.spring.service.persistence.UserEntity;
import ti4.spring.service.persistence.UserEntityRepository;

// Runs against a real Postgres. CI provides one (the postgres service in .github/workflows/run-tests.yml).
// Locally, set the variable to a throwaway database, never the dev "tibot" one, since create-drop drops its tables:
//   TI4_TEST_POSTGRES_URL=jdbc:postgresql://localhost:5432/tibot_test
// Skipped when the variable is unset.
@EnabledIfEnvironmentVariable(named = "TI4_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(
        classes = PlayerTitleServiceDatabaseTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PlayerTitleServiceDatabaseTest {

    @Autowired
    private PlayerTitleService service;

    @Autowired
    private TitleEntityRepository titleRepository;

    @Autowired
    private StandaloneTitleEntityRepository standaloneTitleRepository;

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
        standaloneTitleRepository.deleteAll();
        gameRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void endedGameTitlesAreOnlyThoseOfTheUserInEndedGames() {
        UserEntity alice = userRepository.save(new UserEntity("1", "alice"));
        UserEntity bob = userRepository.save(new UserEntity("2", "bob"));
        GameEntity ended = gameRepository.save(game("pbd10", 5_000L));
        GameEntity inProgress = gameRepository.save(game("pbd11", null));
        titleRepository.save(title(ended, alice, "Rules Master"));
        titleRepository.save(title(ended, alice, "Hard To Kill"));
        titleRepository.save(title(ended, bob, "Spice Bringer"));
        // Titles on a game that hasn't ended stay hidden, matching the old save-file scan.
        titleRepository.save(title(inProgress, alice, "Early Bird"));

        assertThat(service.getEndedGameTitles("1"))
                .containsExactlyInAnyOrder(
                        new EarnedTitle("Rules Master", "pbd10"), new EarnedTitle("Hard To Kill", "pbd10"));
    }

    @Test
    void standaloneTitlesCarryTheirSource() {
        UserEntity alice = userRepository.save(new UserEntity("1", "alice"));
        StandaloneTitleEntity standalone = new StandaloneTitleEntity();
        standalone.setUser(alice);
        standalone.setTitle("Tournament Champion");
        standalone.setSource("Fall Cup");
        standaloneTitleRepository.save(standalone);

        assertThat(service.getStandaloneTitles("1"))
                .containsExactly(new EarnedTitle("Tournament Champion", "Fall Cup"));
        assertThat(service.getStandaloneTitles("2")).isEmpty();
    }

    private static GameEntity game(String gameName, Long endedEpochMilliseconds) {
        GameEntity game = new GameEntity();
        game.setGameName(gameName);
        game.setEndedEpochMilliseconds(endedEpochMilliseconds);
        return game;
    }

    private static TitleEntity title(GameEntity game, UserEntity user, String title) {
        TitleEntity entity = new TitleEntity();
        entity.setGame(game);
        entity.setUser(user);
        entity.setTitle(title);
        return entity;
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
    @EntityScan(basePackageClasses = TitleEntity.class)
    @EnableJpaRepositories(basePackageClasses = TitleEntityRepository.class)
    @Import(PlayerTitleService.class)
    static class TestConfig {}
}
