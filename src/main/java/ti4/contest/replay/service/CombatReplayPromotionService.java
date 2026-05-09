package ti4.contest.replay.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatCandidatePromotionStatus;
import ti4.contest.replay.core.CombatCandidateStatus;
import ti4.contest.replay.core.CombatContestReplayStatus;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.LazaxCombatSupport;
import ti4.contest.replay.core.renderers.CombatReplayTileRenderer;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatObservationEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatObservationRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;

@Service
@RequiredArgsConstructor
public class CombatReplayPromotionService {

    private static final String PROMOTION_DISABLED_REASON = "Candidate-to-contest promotion is disabled.";
    private static final ZoneId CENTRAL_TIME = ZoneId.of("America/Chicago");
    private static final Set<CombatContestReplayStatus> ACTIVE_REPLAY_STATUSES =
            Set.of(CombatContestReplayStatus.PENDING, CombatContestReplayStatus.REPLAYING);

    private final CombatContestSettings settings;
    private final CombatCandidateRepository candidateRepository;
    private final CombatObservationRepository observationRepository;
    private final CombatReplayContestRepository replayContestRepository;
    private final CombatReplayLeaderboardService replayLeaderboardService;
    private final CombatReplayExecutionService replayExecutionService;
    private final CombatReplayDiscordPostService discordPostService;
    private Clock clock = Clock.systemDefaultZone();

    public void promoteBestCandidateIfDue() {
        if (!settings.getPromotion().isEnabled()) return;

        if (settings.getRuntime().isImmediatePromotionOnResolve()) {
            CombatCandidateEntity winner = selectPromotionWinner(findPromotionCandidates());
            if (winner == null) return;
            promoteCandidate(winner);
            return;
        }

        if (replayContestRepository.existsByReplayStatusIn(ACTIVE_REPLAY_STATUSES)) return;

        CombatCandidateEntity winner = selectPromotionWinner(findPromotionCandidates());
        if (winner == null) return;
        promoteCandidate(winner);
    }

    public CombatReplayContestLifecycleService.ForcePromoteResult forcePromoteCandidate(Long candidateId) {
        if (candidateId == null)
            return CombatReplayContestLifecycleService.ForcePromoteResult.rejected("candidateId is required");
        if (!settings.getPromotion().isEnabled()) {
            return CombatReplayContestLifecycleService.ForcePromoteResult.rejected(PROMOTION_DISABLED_REASON);
        }

        CombatCandidateEntity candidate =
                candidateRepository.findById(candidateId).orElse(null);
        if (candidate == null)
            return CombatReplayContestLifecycleService.ForcePromoteResult.rejected("Candidate not found");
        if (candidate.getStatus() != CombatCandidateStatus.RESOLVED) {
            return CombatReplayContestLifecycleService.ForcePromoteResult.rejected(
                    "Candidate must be RESOLVED before promotion");
        }
        if (candidate.getPromotionStatus() != CombatCandidatePromotionStatus.PENDING) {
            return CombatReplayContestLifecycleService.ForcePromoteResult.rejected(
                    "Candidate is not eligible for promotion");
        }
        if (!usesCurrentReplaySnapshotFormat(candidate)) {
            return CombatReplayContestLifecycleService.ForcePromoteResult.rejected(
                    "Candidate is missing a renderable replay snapshot");
        }
        if (expireIfDiscordantStars(candidate)) {
            return CombatReplayContestLifecycleService.ForcePromoteResult.rejected(
                    "Discordant Stars games are not eligible for promotion.");
        }

        CombatReplayContestEntity existingContest =
                replayContestRepository.findByCandidateId(candidateId).orElse(null);
        if (existingContest != null && existingContest.getReplayStatus() != CombatContestReplayStatus.COMPLETED) {
            return CombatReplayContestLifecycleService.ForcePromoteResult.rejected(
                    "Candidate already has an active replay contest", existingContest);
        }

        CombatReplayContestEntity contest = promoteCandidate(candidate, existingContest);
        if (contest == null) {
            return CombatReplayContestLifecycleService.ForcePromoteResult.rejected("Promotion failed");
        }
        return CombatReplayContestLifecycleService.ForcePromoteResult.promoted(contest);
    }

    private List<CombatCandidateEntity> findPromotionCandidates() {
        List<CombatCandidateEntity> candidates = candidateRepository.findResolvedPromotionCandidates(
                CombatCandidateStatus.RESOLVED, CombatCandidatePromotionStatus.PENDING);
        return candidates.stream()
                .filter(candidate -> !expireIfDiscordantStars(candidate))
                .filter(this::usesCurrentReplaySnapshotFormat)
                .toList();
    }

    private CombatReplayContestEntity promoteCandidate(CombatCandidateEntity winner) {
        return promoteCandidate(
                winner,
                replayContestRepository.findByCandidateId(winner.getId()).orElse(null));
    }

