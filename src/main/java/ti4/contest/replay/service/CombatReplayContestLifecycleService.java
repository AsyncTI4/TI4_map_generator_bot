package ti4.contest.replay.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.*;
import ti4.contest.replay.dispatch.ReplayDispatchPayload;
import ti4.contest.replay.dispatch.ReplayDispatchSerializer;
import ti4.contest.replay.entities.*;
import ti4.contest.replay.repository.*;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.GameManager;
import ti4.helpers.RandomHelper;
import ti4.image.TileGenerator;
import ti4.logging.BotLogger;
import ti4.spring.service.contest.CombatContestService;

@Service
@RequiredArgsConstructor
/**
 * Promotes resolved candidates into replay contests and advances replay execution over time.
 */
public class CombatReplayContestLifecycleService {

    private static final String CONTEST_CHANNEL_NAME_V1 = "lazax-war-archives-dev";
    private static final String CONTEST_CHANNEL_NAME_V2 = "lazax-war-archives";
    private static final long SHADOW_DISCORD_ID = 0L;
    private static final List<String> PREDICTION_LOCK_TITLES = List.of(
            "The Wagers Open",
            "The Archives Open",
            "Predictions Are Open",
            "The War Ledger Opens",
            "The Betting Hall Opens",
            "Call for Predictions",
            "The Scribes Await",
            "Cast Your Wager");
    private static final List<String> PREDICTION_LOCK_SUBTITLES = List.of(
            "_The Lazax recorders now accept predictions for the coming clash._",
            "_The archives invite your judgment before the battle unfolds._",
            "_The war ledger opens to all who would call the victor._",
            "_The betting hall stirs as a new contest enters the record._",
            "_The scribes stand ready to record your chosen champion._",
            "_A new combat has been entered into the annals; predictions are welcome._",
            "_The archives seek your verdict before the first volleys are fired._",
            "_The next battle stands before the record; declare your pick._",
            "_The Lazax ledgers are open to those bold enough to choose a side._",
            "_Another clash enters the chronicles; place your wager in the record._");

    private final CombatContestSettings settings;
    private final CombatCandidateRepository candidateRepository;
    private final CombatObservationRepository observationRepository;
    private final CombatReplayContestRepository replayContestRepository;
    private final CombatCandidateEventRepository candidateEventRepository;
    private final CombatReplayLeaderboardService replayLeaderboardService;
    private final ReplayDispatchSerializer payloadSerializer;

    public void promoteBestCandidateIfDue() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        int maxPromotionsPerHour = settings.getPromotion().getMaxPromotionsPerHour();
        if (maxPromotionsPerHour <= 0) return;
        if (replayContestRepository.countByPostedAtGreaterThanEqual(now.truncatedTo(ChronoUnit.HOURS))
                >= maxPromotionsPerHour) {
            return;
        }

