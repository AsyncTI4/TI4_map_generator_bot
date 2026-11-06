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
import ti4.game.Game;
import ti4.test.PostgresDatabaseTest;

// Runs against a real Postgres, e.g. a throwaway database in a local container:
//   TI4_TEST_POSTGRES_URL=jdbc:postgresql://localhost:55432/tibot_ci_test
// Skipped when the variable is unset. CI provides a dedicated test database.
@EnabledIfEnvironmentVariable(named = "TI4_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(
        classes = GameEventSinceDatabaseTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class GameEventSinceDatabaseTest extends PostgresDatabaseTest {

    @Autowired
    private GameEventService service;

    @Autowired
    private GameEventRepository repository;

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
