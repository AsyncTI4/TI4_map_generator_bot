package ti4.contest.replay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.support.TransactionTemplate;
import ti4.contest.replay.core.CombatCandidatePromotionStatus;
import ti4.contest.replay.core.CombatCandidateStatus;
import ti4.contest.replay.core.CombatContestReplayStatus;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatContestSideBetEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.entities.CombatReplayLeaderboardEntryEntity;
import ti4.contest.replay.entities.CombatReplayPredictionEntity;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatContestSideBetRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayLeaderboardEntryRepository;
import ti4.contest.replay.repository.CombatReplayPredictionRepository;
import ti4.test.PostgresDatabaseTest;

// Checks that a failure part-way through scoring, side-bet resolution or promotion rolls back every write,
// so a retry (the next cron tick, or the next instance after a deploy) neither double-awards nor loses points.
// Runs against a real Postgres, like GameMessageServiceDatabaseTest:
//   TI4_TEST_POSTGRES_URL=jdbc:postgresql://localhost:55432/tibot_ci_test
// Skipped when the variable is unset.
@EnabledIfEnvironmentVariable(named = "TI4_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(
        classes = CombatReplayAtomicWritesDatabaseTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class CombatReplayAtomicWritesDatabaseTest extends PostgresDatabaseTest {

    private static final String PREDICTOR_ID = "100";
    private static final String BETTOR_ID = "200";
    private static final String ATTACKER = "sol";
    private static final String DEFENDER = "yin";

    @Autowired
    private CombatReplayLeaderboardEntryRepository leaderboardEntryRepository;

    @Autowired
    private CombatReplayPredictionRepository predictionRepository;

    @Autowired
    private CombatContestSideBetRepository sideBetRepository;

    @Autowired
    private CombatReplayContestRepository contestRepository;

    @Autowired
    private CombatCandidateRepository candidateRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final CombatContestSettings settings = new CombatContestSettings();

    @BeforeEach
    void clearTables() {
        sideBetRepository.deleteAll();
        predictionRepository.deleteAll();
        leaderboardEntryRepository.deleteAll();
        contestRepository.deleteAll();
        candidateRepository.deleteAll();
    }

    @Test
    void failedSideBetResolutionRollsBackPredictionPointsSoTheRetryAwardsThemOnce() {
        CombatReplayContestEntity contest = contestWithId(1L);
        CombatCandidateEntity candidate = unsavedCandidate();
        predictionRepository.save(lockedPrediction(contest.getId()));

        CombatReplaySideBetService failingSideBets = mock(CombatReplaySideBetService.class);
        when(failingSideBets.resolveSideBets(any(), any())).thenThrow(new IllegalStateException("simulated crash"));
        assertThatThrownBy(() -> leaderboardService(failingSideBets, leaderboardEntryRepository)
                        .finalizeReplayLeaderboardContest(null, contest, candidate))
                .isInstanceOf(IllegalStateException.class);

        // The prediction points were written before the crash; the rollback must undo them along with scoredAt.
        assertThat(leaderboardEntryRepository.findByDiscordUserId(PREDICTOR_ID)).isEmpty();
        assertThat(predictionRepository
                        .findByContestId(contest.getId())
                        .orElseThrow()
                        .getScoredAt())
                .isNull();

        CombatReplaySideBetService workingSideBets = mock(CombatReplaySideBetService.class);
        when(workingSideBets.resolveSideBets(any(), any()))
                .thenReturn(CombatReplaySideBetService.SideBetResolution.empty());
        CombatReplayLeaderboardService service = leaderboardService(workingSideBets, leaderboardEntryRepository);
        service.finalizeReplayLeaderboardContest(null, contest, candidate);
        int pointsAfterScoring = predictorPoints();
        service.finalizeReplayLeaderboardContest(null, contest, candidate);

        assertThat(predictorPoints()).isEqualTo(pointsAfterScoring);
        assertThat(leaderboardEntryRepository
                        .findByDiscordUserId(PREDICTOR_ID)
                        .orElseThrow()
                        .getPredictionCount())
                .isEqualTo(1);
        assertThat(predictionRepository
                        .findByContestId(contest.getId())
                        .orElseThrow()
                        .getScoredAt())
                .isNotNull();
    }

    @Test
    void failedCreditSaveRollsBackTheResolvedMarkerSoTheRetryStillPaysTheWinnings() {
        CombatReplayContestEntity contest = contestWithId(2L);
        CombatCandidateEntity candidate = unsavedCandidate();
        candidate.setAttackerPlayedMoraleBoost(true);
        leaderboardEntryRepository.save(leaderboardEntry(BETTOR_ID, 50));
        sideBetRepository.save(moraleBoostBet(contest.getId(), 16));

        CombatReplayLeaderboardEntryRepository failingEntries = mock(
                CombatReplayLeaderboardEntryRepository.class,
                AdditionalAnswers.delegatesTo(leaderboardEntryRepository));
        doThrow(new IllegalStateException("simulated crash"))
                .when(failingEntries)
                .saveAll(any());
        assertThatThrownBy(() -> leaderboardService(sideBetService(failingEntries), failingEntries)
                        .finalizeReplayLeaderboardContest(null, contest, candidate))
                .isInstanceOf(IllegalStateException.class);

        // The bets were marked resolved before the crash; the rollback must leave them unresolved.
        assertThat(sideBetRepository.findByContestId(contest.getId()))
                .allSatisfy(bet -> assertThat(bet.getResolvedAt()).isNull());
        assertThat(bettorPoints()).isEqualTo(50);

        CombatReplayLeaderboardService service =
                leaderboardService(sideBetService(leaderboardEntryRepository), leaderboardEntryRepository);
        service.finalizeReplayLeaderboardContest(null, contest, candidate);
        service.finalizeReplayLeaderboardContest(null, contest, candidate);

        assertThat(bettorPoints()).isEqualTo(66);
        assertThat(sideBetRepository.findByContestId(contest.getId()))
                .allSatisfy(bet -> assertThat(bet.getResolvedAt()).isNotNull());
    }

    @Test
    void failedCandidateSaveRollsBackTheContestResetAndKeepsTheLockedPrediction() {
        CombatCandidateEntity candidate = candidateRepository.save(unsavedCandidate());
        CombatReplayContestEntity existingContest = completedContest(candidate.getId());
        predictionRepository.save(lockedPrediction(existingContest.getId()));

        CombatCandidateRepository failingCandidates =
                mock(CombatCandidateRepository.class, AdditionalAnswers.delegatesTo(candidateRepository));
        doThrow(new IllegalStateException("simulated crash"))
                .when(failingCandidates)
                .save(any());
        assertThatThrownBy(() -> promotionService(failingCandidates)
                        .persistPromotedReplayContest(
                                candidate, LocalDateTime.now(), channel(), message(), null, existingContest))
                .isInstanceOf(IllegalStateException.class);

        // Without the rollback, the contest would be reset to PENDING and its locked prediction deleted
        // while the candidate stayed PENDING, so a later promotion would score this combat a second time.
        CombatReplayContestEntity storedContest =
                contestRepository.findById(existingContest.getId()).orElseThrow();
        assertThat(storedContest.getReplayStatus()).isEqualTo(CombatContestReplayStatus.COMPLETED);
        assertThat(storedContest.getLeaderboardPostedAt()).isNotNull();
        assertThat(predictionRepository.findByContestId(existingContest.getId()))
                .isPresent();
        assertThat(candidateRepository.findById(candidate.getId()).orElseThrow().getPromotionStatus())
                .isEqualTo(CombatCandidatePromotionStatus.PENDING);
    }

    @Test
    void successfulPromotionCommitsTheContestAndTheCandidateTogether() {
        CombatCandidateEntity candidate = candidateRepository.save(unsavedCandidate());

        CombatReplayContestEntity contest = promotionService(candidateRepository)
                .persistPromotedReplayContest(candidate, LocalDateTime.now(), channel(), message(), null, null);

        assertThat(contestRepository.findByCandidateId(candidate.getId()))
                .get()
                .extracting(CombatReplayContestEntity::getId)
                .isEqualTo(contest.getId());
        assertThat(candidateRepository.findById(candidate.getId()).orElseThrow().getPromotionStatus())
                .isEqualTo(CombatCandidatePromotionStatus.PROMOTED);
    }

    private CombatReplayLeaderboardService leaderboardService(
            CombatReplaySideBetService sideBetService, CombatReplayLeaderboardEntryRepository entries) {
        return new CombatReplayLeaderboardService(
                settings,
                contestRepository,
                predictionRepository,
                entries,
                sideBetService,
                mock(CombatDoubleOrBustService.class),
                transactionTemplate);
    }

    private CombatReplaySideBetService sideBetService(CombatReplayLeaderboardEntryRepository entries) {
        return new CombatReplaySideBetService(
                settings,
                contestRepository,
                candidateRepository,
                sideBetRepository,
                entries,
                mock(CombatReplaySideBetPayoutService.class),
                mock(CombatReplaySideBetUiService.class),
                mock(CombatSideBetAvailabilityService.class));
    }

    private CombatReplayPromotionService promotionService(CombatCandidateRepository candidates) {
        return new CombatReplayPromotionService(
                settings,
                candidates,
                null,
                contestRepository,
                leaderboardService(mock(CombatReplaySideBetService.class), leaderboardEntryRepository),
                mock(CombatReplayExecutionService.class),
                mock(CombatReplayDiscordPostService.class),
                transactionTemplate);
    }

    private int predictorPoints() {
        return leaderboardEntryRepository
                .findByDiscordUserId(PREDICTOR_ID)
                .orElseThrow()
                .getTotalPoints();
    }

    private int bettorPoints() {
        return leaderboardEntryRepository
                .findByDiscordUserId(BETTOR_ID)
                .orElseThrow()
                .getTotalPoints();
    }

    private static CombatReplayContestEntity contestWithId(long id) {
        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        contest.setId(id);
        return contest;
    }

    private CombatReplayContestEntity completedContest(Long candidateId) {
        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        contest.setCandidateId(candidateId);
        contest.setReplayStatus(CombatContestReplayStatus.COMPLETED);
        contest.setNextEventSequence(9);
        contest.setLeaderboardPostedAt(LocalDateTime.now());
        return contestRepository.save(contest);
    }

    private static CombatCandidateEntity unsavedCandidate() {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setObservationId(1L);
        candidate.setStatus(CombatCandidateStatus.RESOLVED);
        candidate.setPromotionStatus(CombatCandidatePromotionStatus.PENDING);
        candidate.setNextEventSequence(1);
        candidate.setStartedAt(LocalDateTime.now());
        candidate.setGameName("pbd1000");
        candidate.setTilePosition("101");
        candidate.setAttackerFaction(ATTACKER);
        candidate.setDefenderFaction(DEFENDER);
        candidate.setWinnerFaction(ATTACKER);
        return candidate;
    }

    private static CombatReplayPredictionEntity lockedPrediction(Long contestId) {
        CombatReplayPredictionEntity prediction = new CombatReplayPredictionEntity();
        prediction.setContestId(contestId);
        prediction.setLockedAt(LocalDateTime.now());
        prediction.setAttackerPredictionCount(1);
        prediction.setDefenderPredictionCount(0);
        prediction.setAttackerPredictionsJson(
                "[{\"discordUserId\":\"" + PREDICTOR_ID + "\",\"discordUserName\":\"predictor\"}]");
        prediction.setDefenderPredictionsJson("[]");
        return prediction;
    }

    private static CombatReplayLeaderboardEntryEntity leaderboardEntry(String userId, int points) {
        CombatReplayLeaderboardEntryEntity entry = new CombatReplayLeaderboardEntryEntity();
        entry.setDiscordUserId(userId);
        entry.setDiscordUserName("bettor");
        entry.setTotalPoints(points);
        entry.setPredictionCount(0);
        entry.setCorrectPredictions(0);
        entry.setUpdatedAt(LocalDateTime.now());
        return entry;
    }

    private static CombatContestSideBetEntity moraleBoostBet(Long contestId, int profitPoints) {
        CombatContestSideBetEntity bet = new CombatContestSideBetEntity();
        bet.setContestId(contestId);
        bet.setDiscordUserId(BETTOR_ID);
        bet.setDiscordUserName("bettor");
        bet.setBetType(CombatSideBetType.MORALE_BOOST);
        bet.setTargetFaction(ATTACKER);
        bet.setOfferedProfitPoints(profitPoints);
        bet.setPlacedAt(LocalDateTime.now());
        return bet;
    }

    private static TextChannel channel() {
        TextChannel channel = mock(TextChannel.class);
        when(channel.getIdLong()).thenReturn(10L);
        return channel;
    }

    private static Message message() {
        Message message = mock(Message.class);
        when(message.getIdLong()).thenReturn(20L);
        return message;
    }

    @Configuration
    @ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        TransactionAutoConfiguration.class
    })
    @EntityScan(basePackageClasses = CombatCandidateEntity.class)
    @EnableJpaRepositories(basePackageClasses = CombatCandidateRepository.class)
    static class TestConfig {}
}