        List<CombatCandidateEntity> candidates = findPromotionCandidates(now);
        CombatCandidateEntity winner = selectPromotionWinner(candidates);
        if (winner == null) return;
        promoteCandidate(winner);
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
            if (!candidates.isEmpty()) {
                return candidates;
            }
        }
        return List.of();
    }

    public ForcePromoteResult forcePromoteCandidate(Long candidateId) {
        if (candidateId == null) return ForcePromoteResult.rejected("candidateId is required");

        CombatCandidateEntity candidate =
                candidateRepository.findById(candidateId).orElse(null);
        if (candidate == null) return ForcePromoteResult.rejected("Candidate not found");
        if (candidate.getStatus() != CombatCandidateStatus.RESOLVED) {
            return ForcePromoteResult.rejected("Candidate must be RESOLVED before promotion");
        }

        CombatReplayContestEntity existingContest =
                replayContestRepository.findByCandidateId(candidateId).orElse(null);
        if (existingContest != null && existingContest.getReplayStatus() != CombatContestReplayStatus.COMPLETED) {
            return ForcePromoteResult.rejected("Candidate already has an active replay contest", existingContest);
        }

        CombatReplayContestEntity contest = promoteCandidate(candidate, existingContest);
        if (contest == null) {
            return ForcePromoteResult.rejected("Promotion failed");
        }
        return ForcePromoteResult.promoted(contest);
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

        String startSummaryText = candidateEventRepository
                .findByCandidateIdAndSequenceNumber(winner.getId(), 1)
                .map(CombatCandidateEventEntity::getSummaryText)
                .orElse(null);
        String message = LazaxCombatSupport.formatReplayAnnouncement(
                game, observation, winner, getLazaxRoleMention(), startSummaryText);
        if (!settings.getRuntime().isDiscordPostingEnabled()) {
            return createShadowReplayContest(game, observation, winner, message, existingContest);
        }

        TextChannel contestChannel = getContestChannel();
        if (contestChannel == null) return null;

        try {
            LocalDateTime promotedAt = LocalDateTime.now();
            Message posted = postPromotionMessage(contestChannel, message, game, winner);
            addPredictionReactions(game, winner, posted);
            ThreadChannel thread = createReplayThread(posted, winner);
            CombatReplayContestEntity contest = persistPromotedReplayContest(
                    game, observation, winner, message, promotedAt, contestChannel, posted, thread, existingContest);
            postPromotionContext(thread != null ? thread : contestChannel, contest, winner);
            return contest;
        } catch (Exception e) {
            BotLogger.error("Replay contest promotion failed.", e);
            return null;
        }
    }

    @SneakyThrows
    private Message postPromotionMessage(
            TextChannel contestChannel, String message, Game game, CombatCandidateEntity candidate) {
        return sendTileRenderMessage(
                contestChannel,
                message,
                List.of(),
                restoreReplayGame(
                        candidate.getInitialRenderSnapshotJson(), game, candidate, candidate.getTilePosition()),
                candidate.getTilePosition());
    }

    public void runReplayTick() {
        if (!settings.getRuntime().isDiscordPostingEnabled()) return;

        List<CombatReplayContestEntity> dueContests =
                replayContestRepository.findByReplayStatusInAndNextReplayAtLessThanEqualOrderByNextReplayAtAsc(
                        Set.of(CombatContestReplayStatus.PENDING, CombatContestReplayStatus.REPLAYING),
                        LocalDateTime.now());
        for (CombatReplayContestEntity contest : dueContests) {
            replaySingleContest(contest);
        }
    }

    private void replaySingleContest(CombatReplayContestEntity contest) {
        CombatCandidateEventEntity event = candidateEventRepository
                .findByCandidateIdAndSequenceNumber(contest.getCandidateId(), contest.getNextEventSequence())
                .orElse(null);
        if (event == null) {
            completeReplayContest(contest);
            return;
        }

        CombatCandidateEntity candidate = loadCandidate(contest.getCandidateId());
        Game game = candidate == null ? null : loadGame(candidate.getGameName());
        try {
            MessageChannel channel = getContestThreadOrChannel(contest);
            if (channel == null) {
                rescheduleReplay(contest, "Replay channel unavailable.");
                return;
            }
            if (contest.getReplayStatus() == CombatContestReplayStatus.PENDING && candidate != null && game != null) {
                announcePreReplayContextIfNeeded(channel, contest, candidate);
                replayLeaderboardService.lockPredictionsAtReplayStart(game, contest, candidate);
                replayLeaderboardService.announceLockedPredictionsIfNeeded(channel, game, contest, candidate);
            }
            if (event.getEventType() != CombatCandidateEventType.START) {
                postReplayEvent(channel, game, candidate, event);
            }
            CombatCandidateEventEntity nextEvent = candidateEventRepository
                    .findByCandidateIdAndSequenceNumber(contest.getCandidateId(), contest.getNextEventSequence() + 1)
                    .orElse(null);
            contest.setNextEventSequence(contest.getNextEventSequence() + 1);
            contest.setReplayError(null);
            if (nextEvent == null) {
                completeReplayContest(contest);
                return;
            }
            contest.setReplayStatus(CombatContestReplayStatus.REPLAYING);
            contest.setNextReplayAt(computeConfiguredNextReplayAt(LocalDateTime.now(), event, nextEvent));
        } catch (Exception e) {
            rescheduleReplay(contest, e.getMessage());
            return;
        }
        replayContestRepository.save(contest);
    }

    private void announcePreReplayContextIfNeeded(
            MessageChannel channel, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        if (contest.getPreReplayContextPostedAt() != null) return;

        String message = candidate.getPreReplayContextText();
        if (message != null && !message.isBlank()) {
            channel.sendMessage(message).complete();
        }
        contest.setPreReplayContextPostedAt(LocalDateTime.now());
        replayContestRepository.save(contest);
    }

    private void announcePredictionLockCountdown(MessageChannel channel) {
        int startDelayMinutes = settings.getReplayExecution().getStartDelayMinutes();
        String title = RandomHelper.pickRandomFromList(PREDICTION_LOCK_TITLES);
        String subtitle = RandomHelper.pickRandomFromList(PREDICTION_LOCK_SUBTITLES);
        String message = startDelayMinutes <= 0
                ? "## " + title + "\n" + subtitle + "\nVoting is now open. The combat begins immediately."
                : "## " + title + "\n" + subtitle + "\nVoting is now open for **" + startDelayMinutes + "** "
                        + (startDelayMinutes == 1 ? "minute" : "minutes") + ".";
        channel.sendMessage(message).complete();
    }

    private void postPromotionContext(
            MessageChannel replayChannel, CombatReplayContestEntity contest, CombatCandidateEntity winner) {
        try {
            announcePreReplayContextIfNeeded(replayChannel, contest, winner);
            announcePredictionLockCountdown(replayChannel);
        } catch (Exception e) {
            BotLogger.error("Failed to post replay context at promotion.", e);
        }
    }

    private void completeReplayContest(CombatReplayContestEntity contest) {
        CombatCandidateEntity candidate = loadCandidate(contest.getCandidateId());
        Game game = candidate == null ? null : loadGame(candidate.getGameName());
        if (candidate != null) {
            replayLeaderboardService.finalizeReplayLeaderboardContest(game, contest, candidate);
        }

        contest.setReplayStatus(CombatContestReplayStatus.COMPLETED);
        contest.setReplayCompletedAt(LocalDateTime.now());
        contest.setReplayError(null);
        replayContestRepository.save(contest);
    }

    private void rescheduleReplay(CombatReplayContestEntity contest, String error) {
        contest.setReplayStatus(CombatContestReplayStatus.REPLAYING);
        contest.setReplayError(error);
        contest.setNextReplayAt(
                LocalDateTime.now().plusSeconds(settings.getReplayExecution().getReplayIntervalSeconds()));
        replayContestRepository.save(contest);
    }

    static LocalDateTime computeNextReplayAt(
            LocalDateTime replayedAt, CombatCandidateEventEntity currentEvent, CombatCandidateEventEntity nextEvent) {
        return computeNextReplayAt(replayedAt, currentEvent, nextEvent, Duration.ofMinutes(1));
    }

    static LocalDateTime computeNextReplayAt(
            LocalDateTime replayedAt,
            CombatCandidateEventEntity currentEvent,
            CombatCandidateEventEntity nextEvent,
            Duration maxReplayEventGap) {
        if (replayedAt == null
                || currentEvent == null
                || nextEvent == null
                || currentEvent.getOccurredAt() == null
                || nextEvent.getOccurredAt() == null) {
            return replayedAt;
        }

        Duration originalGap = Duration.between(currentEvent.getOccurredAt(), nextEvent.getOccurredAt());
        if (originalGap.isNegative() || originalGap.isZero()) {
            return replayedAt;
        }

        Duration replayGap = originalGap.compareTo(maxReplayEventGap) > 0 ? maxReplayEventGap : originalGap;
        return replayedAt.plus(replayGap);
    }

    private LocalDateTime computeConfiguredNextReplayAt(
            LocalDateTime replayedAt, CombatCandidateEventEntity currentEvent, CombatCandidateEventEntity nextEvent) {
        return computeNextReplayAt(
                replayedAt,
                currentEvent,
                nextEvent,
                Duration.ofSeconds(settings.getReplayExecution().getMaxEventGapSeconds()));
    }

    private Comparator<CombatCandidateEntity> candidateComparator(Map<Long, Double> jointScoresByObservationId) {
        return Comparator.<CombatCandidateEntity, Double>comparing(this::getPromotionScore)
                .thenComparing(candidate -> jointScoresByObservationId.getOrDefault(candidate.getObservationId(), 0.0))
                .thenComparing(CombatCandidateEntity::getResolvedAt, Comparator.reverseOrder())
                .thenComparing(CombatCandidateEntity::getId, Comparator.reverseOrder());
    }

    private double getPromotionScore(CombatCandidateEntity candidate) {
        return candidate.getPromotionScore() == null ? 0.0 : candidate.getPromotionScore();
    }

    private CombatCandidateEntity selectPromotionWinner(List<CombatCandidateEntity> candidates) {
        Map<Long, Double> jointScoresByObservationId = loadJointScores(candidates);
        return candidates.stream()
                .filter(candidate -> candidate.getResolvedAt() != null)
                .max(candidateComparator(jointScoresByObservationId))
                .orElse(null);
    }

    private Map<Long, Double> loadJointScores(List<CombatCandidateEntity> candidates) {
        List<Long> observationIds =
                candidates.stream().map(CombatCandidateEntity::getObservationId).toList();
        return observationRepository.findAllById(observationIds).stream()
                .collect(Collectors.toMap(CombatObservationEntity::getId, CombatObservationEntity::getJointScore));
    }

    private ThreadChannel createReplayThread(Message posted, CombatCandidateEntity winner) {
        return posted.createThreadChannel(buildReplayThreadName(winner))
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS)
                .complete();
    }

    private String buildReplayThreadName(CombatCandidateEntity candidate) {
        String attacker = normalizeThreadNamePart(candidate.getAttackerFaction());
        String defender = normalizeThreadNamePart(candidate.getDefenderFaction());
        String tilePosition = StringUtils.defaultIfBlank(candidate.getTilePosition(), "unknown");
        String candidateId =
                candidate.getId() == null ? "unknown" : candidate.getId().toString();
        return "combat-archive-c" + candidateId + "-t" + tilePosition + "-" + attacker + "-v-" + defender;
    }

    private String normalizeThreadNamePart(String value) {
        String normalized = StringUtils.defaultIfBlank(value, "unknown")
                .trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (normalized.isBlank()) return "unknown";
        return StringUtils.abbreviate(normalized, 18);
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

    private CombatReplayContestEntity createShadowReplayContest(
            Game game,
            CombatObservationEntity observation,
            CombatCandidateEntity winner,
            String message,
            CombatReplayContestEntity existingContest) {
        BotLogger.info("Combat replay shadow mode: promotion suppressed for candidate " + winner.getId());
        return persistPromotedReplayContest(
                game, observation, winner, message, LocalDateTime.now(), null, null, null, existingContest);
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
            Game game,
            CombatObservationEntity observation,
            CombatCandidateEntity winner,
            String message,
            LocalDateTime promotedAt,
            TextChannel contestChannel,
            Message posted,
            ThreadChannel thread,
            CombatReplayContestEntity existingContest) {
        long publicChannelId = contestChannel == null ? SHADOW_DISCORD_ID : contestChannel.getIdLong();
        long publicMessageId = posted == null ? SHADOW_DISCORD_ID : posted.getIdLong();
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
        existingContest.setReplayError(null);
        replayLeaderboardService.clearLockedPredictions(existingContest.getId());
        return existingContest;
    }

    private void configureReplayContest(
            CombatReplayContestEntity contest,
            CombatCandidateEntity winner,
            long publicChannelId,
            long publicMessageId,
            Long publicThreadId,
            LocalDateTime promotedAt) {
        LocalDateTime replayStartAt =
                promotedAt.plusMinutes(settings.getReplayExecution().getStartDelayMinutes());
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

    private MessageChannel getContestThreadOrChannel(CombatReplayContestEntity contest) {
        if (JdaService.guildPrimary == null) return null;
        if (contest.getPublicThreadId() != null) {
            ThreadChannel thread = JdaService.guildPrimary.getThreadChannelById(contest.getPublicThreadId());
            if (thread != null) return thread;
        }
        return JdaService.guildPrimary.getTextChannelById(contest.getPublicChannelId());
    }

    private TextChannel getContestChannel() {
        if (JdaService.guildPrimary == null) return null;
        return JdaService.guildPrimary.getTextChannelsByName(getContestChannelName(), true).stream()
                .findFirst()
                .orElse(null);
    }

    private String getContestChannelName() {
        return isReplayV2Enabled() ? CONTEST_CHANNEL_NAME_V2 : CONTEST_CHANNEL_NAME_V1;
    }

    private boolean isReplayV2Enabled() {
        String versionEnabled = settings.getRuntime().getVersionEnabled();
        return "v2".equalsIgnoreCase(versionEnabled);
    }

    private String getLazaxRoleMention() {
        if (JdaService.guildPrimary == null) return "";
        Role role = JdaService.guildPrimary.getRolesByName(CombatContestService.LAZAX_MINIGAME_ROLE_NAME, true).stream()
                .findFirst()
                .orElse(null);
        return role == null ? "" : role.getAsMention();
    }

    private Game loadGame(String gameName) {
        var managedGame = GameManager.getManagedGame(gameName);
        return managedGame == null ? null : managedGame.getGame();
    }

    private void postReplayEvent(
            MessageChannel channel, Game game, CombatCandidateEntity candidate, CombatCandidateEventEntity event) {
        ReplayDispatchPayload payload = payloadSerializer.read(event);
        if (payload == null) {
            sendDiscordMessage(channel, event.getSummaryText(), List.of());
            return;
        }

        if (payload instanceof ReplayDispatchPayload.HitAssignDispatch hit) {
            postHitAssignmentReplayEvent(channel, game, candidate, event, hit);
            return;
        }
        if (payload instanceof ReplayDispatchPayload.TileRenderMessageDispatch tileRender) {
            postTileRenderReplayEvent(channel, game, candidate, event.getSummaryText(), tileRender);
            return;
        }
        if (payload instanceof ReplayDispatchPayload.DiscordMessageDispatch messageDispatch) {
            sendDiscordMessage(channel, messageDispatch.message(), event.getSummaryText());
            return;
        }

        sendDiscordMessage(channel, event.getSummaryText(), List.of());
    }

    @SneakyThrows
    private void postHitAssignmentReplayEvent(
            MessageChannel channel,
            Game game,
            CombatCandidateEntity candidate,
            CombatCandidateEventEntity event,
            ReplayDispatchPayload.HitAssignDispatch payload) {
        postTileRenderReplayEvent(
                channel,
                game,
                candidate,
                event.getSummaryText(),
                List.of(),
                payload.tilePosition(),
                payload.combatStateSnapshotJson());
    }

    @SneakyThrows
    private void postTileRenderReplayEvent(
            MessageChannel channel,
            Game game,
            CombatCandidateEntity candidate,
            String fallbackMessage,
            ReplayDispatchPayload.TileRenderMessageDispatch payload) {
        ReplayDispatchPayload.DiscordMessage replayMessage = payload.message();
        String message =
                replayMessage == null ? fallbackMessage : firstNonBlank(replayMessage.content(), fallbackMessage);
        List<MessageEmbed> embeds =
                replayMessage == null ? List.of() : ReplayDispatchSerializer.toMessageEmbeds(replayMessage.embeds());
        postTileRenderReplayEvent(
                channel, game, candidate, message, embeds, payload.tilePosition(), payload.combatStateSnapshotJson());
    }

    @SneakyThrows
    private void postTileRenderReplayEvent(
            MessageChannel channel,
            Game game,
            CombatCandidateEntity candidate,
            String message,
            List<MessageEmbed> embeds,
            String tilePosition,
            String snapshotJson) {
        sendTileRenderMessage(
                channel, message, embeds, restoreReplayGame(snapshotJson, game, candidate, tilePosition), tilePosition);
    }

    private Game restoreReplayGame(
            String snapshotJson, Game game, CombatCandidateEntity candidate, String tilePosition) {
        if (tilePosition == null || snapshotJson == null) return null;
        Game snapshotGame = CombatReplayRenderSnapshotSupport.restoreGame(snapshotJson, game);
        if (snapshotGame == null || candidate == null) return snapshotGame;
        removeReplayCommandCounters(snapshotGame, tilePosition);
        snapshotGame.setName(CombatReplayRenderSnapshotSupport.buildReplaySnapshotName(
                candidate.getAttackerFaction(), candidate.getDefenderFaction()));
        return snapshotGame;
    }

    @SneakyThrows
    private Message sendTileRenderMessage(
            MessageChannel channel, String message, List<MessageEmbed> embeds, Game snapshotGame, String tilePosition) {
        if (snapshotGame == null) {
            return sendDiscordMessage(channel, message, embeds);
        }
        try (FileUpload fileUpload = new TileGenerator(snapshotGame, null, null, 0, tilePosition).createFileUpload()) {
            MessageCreateBuilder builder =
                    new MessageCreateBuilder().addContent(message).addFiles(fileUpload);
            if (!embeds.isEmpty()) builder.addEmbeds(embeds);
            return channel.sendMessage(builder.build()).complete();
        }
    }

    private void removeReplayCommandCounters(Game snapshotGame, String tilePosition) {
        if (tilePosition == null || tilePosition.isBlank()) return;
        Tile tile = snapshotGame.getTileByPosition(tilePosition);
        if (tile == null) return;
        tile.removeAllCC();
    }

    private Message sendDiscordMessage(
            MessageChannel channel, ReplayDispatchPayload.DiscordMessage message, String fallbackContent) {
        if (message == null) {
            return channel.sendMessage(fallbackContent).complete();
        }
        return sendDiscordMessage(
                channel,
                firstNonBlank(message.content(), fallbackContent),
                ReplayDispatchSerializer.toMessageEmbeds(message.embeds()));
    }

    private Message sendDiscordMessage(MessageChannel channel, String content, List<MessageEmbed> embeds) {
        if (embeds.isEmpty()) {
            return channel.sendMessage(content).complete();
        }
        if (content == null || content.isBlank()) {
            return channel.sendMessageEmbeds(embeds).complete();
        }
        return channel.sendMessage(content).addEmbeds(embeds).complete();
    }

    private String firstNonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private CombatCandidateEntity loadCandidate(Long candidateId) {
        return candidateRepository.findById(candidateId).orElse(null);
    }

    public record ForcePromoteResult(boolean promoted, String reason, CombatReplayContestEntity contest) {

        private static ForcePromoteResult promoted(CombatReplayContestEntity contest) {
            return new ForcePromoteResult(true, null, contest);
        }

        private static ForcePromoteResult rejected(String reason) {
            return new ForcePromoteResult(false, reason, null);
        }

        private static ForcePromoteResult rejected(String reason, CombatReplayContestEntity contest) {
            return new ForcePromoteResult(false, reason, contest);
        }
    }
}
