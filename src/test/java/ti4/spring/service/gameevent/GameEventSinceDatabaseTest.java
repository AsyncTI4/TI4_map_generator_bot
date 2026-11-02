package ti4.spring.service.gameevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import ti4.game.Game;

// Runs against a real Postgres, e.g. a throwaway database in a local container:
//   TI4_TEST_POSTGRES_URL=jdbc:postgresql://localhost:5432/tibot_test
// Skipped when the variable is unset (CI has no database).
@EnabledIfEnvironmentVariable(named = "TI4_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(
        classes = GameEventSinceDatabaseTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class GameEventSinceDatabaseTest {

    @Autowired
    private GameEventService service;

    @Autowired
    private GameEventRepository repository;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TI4_TEST_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> envOrDefault("TI4_TEST_POSTGRES_USER", "tibot"));
        registry.add("spring.datasource.password", () -> envOrDefault("TI4_TEST_POSTGRES_PASSWORD", ""));
    }

    @BeforeEach
    void clearTable() {
        repository.deleteAll();
    }

    @Test
    void getEventsSinceReturnsOnlyEventsStrictlyAfterTheTimestampInSequenceOrder() {
        save("pbd1", 1, 100);
        save("pbd1", 2, 200);
        save("pbd1", 3, 300);
        save("pbd2", 1, 400);

        assertThat(service.getEventsSince(game("pbd1", 3), 100))
                .extracting(GameEventDto::seq)
                .containsExactly(2L, 3L);
    }

    @Test
    void getEventsSinceIgnoresRowsAboveTheGamesSequenceCounterLeftBehindByUndo() {
        save("pbd1", 1, 100);
        save("pbd1", 2, 200);
        save("pbd1", 3, 300);

        assertThat(service.getEventsSince(game("pbd1", 2), 0))
                .extracting(GameEventDto::seq)
                .containsExactly(1L, 2L);
    }

    @Test
    void findGameNamesWithEventsAfterListsEachGameOnceInNameOrder() {
        save("pbd2", 1, 500);
        save("pbd2", 2, 600);
        save("pbd1", 1, 700);
        save("pbd3", 1, 100);

        assertThat(repository.findGameNamesWithEventsAfter(100)).containsExactly("pbd1", "pbd2");
    }

    private void save(String gameName, long seq, long timestamp) {
        repository.save(new GameEventEntity(
                null, gameName, seq, "TURN", 1, "action", "sol", timestamp, "{}", null, null, null));
    }

    private static Game game(String name, long eventSequenceCounter) {
        Game game = mock(Game.class);
        when(game.getName()).thenReturn(name);
        when(game.getEventSequenceCounter()).thenReturn(eventSequenceCounter);
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
    @EntityScan(basePackageClasses = GameEventEntity.class)
    @EnableJpaRepositories(basePackageClasses = GameEventRepository.class)
    @Import(GameEventService.class)
    static class TestConfig {}
}
