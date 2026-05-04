package ti4.contest.replay.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import ti4.contest.replay.core.CombatReplayDecoys;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.core.LazaxCombatSupport;
import ti4.contest.replay.core.renderers.CombatReplayTileRenderer;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatObservationEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.house.hacan.CombatReplayHacanTradeConvoysService;
import ti4.contest.replay.house.mentak.CombatReplayMentakAbilityService;
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
    private static final Duration PUBLIC_PROMOTION_COOLDOWN = Duration.ofHours(2);
    private static final Duration PUBLIC_PROMOTION_COOLDOWN_GRACE = Duration.ofMinutes(5);

    private final CombatContestSettings settings;
    private final CombatCandidateRepository candidateRepository;
    private final CombatObservationRepository observationRepository;
    private final CombatReplayContestRepository replayContestRepository;
    private final CombatReplayLeaderboardService replayLeaderboardService;
    private final CombatReplayHacanTradeConvoysService hacanTradeConvoysService;
    private final CombatReplayMentakAbilityService mentakAbilityService;
    private final CombatReplayExecutionService replayExecutionService;
    private final CombatReplayDiscordPostService discordPostService;
    private Clock clock = Clock.systemDefaultZone();

    public void promoteBestCandidateIfDue() {
        if (!settings.getPromotion().isEnabled()) return;

        LocalDateTime now = LocalDateTime.now(clock);
        if (settings.getRuntime().isImmediatePromotionOnResolve()) {
            CombatCandidateEntity winner = selectPromotionWinner(findPromotionCandidates(now));
            if (winner == null) return;
            promoteCandidate(winner);
            return;
        }

        int maxPromotionsPerHour = settings.getPromotion().getMaxPromotionsPerHour();
        if (maxPromotionsPerHour <= 0) return;

        if (usesMentakPreviewFlow()) {
            if (settings.getRuntime().isDevMode()) {
                postMentakPreviewIfDue(now);
                promoteMentakPreviewedCandidateIfDue(now);
                return;
            }

            postProductionMentakPreviewIfDue(now);
            LocalDateTime publicPromotionWindow = now.truncatedTo(ChronoUnit.MINUTES);
            if (publicPromotionWindow.getMinute() != 0) return;
            if (!canPublicPromoteAt(publicPromotionWindow)) return;
            promoteMentakPreviewedCandidateIfDue(now);
            return;
        }

        LocalDateTime publicPromotionWindow = now.truncatedTo(ChronoUnit.MINUTES);
        if (publicPromotionWindow.getMinute() != 0) return;
        if (!canPublicPromoteAt(publicPromotionWindow)) return;

        CombatCandidateEntity winner = selectPromotionWinner(findPromotionCandidates(publicPromotionWindow));
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
                    "Candidate uses the old replay snapshot format");
        }

        CombatReplayContestEntity existingContest =
                replayContestRepository.findByCandidateId(candidateId).orElse(null);
        if (existingContest != null
                && existingContest.getReplayStatus() != CombatContestReplayStatus.COMPLETED
                && existingContest.getReplayStatus() != CombatContestReplayStatus.PREVIEW) {
            return CombatReplayContestLifecycleService.ForcePromoteResult.rejected(
                    "Candidate already has an active replay contest", existingContest);
        }

        CombatReplayContestEntity contest = promoteCandidate(candidate, existingContest);
        if (contest == null) {
            return CombatReplayContestLifecycleService.ForcePromoteResult.rejected("Promotion failed");
        }
        return CombatReplayContestLifecycleService.ForcePromoteResult.promoted(contest);
    }

    private List<CombatCandidateEntity> findPromotionCandidates(LocalDateTime now) {
        int configuredLookbackHours = settings.getPromotion().getCandidateLookbackHours();
        int maxLookbackHours =
                Math.max(configuredLookbackHours, CombatContestSettings.PROMOTION_LOOKBACK_FALLBACK_MAX_HOURS);
        for (int lookbackHours = configuredLookbackHours; lookbackHours <= maxLookbackHours; lookbackHours++) {
            List<CombatCandidateEntity> candidates = candidateRepository.findResolvedPromotionCandidates(
                    CombatCandidateStatus.RESOLVED,
                    CombatCandidatePromotionStatus.PENDING,
                    now.minusHours(lookbackHours));
            candidates = candidates.stream()
                    .filter(this::usesCurrentReplaySnapshotFormat)
                    .toList();
            if (!candidates.isEmpty()) return candidates;
        }
        return List.of();
    }

    private CombatReplayContestEntity promoteCandidate(CombatCandidateEntity winner) {
        return promoteCandidate(
                winner,
                replayContestRepository.findByCandidateId(winner.getId()).orElse(null));
    }

    private CombatReplayContestEntity promoteCandidate(
            CombatCandidateEntity winner, CombatReplayContestEntity existingContest) {
        CombatObservationEntity observation =
                observationRepository.findById(winner.getObservationId()).orElse(null);
        if (observation == null) return null;

        Game game = loadGame(winner.getGameName());
        if (game == null) return null;

        mentakAbilityService.resolveVoteIfNeeded(winner);

        String startSummaryText = snapshotStartSummaryText(winner);
        String message = LazaxCombatSupport.formatReplayAnnouncement(
                game, winner, discordPostService.getLazaxRoleMention(), startSummaryText);

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

    private void postMentakPreviewIfDue(LocalDateTime now) {
        List<CombatCandidateEntity> candidates = findPromotionCandidates(now);
        if (candidates.stream().anyMatch(candidate -> candidate.getMentakPreviewPostedAt() != null)) return;
        CombatCandidateEntity winner = selectPromotionWinner(candidates);
        if (winner == null) return;
        postMentakPreview(winner);
    }

    private void postProductionMentakPreviewIfDue(LocalDateTime now) {
        int previewLeadSeconds = settings.getHouseAbilities().getMentak().getPreviewLeadSeconds();
        LocalDateTime nextPublicPromotion = now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
        if (!isMentakPreviewWindow(now, nextPublicPromotion, previewLeadSeconds)) return;
        if (!canPublicPromoteAt(nextPublicPromotion)) return;
        postMentakPreviewIfDue(now);
    }

    private boolean isMentakPreviewWindow(
            LocalDateTime now, LocalDateTime publicPromotionWindow, int previewLeadSeconds) {
        long secondsUntilPromotion =
                Duration.between(now, publicPromotionWindow).toSeconds();
        return secondsUntilPromotion >= 0 && secondsUntilPromotion <= previewLeadSeconds;
    }

    private void promoteMentakPreviewedCandidateIfDue(LocalDateTime now) {
        CombatCandidateEntity winner = selectPromotionWinner(findPromotionCandidates(now).stream()
                .filter(candidate -> isMentakPreviewReadyForPublicPromotion(candidate, now))
                .toList());
        if (winner == null) return;
        promoteCandidate(winner);
    }

    private boolean usesMentakPreviewFlow() {
        return settings.isHousesEnabled()
                && settings.getHouseAbilities().getMentak().getPreviewLeadSeconds() > 0;
    }

    private boolean canPublicPromoteAt(LocalDateTime publicPromotionWindow) {
        if (publicPromotionWindow == null) return false;
        if (!settings.getRuntime().isDevMode()
                && replayContestRepository.countByPostedAtGreaterThanEqual(
                                publicPromotionCooldownCutoff(publicPromotionWindow))
                        > 0) {
            return false;
        }
        return replayContestRepository.countByPostedAtGreaterThanEqual(
                        publicPromotionWindow.truncatedTo(ChronoUnit.HOURS))
                < settings.getPromotion().getMaxPromotionsPerHour();
    }

    private boolean isMentakPreviewReadyForPublicPromotion(CombatCandidateEntity candidate, LocalDateTime now) {
        if (candidate == null || candidate.getMentakPreviewPostedAt() == null) return false;
        int previewLeadSeconds = settings.getHouseAbilities().getMentak().getPreviewLeadSeconds();
        return !candidate
                .getMentakPreviewPostedAt()
                .plusSeconds(previewLeadSeconds)
                .isAfter(now);
    }

    private void postMentakPreview(CombatCandidateEntity candidate) {
        CombatObservationEntity observation =
                observationRepository.findById(candidate.getObservationId()).orElse(null);
        Game game = loadGame(candidate.getGameName());
        TextChannel channel = discordPostService.houseChannel(CombatReplayHouse.MENTAK);
        if (observation == null || game == null || channel == null) return;

        CombatReplayContestEntity previewContest = createPreviewContest(candidate);
        lockPreviousContestTradeConvoys(previewContest);

        String startSummaryText = snapshotStartSummaryText(candidate);
        String message = LazaxCombatSupport.formatReplayAnnouncement(
                game, candidate, discordPostService.getHouseRoleMention(CombatReplayHouse.MENTAK), startSummaryText);
        try {
            discordPostService.postPromotionMessage(channel, message, game, candidate);
            mentakAbilityService.postDecoyButtons(channel, candidate);
            candidate.setMentakPreviewPostedAt(LocalDateTime.now(clock));
            candidateRepository.save(candidate);
        } catch (Exception e) {
            BotLogger.error("Failed to post Mentak combat replay preview.", e);
        }
    }

    void setClock(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    static LocalDateTime publicPromotionCooldownCutoff(LocalDateTime now) {
        return now.minus(PUBLIC_PROMOTION_COOLDOWN).plus(PUBLIC_PROMOTION_COOLDOWN_GRACE);
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
        CombatReplayDecoys.applyToTile(
                snapshotGame, candidate.getTilePosition(), CombatReplayDecoys.read(candidate.getReplayAbilitiesJson()));

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

    private CombatCandidateEntity selectPromotionWinner(List<CombatCandidateEntity> candidates) {
        Map<Long, Double> jointScoresByObservationId = loadJointScores(candidates);
        CombatCandidateEntity winner = null;
        Comparator<CombatCandidateEntity> comparator = candidateComparator(jointScoresByObservationId);
        List<CombatCandidateEntity> previewedCandidates = candidates.stream()
                .filter(candidate -> candidate.getMentakPreviewPostedAt() != null)
                .toList();
        if (!previewedCandidates.isEmpty()) candidates = previewedCandidates;
        for (CombatCandidateEntity candidate : candidates) {
            if (candidate.getResolvedAt() == null) continue;
            if (winner == null || comparator.compare(candidate, winner) > 0) winner = candidate;
        }
        return winner;
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
        lockPreviousContestTradeConvoys(savedContest);

        winner.setPromotionStatus(CombatCandidatePromotionStatus.PROMOTED);
        winner.setPromotedAt(promotedAt);
        candidateRepository.save(winner);
        return savedContest;
    }

    private void lockPreviousContestTradeConvoys(CombatReplayContestEntity newContest) {
        if (newContest == null || newContest.getId() == null) return;
        CombatReplayContestEntity previousContest = replayContestRepository
                .findFirstByIdLessThanOrderByIdDesc(newContest.getId())
                .orElse(null);
        if (previousContest == null || previousContest.getCandidateId() == null) return;
        if (!isPostCombat(previousContest)) return;
        CombatCandidateEntity previousCandidate =
                candidateRepository.findById(previousContest.getCandidateId()).orElse(null);
        if (previousCandidate == null) return;
        hacanTradeConvoysService.lockTradeConvoysIfNeeded(previousContest, previousCandidate);
        replayLeaderboardService.computeAndPersistHouseScoresFromFacts(previousCandidate, previousContest);
    }

    private boolean isPostCombat(CombatReplayContestEntity contest) {
        return contest != null && (contest.getReplayCompletedAt() != null || contest.getLeaderboardPostedAt() != null);
    }

    private CombatReplayContestEntity createPreviewContest(CombatCandidateEntity candidate) {
        CombatReplayContestEntity existingContest =
                replayContestRepository.findByCandidateId(candidate.getId()).orElse(null);
        if (existingContest != null) return existingContest;

        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        contest.setCandidateId(candidate.getId());
        contest.setReplayStatus(CombatContestReplayStatus.PREVIEW);
        contest.setNextEventSequence(1);
        contest.setSideBetPayoutModel(CombatReplaySideBetPayoutService.ODDS_V1);
        return replayContestRepository.saveAndFlush(contest);
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
        existingContest.setSideBetMarketPostedAt(null);
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
        contest.setSideBetPayoutModel(CombatReplaySideBetPayoutService.ODDS_V1);
        return contest;
    }

    private void configureReplayContest(
            CombatReplayContestEntity contest,
            CombatCandidateEntity winner,
            long publicChannelId,
            long publicMessageId,
            Long publicThreadId,
            LocalDateTime promotedAt) {
        LocalDateTime replayStartAt = promotedAt.plusSeconds(replayStartDelaySeconds());
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

    private int replayStartDelaySeconds() {
        if (!settings.isHousesEnabled()) return settings.getReplayExecution().getStartDelaySeconds();
        return settings.getReplayExecution().getDiscussionWindowSeconds()
                + settings.getReplayExecution().getSideBetWindowSeconds();
    }

    private Game loadGame(String gameName) {
        var managedGame = GameManager.getManagedGame(gameName);
        return managedGame == null ? null : managedGame.getGame();
    }
}