    private CombatReplayContestEntity promoteCandidate(
            CombatCandidateEntity winner, CombatReplayContestEntity existingContest) {
        if (expireIfDiscordantStars(winner)) return null;

        Game game = loadGame(winner.getGameName());
        if (game == null) return null;

        String startSummaryText = snapshotStartSummaryText(winner);
        String message = LazaxCombatSupport.formatReplayAnnouncement(game, winner, "", startSummaryText);

        TextChannel contestChannel = discordPostService.getContestChannel();
        if (contestChannel == null) return null;

        try {
            LocalDateTime promotedAt = LocalDateTime.now(clock);
            Message posted = discordPostService.postPromotionMessage(contestChannel, message, game, winner);
            ThreadChannel thread = discordPostService.createReplayThread(posted, winner);
            CombatReplayContestEntity contest =
                    persistPromotedReplayContest(winner, promotedAt, contestChannel, posted, thread, existingContest);
            addPredictionReactions(game, winner, posted);
            replayExecutionService.postPromotionContext(thread != null ? thread : contestChannel, contest, winner);
            return contest;
        } catch (Exception e) {
            BotLogger.error("Replay contest promotion failed.", e);
            return null;
        }
    }

    void setClock(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    String snapshotStartSummaryText(CombatCandidateEntity candidate) {
        if (StringUtils.isBlank(candidate.getInitialRenderSnapshotJson())) return null;

        Game snapshotGame = CombatReplayTileRenderer.render(
                candidate.getInitialRenderSnapshotJson(), candidate.getInitialRenderSnapshotJson());
        if (snapshotGame == null) return null;
        Player attacker = snapshotGame.getPlayerFromColorOrFaction(candidate.getAttackerFaction());
        Player defender = snapshotGame.getPlayerFromColorOrFaction(candidate.getDefenderFaction());
        Tile tile = snapshotGame.getTileByPosition(candidate.getTilePosition());
        if (attacker == null || defender == null || tile == null) return null;
        LazaxCombatSupport.SpaceCombatSnapshot snapshot =
                LazaxCombatSupport.buildSpaceCombatSnapshot(snapshotGame, attacker, defender, tile);
        return snapshot == null ? null : snapshot.replaySummaryText();
    }

    private Comparator<CombatCandidateEntity> candidateComparator(Map<Long, Double> jointScoresByObservationId) {
        return Comparator.comparing(this::getPromotionScore)
                .thenComparing(candidate -> jointScoresByObservationId.getOrDefault(candidate.getObservationId(), 0.0))
                .thenComparing(CombatCandidateEntity::getResolvedAt, Comparator.reverseOrder())
                .thenComparing(CombatCandidateEntity::getId, Comparator.reverseOrder());
    }

    private double getPromotionScore(CombatCandidateEntity candidate) {
        return candidate.getPromotionScore() == null ? 0.0 : candidate.getPromotionScore();
    }

    private boolean usesCurrentReplaySnapshotFormat(CombatCandidateEntity candidate) {
        return candidate != null && CombatReplayTileRenderer.canRender(candidate.getInitialRenderSnapshotJson());
    }

    private boolean expireIfDiscordantStars(CombatCandidateEntity candidate) {
        if (candidate == null || candidate.getPromotionStatus() != CombatCandidatePromotionStatus.PENDING) return false;
        if (StringUtils.isBlank(candidate.getGameName())) return false;
        Game game = loadGame(candidate.getGameName());
        if (game == null || !game.isDiscordantStarsMode()) return false;
        candidate.setPromotionStatus(CombatCandidatePromotionStatus.EXPIRED);
        candidate.setCancellationReason("Ineligible for promotion because Discordant Stars games are excluded.");
        candidateRepository.save(candidate);
        return true;
    }

    private CombatCandidateEntity selectPromotionWinner(List<CombatCandidateEntity> candidates) {
        List<CombatCandidateEntity> rankedCandidates = rankPromotionCandidates(candidates);
        return rankedCandidates.isEmpty() ? null : rankedCandidates.getFirst();
    }

    private List<CombatCandidateEntity> rankPromotionCandidates(List<CombatCandidateEntity> candidates) {
        Map<Long, Double> jointScoresByObservationId = loadJointScores(candidates);
        Comparator<CombatCandidateEntity> comparator = candidateComparator(jointScoresByObservationId);
        return candidates.stream()
                .filter(candidate -> candidate.getResolvedAt() != null)
                .sorted(comparator.reversed())
                .toList();
    }

    private Map<Long, Double> loadJointScores(List<CombatCandidateEntity> candidates) {
        List<Long> observationIds = new ArrayList<>(candidates.size());
        for (CombatCandidateEntity candidate : candidates) {
            observationIds.add(candidate.getObservationId());
        }

        Map<Long, Double> jointScoresByObservationId = new HashMap<>();
        for (CombatObservationEntity observation : observationRepository.findAllById(observationIds)) {
            jointScoresByObservationId.put(observation.getId(), observation.getJointScore());
        }
        return jointScoresByObservationId;
    }

    private void addPredictionReactions(Game game, CombatCandidateEntity candidate, Message message) {
        addReaction(game.getPlayerFromColorOrFaction(candidate.getAttackerFaction()), message);
        addReaction(game.getPlayerFromColorOrFaction(candidate.getDefenderFaction()), message);
    }

    private void addReaction(Player player, Message message) {
        if (player == null) return;
        try {
            message.addReaction(Emoji.fromFormatted(player.getFactionEmoji())).queue(null, BotLogger::catchRestError);
        } catch (Exception e) {
            BotLogger.error("Failed to parse replay contest prediction reaction emoji.", e);
        }
    }

    private CombatReplayContestEntity persistPromotedReplayContest(
            CombatCandidateEntity winner,
            LocalDateTime promotedAt,
            TextChannel contestChannel,
            Message posted,
            ThreadChannel thread,
            CombatReplayContestEntity existingContest) {
        long publicChannelId = contestChannel.getIdLong();
        long publicMessageId = posted.getIdLong();
        Long publicThreadId = thread == null ? null : thread.getIdLong();
        CombatReplayContestEntity contest = existingContest == null
                ? buildReplayContest(winner, publicChannelId, publicMessageId, publicThreadId, promotedAt)
                : resetReplayContest(
                        existingContest, winner, publicChannelId, publicMessageId, publicThreadId, promotedAt);
        CombatReplayContestEntity savedContest = replayContestRepository.save(contest);
        winner.setPromotionStatus(CombatCandidatePromotionStatus.PROMOTED);
        winner.setPromotedAt(promotedAt);
        candidateRepository.save(winner);
        return savedContest;
    }

    private CombatReplayContestEntity resetReplayContest(
            CombatReplayContestEntity existingContest,
            CombatCandidateEntity winner,
            long publicChannelId,
            long publicMessageId,
            Long publicThreadId,
            LocalDateTime promotedAt) {
        configureReplayContest(existingContest, winner, publicChannelId, publicMessageId, publicThreadId, promotedAt);
        existingContest.setReplayCompletedAt(null);
        existingContest.setPreReplayContextPostedAt(null);
        existingContest.setLeaderboardPostedAt(null);
        existingContest.setSideBetSummaryMessageId(null);
        existingContest.setSideBetButtonsPostedAt(null);
        existingContest.setReplayError(null);
        replayLeaderboardService.clearLockedPredictions(existingContest.getId());
        return existingContest;
    }

    private CombatReplayContestEntity buildReplayContest(
            CombatCandidateEntity winner,
            long publicChannelId,
            long publicMessageId,
            Long publicThreadId,
            LocalDateTime promotedAt) {
        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        configureReplayContest(contest, winner, publicChannelId, publicMessageId, publicThreadId, promotedAt);
        return contest;
    }

    private void configureReplayContest(
            CombatReplayContestEntity contest,
            CombatCandidateEntity winner,
            long publicChannelId,
            long publicMessageId,
            Long publicThreadId,
            LocalDateTime promotedAt) {
        LocalDateTime replayStartAt = computeReplayStartAt(promotedAt);
        contest.setCandidateId(winner.getId());
        contest.setPostedAt(promotedAt);
        contest.setPublicChannelId(publicChannelId);
        contest.setPublicMessageId(publicMessageId);
        contest.setPublicThreadId(publicThreadId);
        contest.setReplayStatus(CombatContestReplayStatus.PENDING);
        contest.setReplayStartAt(replayStartAt);
        contest.setNextReplayAt(replayStartAt);
        contest.setNextEventSequence(1);
    }

    private LocalDateTime computeReplayStartAt(LocalDateTime promotedAt) {
        int dailyLockHourCentral = settings.getReplayExecution().getDailyLockHourCentral();
        if (dailyLockHourCentral < 0) {
            return promotedAt.plusSeconds(settings.getReplayExecution().getStartDelaySeconds());
        }

        ZonedDateTime promotedCentral = promotedAt.atZone(clock.getZone()).withZoneSameInstant(CENTRAL_TIME);
        ZonedDateTime replayStartCentral = promotedCentral
                .withHour(dailyLockHourCentral)
                .withMinute(settings.getReplayExecution().getDailyLockMinuteCentral())
                .withSecond(0)
                .withNano(0);
        if (!replayStartCentral.isAfter(promotedCentral)) {
            replayStartCentral = replayStartCentral.plusDays(1);
        }
        return replayStartCentral.withZoneSameInstant(clock.getZone()).toLocalDateTime();
    }

    private Game loadGame(String gameName) {
        var managedGame = GameManager.getManagedGame(gameName);
        return managedGame == null ? null : managedGame.getGame();
    }
}
