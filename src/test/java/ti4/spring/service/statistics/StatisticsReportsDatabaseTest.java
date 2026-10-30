package ti4.spring.service.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.MockedStatic;
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
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.GameEntityRepository;
import ti4.spring.service.persistence.PlayerEntity;
import ti4.spring.service.persistence.PlayerEntityRepository;
import ti4.spring.service.persistence.TitleEntity;
import ti4.spring.service.persistence.TitleEntityRepository;
import ti4.spring.service.persistence.UserEntity;
import ti4.spring.service.persistence.UserEntityRepository;
import ti4.spring.service.statistics.matchmaking.MatchmakingRatingEventService;
import ti4.spring.service.tigl.TiglGamesInfoService;

// The statistics services run their repository queries without a surrounding transaction, so every
// entity they read is detached. Touching an association the query did not JOIN FETCH throws
// LazyInitializationException. These tests run each report end to end against a real Postgres so a
// query/usage mismatch fails here rather than in production.
//
// Runs against a throwaway database, e.g. in a local container:
//   TI4_TEST_POSTGRES_URL=jdbc:postgresql://localhost:5432/tibot_test
// Skipped when the variable is unset (CI has no database).
@EnabledIfEnvironmentVariable(named = "TI4_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(
        classes = StatisticsReportsDatabaseTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class StatisticsReportsDatabaseTest {

    private static final int PLAYERS_PER_GAME = 6;
    private static final int TURNS_PER_PLAYER = 120;

    @Autowired
    private AverageHitsPerTurnService averageHitsPerTurnService;

    @Autowired
    private AverageTurnTimeService averageTurnTimeService;

    @Autowired
    private DiceLuckService diceLuckService;

    @Autowired
    private ListTitlesGivenService listTitlesGivenService;

    @Autowired
    private MedianTurnTimeService medianTurnTimeService;

    @Autowired
    private UserGameInfoService userGameInfoService;

    @Autowired
    private MatchmakingRatingEventService matchmakingRatingEventService;

    @Autowired
    private TiglGamesInfoService tiglGamesInfoService;

    @Autowired
    private UserEntityRepository userRepository;

    @Autowired
    private GameEntityRepository gameRepository;

    @Autowired
    private PlayerEntityRepository playerRepository;

    @Autowired
    private TitleEntityRepository titleRepository;

    private final List<String> sentMessages = new ArrayList<>();
    private MockedStatic<MessageHelper> messageHelper;
    private List<UserEntity> users;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TI4_TEST_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> envOrDefault("TI4_TEST_POSTGRES_USER", "tibot"));
        registry.add("spring.datasource.password", () -> envOrDefault("TI4_TEST_POSTGRES_PASSWORD", ""));
    }

    @BeforeEach
    void seedGames() {
        titleRepository.deleteAll();
        playerRepository.deleteAll();
        gameRepository.deleteAll();
        userRepository.deleteAll();

        users = new ArrayList<>();
        for (int i = 1; i <= PLAYERS_PER_GAME; i++) {
            users.add(userRepository.save(new UserEntity(String.valueOf(i), "user" + i)));
        }
        long now = Instant.now().toEpochMilli();
        GameEntity completed =
                gameRepository.save(game("pbd1", now - Duration.ofDays(9).toMillis(), now, false));
        GameEntity ongoingTigl =
                gameRepository.save(game("pbd2", now - Duration.ofDays(2).toMillis(), null, true));
        for (int seat = 0; seat < PLAYERS_PER_GAME; seat++) {
            playerRepository.save(player(completed, users.get(seat), seat == 0));
            playerRepository.save(player(ongoingTigl, users.get(seat), false));
        }
        titleRepository.save(title(completed, users.getFirst(), "Rules Master"));

        // Every report sends through MessageHelper; record the arguments instead of reaching Discord.
        messageHelper = mockStatic(MessageHelper.class, invocation -> {
            sentMessages.add(Arrays.deepToString(invocation.getArguments()));
            return null;
        });
    }

    @AfterEach
    void closeMessageHelper() {
        messageHelper.close();
    }

    @Test
    void slashCommandReportsReadOnlyFetchedAssociations() {
        averageHitsPerTurnService.getExpectedHitsPerTurn(eventWithOptions(Map.of()));
        diceLuckService.getActualVersusExpectedHits(eventWithOptions(Map.of()));
        medianTurnTimeService.getMedianTurnTimes(eventWithOptions(Map.of()));
        averageTurnTimeService.getAverageTurnTimes(eventWithOptions(Map.of()));
        listTitlesGivenService.listTitlesGiven(eventWithOptions(Map.of()));
        matchmakingRatingEventService.calculateRatings(eventWithOptions(Map.of()));
        matchmakingRatingEventService.showRatingHistory(eventWithOptions(Map.of()), "1", "user1");

        assertThat(sentMessages).hasSize(7);
        // One game calibrates no-one, so the ratings list names nobody but still counts all six players.
        assertThat(sentMessages.get(5)).contains("Player Matchmaking Ratings").contains("calibrated (0 of 6)");
        assertThat(sentMessages)
                .filteredOn(message -> !message.contains("Player Matchmaking Ratings"))
                .allSatisfy(message -> assertThat(message).contains("user1"));
    }

    @Test
    void ignoringEndedGamesStillReadsOnlyFetchedAssociations() {
        SlashCommandInteractionEvent event =
                eventWithOptions(Map.<String, Object>of(Constants.IGNORE_ENDED_GAMES, true));

        diceLuckService.getActualVersusExpectedHits(event);
        medianTurnTimeService.getMedianTurnTimes(event);
        averageTurnTimeService.getAverageTurnTimes(event);

        assertThat(sentMessages).hasSize(3);
    }

    @Test
    void fullPlayerBaseTurnTimeReportUsesBatchedFastestDays() {
        SlashCommandInteractionEvent event = eventWithOptions(Map.<String, Object>of(Constants.TOP_LIMIT, 2000));

        averageTurnTimeService.getAverageTurnTimes(event);

        // Each of the six users has one 6-player game finished in 9 days, so all count as "with fast games".
        assertThat(sentMessages).hasSize(2);
        assertThat(sentMessages)
                .allSatisfy(message -> assertThat(message).contains("under 60 minutes: 6 (with fast games: 6)"));
    }

    @Test
    void userLookupsReadOnlyFetchedAssociations() {
        List<String> userIds = users.stream().map(UserEntity::getId).toList();

        assertThat(averageHitsPerTurnService.getAverageHitsPerTurn(userIds)).contains("user1");
        assertThat(diceLuckService.getDiceLuck(userIds)).contains("user1");
        assertThat(averageTurnTimeService.getAverageTurnTimesString(userIds)).contains("user1");
        assertThat(userGameInfoService.getUsersThreeFastestDaysToComplete6PlayerGames("1"))
                .containsExactly(9);
        assertThat(userGameInfoService.getThreeFastestDaysToComplete6PlayerGamesByUserId())
                .containsOnlyKeys(userIds)
                .containsEntry("1", List.of(9));
        assertThat(userGameInfoService.hasCompletedGameInDays("1", 10)).isTrue();
        assertThat(userGameInfoService.getUserGameRecord("1").completedGames()).isEqualTo(1);
        assertThat(userGameInfoService.getUserGameInfo(List.of(discordUser("1"))))
                .contains("user1");
    }

    @Test
    void tiglReportReadsOngoingGames() {
        assertThat(tiglGamesInfoService.getOngoingGamesByRankMessage(true)).contains("pbd2");
    }

    @SuppressWarnings("unchecked")
    private static SlashCommandInteractionEvent eventWithOptions(Map<String, Object> overrides) {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        when(event.getOption(anyString(), (Object) any(), any(Function.class)))
                .thenAnswer(invocation -> overrides.getOrDefault(invocation.getArgument(0), invocation.getArgument(1)));
        return event;
    }

    private static net.dv8tion.jda.api.entities.User discordUser(String id) {
        net.dv8tion.jda.api.entities.User user = mock(net.dv8tion.jda.api.entities.User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    private static GameEntity game(String gameName, long created, Long ended, boolean tigl) {
        GameEntity game = new GameEntity();
        game.setGameName(gameName);
        game.setCreationEpochMilliseconds(created);
        game.setEndedEpochMilliseconds(ended);
        game.setCompleted(ended != null);
        game.setPlayerCount(PLAYERS_PER_GAME);
        game.setVictoryPointGoal(10);
        game.setTwilightImperiumGlobalLeague(tigl);
        return game;
    }

    private static PlayerEntity player(GameEntity game, UserEntity user, boolean winner) {
        PlayerEntity player = new PlayerEntity();
        player.setGame(game);
        player.setUser(user);
        player.setWinner(winner);
        player.setScore(winner ? 10 : 7);
        player.setTotalNumberOfTurns(TURNS_PER_PLAYER);
        player.setTotalTurnTime(TURNS_PER_PLAYER * Duration.ofMinutes(30).toMillis());
        player.setExpectedHits(40.0);
        player.setActualHits(42);
        return player;
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
    @EntityScan(basePackageClasses = PlayerEntity.class)
    @EnableJpaRepositories(basePackageClasses = PlayerEntityRepository.class)
    @Import({
        AverageHitsPerTurnService.class,
        AverageTurnTimeService.class,
        DiceLuckService.class,
        ListTitlesGivenService.class,
        MedianTurnTimeService.class,
        UserGameInfoService.class,
        MatchmakingRatingEventService.class,
        TiglGamesInfoService.class
    })
    static class TestConfig {}
}
