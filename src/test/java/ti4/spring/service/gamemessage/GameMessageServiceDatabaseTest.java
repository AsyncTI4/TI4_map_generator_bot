package ti4.spring.service.gamemessage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
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
import ti4.message.GameMessage;
import ti4.message.GameMessageType;

// Runs against a real Postgres, e.g. a throwaway database in a local container:
//   TI4_TEST_POSTGRES_URL=jdbc:postgresql://localhost:5432/tibot_test
// Skipped when the variable is unset (CI has no database).
@EnabledIfEnvironmentVariable(named = "TI4_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(
        classes = GameMessageServiceDatabaseTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class GameMessageServiceDatabaseTest {

    private static final String GAME = "pbd1000";

    @Autowired
    private GameMessageService service;

    @Autowired
    private GameMessageEntityRepository repository;

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
    void addIgnoresAMessageIdAlreadyTrackedForTheGame() {
        service.add(GAME, new GameMessage("m1", GameMessageType.ACTION_CARD, 10L));
        service.add(GAME, new GameMessage("m1", GameMessageType.ACTION_CARD, 20L));

        assertThat(service.getAll(GAME, GameMessageType.ACTION_CARD))
                .singleElement()
                .extracting(GameMessage::gameSaveTime)
                .isEqualTo(10L);
    }

    @Test
    void replaceSwapsTheMessageWithTheSameTypeAndKeyAndReturnsTheOldId() {
        service.add(GAME, new GameMessage("old", GameMessageType.STRATEGY_CARD, 10L, "4::8"));
        service.add(GAME, new GameMessage("other", GameMessageType.STRATEGY_CARD, 10L, "4::2"));

        assertThat(service.replace(GAME, new GameMessage("new", GameMessageType.STRATEGY_CARD, 30L, "4::8")))
                .contains("old");
        assertThat(service.getOne(GAME, GameMessageType.STRATEGY_CARD, "4::8"))
                .get()
                .extracting(GameMessage::messageId)
                .isEqualTo("new");
        assertThat(service.getAll(GAME, GameMessageType.STRATEGY_CARD)).hasSize(2);
    }

    @Test
    void replaceAddsWhenNothingMatches() {
        assertThat(service.replace(GAME, new GameMessage("m1", GameMessageType.TURN, 10L)))
                .isEmpty();
        assertThat(service.getOne(GAME, GameMessageType.TURN, null)).isPresent();
    }

    @Test
    void nullKeyOnlyMatchesMessagesWithoutAKey() {
        service.add(GAME, new GameMessage("keyed", GameMessageType.AGENDA_WHEN, 10L, "x"));

        assertThat(service.getOne(GAME, GameMessageType.AGENDA_WHEN, null)).isEmpty();

        service.add(GAME, new GameMessage("unkeyed", GameMessageType.AGENDA_WHEN, 10L));

        assertThat(service.getOne(GAME, GameMessageType.AGENDA_WHEN, null))
                .get()
                .extracting(GameMessage::messageId)
                .isEqualTo("unkeyed");
    }

    @Test
    void reactionsKeepTheirOrderAndAreNotDuplicated() {
        service.add(GAME, new GameMessage("m1", GameMessageType.ACTION_CARD, 10L));

        service.addReaction(GAME, "xxcha", GameMessageType.ACTION_CARD, null);
        service.addReaction(GAME, "argent", "m1");
        service.addReaction(GAME, "xxcha", "m1");

        assertThat(service.getOne(GAME, "m1").orElseThrow().factionsThatReacted())
                .containsExactly("xxcha", "argent");
    }

    @Test
    void concurrentReactionsToTheSameMessageAreAllKept() throws InterruptedException {
        // The old file store relied on one global lock; the per-game lock must still prevent lost updates.
        service.add(GAME, new GameMessage("m1", GameMessageType.ACTION_CARD, 10L));
        List<String> factions = List.of("argent", "xxcha", "hacan", "sol", "jolnar", "muaat", "nekro", "yin");

        runConcurrently(factions.size(), i -> service.addReaction(GAME, factions.get(i), "m1"));

        assertThat(service.getOne(GAME, "m1").orElseThrow().factionsThatReacted())
                .containsExactlyInAnyOrderElementsOf(factions);
    }

    @Test
    void concurrentReplacesIntoAnEmptyGameLeaveOneMessage() throws InterruptedException {
        // A row lock can't serialize inserts when no row exists yet; the per-game advisory lock must.
        runConcurrently(8, i -> service.replace(GAME, new GameMessage("m" + i, GameMessageType.TURN, i)));

        assertThat(service.getAll(GAME, GameMessageType.TURN)).hasSize(1);
    }

    @Test
    void concurrentAddsOfTheSameMessageLeaveOneRow() throws InterruptedException {
        runConcurrently(8, _ -> service.add(GAME, new GameMessage("same", GameMessageType.ACTION_CARD, 10L)));

        assertThat(service.getAll(GAME, GameMessageType.ACTION_CARD)).hasSize(1);
    }

    @Test
    void removeByTypeAndKeyReturnsTheRemovedMessageId() {
        service.add(GAME, new GameMessage("m1", GameMessageType.TURN, 10L));

        assertThat(service.remove(GAME, GameMessageType.TURN, null)).contains("m1");
        assertThat(service.remove(GAME, GameMessageType.TURN, null)).isEmpty();
    }

    @Test
    void removeSavedAfterOnlyDropsMessagesNewerThanTheUndoPoint() {
        service.add(GAME, new GameMessage("before", GameMessageType.ACTION_CARD, 10L));
        service.add(GAME, new GameMessage("after", GameMessageType.ACTION_CARD, 30L));

        service.removeSavedAfter(GAME, 20L);

        assertThat(service.getAll(GAME, GameMessageType.ACTION_CARD))
                .extracting(GameMessage::messageId)
                .containsExactly("before");
    }

    @Test
    void removeGamesAndRemoveByMessageId() {
        service.add(GAME, new GameMessage("m1", GameMessageType.TURN, 10L));
        service.add(GAME, new GameMessage("m2", GameMessageType.ACTION_CARD, 10L));
        service.add("pbd2000", new GameMessage("m3", GameMessageType.TURN, 10L));

        service.remove(GAME, "m2");
        assertThat(service.getOne(GAME, "m2")).isEmpty();

        service.removeGames(List.of(GAME));
        assertThat(service.getOne(GAME, "m1")).isEmpty();
        assertThat(service.getOne("pbd2000", "m3")).isPresent();
    }

    @Test
    void getAllByGameGroupsOneTypeAcrossGames() {
        service.add(GAME, new GameMessage("m1", GameMessageType.TURN, 10L));
        service.add(GAME, new GameMessage("m2", GameMessageType.ACTION_CARD, 10L));
        service.add("pbd2000", new GameMessage("m3", GameMessageType.TURN, 10L));

        Map<String, List<GameMessage>> turns = service.getAllByGame(GameMessageType.TURN);

        assertThat(turns).containsOnlyKeys(GAME, "pbd2000");
        assertThat(turns.get(GAME)).extracting(GameMessage::messageId).containsExactly("m1");
    }

    @Test
    void importMissingMergesWithoutDuplicatesAndIsSafeToRepeat() {
        // A press may already have written a message before the startup import runs.
        service.add(GAME, new GameMessage("m1", GameMessageType.TURN, 10L));
        LinkedHashSet<String> reacted = new LinkedHashSet<>(List.of("argent"));
        Map<String, List<GameMessage>> legacy = Map.of(
                GAME,
                List.of(
                        new GameMessage("m1", GameMessageType.TURN, 10L),
                        new GameMessage("m2", GameMessageType.ACTION_CARD, reacted, 20L)),
                "pbd2000",
                List.of(new GameMessage("m3", GameMessageType.STRATEGY_CARD, 30L, "4::8")));

        assertThat(service.importMissing(legacy)).isEqualTo(2);
        assertThat(service.importMissing(legacy)).isZero();
        assertThat(service.getOne(GAME, "m2").orElseThrow().factionsThatReacted())
                .containsExactly("argent");
        assertThat(service.getOne("pbd2000", GameMessageType.STRATEGY_CARD, "4::8"))
                .isPresent();
    }

    private static void runConcurrently(int threads, IntConsumer work) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                int index = i;
                results.add(executor.submit(() -> {
                    start.await();
                    work.accept(index);
                    return null;
                }));
            }
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(results).allSatisfy(result -> assertThat(result).succeedsWithin(Duration.ZERO));
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
    @EntityScan(basePackageClasses = GameMessageEntity.class)
    @EnableJpaRepositories(basePackageClasses = GameMessageEntityRepository.class)
    @Import(GameMessageService.class)
    static class TestConfig {}
}
