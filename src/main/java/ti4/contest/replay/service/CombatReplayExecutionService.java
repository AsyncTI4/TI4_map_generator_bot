package ti4.contest.replay.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatCandidateEventType;
import ti4.contest.replay.core.CombatContestReplayStatus;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatCandidateEventEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.helpers.RandomHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@Service
@RequiredArgsConstructor
public class CombatReplayExecutionService {

    private static final List<String> PREDICTION_LOCK_TITLES = List.of(
            "Predictions and Side Bets Open",
            "The Wagers Open",
            "The Betting Hall Opens",
            "The War Ledger Opens",
            "The Archives Open",
            "Cast Your Wager");
    private static final List<String> PREDICTION_LOCK_SUBTITLES = List.of(
            "_The Lazax ledgers now accept predictions and side bets for the coming clash._",
            "_The betting hall stirs as a new contest enters the record._",
            "_The war ledger opens before the battle unfolds._",
            "_The next battle stands before the record; place your wager._",
            "_Another clash enters the chronicles; place your calls and side bets in the record._");
    private static final Duration REPLAY_START_WARNING_LEAD_TIME = Duration.ofMinutes(5);
    private static final List<String> REPLAY_START_WARNING_LORE = List.of(
            "_The arbiters have sealed the last wagers, and the archive-drones are turning their lenses toward the field._",
            "_Across the old imperial datavaults, quills of light scratch the names of fleets about to become precedent._",
            "_The Hall of Cartographers grows silent as the final tactical overlays settle into place._",
            "_A Lazax clerk strikes the bronze bell of remembrance; the combat record is nearly ready to unfold._",
            "_Witness-scribes gather at the edge of the war table while the last echoes of prophecy fade._");

    private final CombatContestSettings settings;
    private final CombatCandidateRepository candidateRepository;
    private final CombatReplayContestRepository replayContestRepository;
    private final CombatCandidateEventRepository candidateEventRepository;
    private final CombatReplayLeaderboardService replayLeaderboardService;
    private final CombatReplaySideBetService replaySideBetService;
    private final CombatReplayDiscordPostService discordPostService;

    public void runReplayTick() {
        postReplayStartWarnings();
        List<CombatReplayContestEntity> dueContests =
                replayContestRepository.findByReplayStatusInAndNextReplayAtLessThanEqualOrderByNextReplayAtAsc(
                        Set.of(CombatContestReplayStatus.PENDING, CombatContestReplayStatus.REPLAYING),
                        LocalDateTime.now());
        for (CombatReplayContestEntity contest : dueContests) {
            replaySingleContest(contest);
        }
    }

    private void postReplayStartWarnings() {
        LocalDateTime now = LocalDateTime.now();
        List<CombatReplayContestEntity> contests =
                replayContestRepository
                        .findByReplayStatusAndReplayStartWarningPostedAtIsNullAndReplayStartAtBetweenOrderByReplayStartAtAsc(
                                CombatContestReplayStatus.PENDING, now, now.plus(REPLAY_START_WARNING_LEAD_TIME));
        for (CombatReplayContestEntity contest : contests) {
            postReplayStartWarning(contest);
        }
    }

    private void postReplayStartWarning(CombatReplayContestEntity contest) {
        try {
            int claimed =
                    replayContestRepository.markReplayStartWarningPostedIfUnset(contest.getId(), LocalDateTime.now());
            if (claimed == 0) return;

            MessageChannel channel = CombatReplayDiscordPostService.getContestThreadOrChannel(contest);
            if (channel == null) return;
            MessageHelper.sendMessageToChannel(channel, buildReplayStartWarningMessage(channel));
        } catch (Exception e) {
            BotLogger.error("Failed to post combat replay start warning.", e);
        }
    }

    private static String buildReplayStartWarningMessage(MessageChannel channel) {
        return LazaxMinigameRoleHelper.mention(channel) + " replay starts in 5 minutes!\n"
                + RandomHelper.pickRandomFromList(REPLAY_START_WARNING_LORE);
    }

    public void postPromotionContext(
            MessageChannel replayChannel, CombatReplayContestEntity contest, CombatCandidateEntity winner) {
        try {
            announcePreReplayContextIfNeeded(replayChannel, contest, winner);
            Game game = loadGame(winner.getGameName());
            replaySideBetService.postSideBetButtonsIfNeeded(replayChannel, game, contest, winner);
            markSideBetButtonsPosted(contest, LocalDateTime.now());
            announcePredictionLockCountdown(replayChannel, contest);
        } catch (Exception e) {
            BotLogger.error("Failed to post replay context at promotion.", e);
        }
    }

    private void markSideBetButtonsPosted(CombatReplayContestEntity contest, LocalDateTime now) {
        if (contest.getSideBetButtonsPostedAt() != null) return;
        contest.setSideBetButtonsPostedAt(now);
        replayContestRepository.save(contest);
    }

    private void replaySingleContest(CombatReplayContestEntity contest) {
        CombatCandidateEntity candidate = loadCandidate(contest.getCandidateId());
        CombatCandidateEventEntity event = candidateEventRepository
                .findByCandidateIdAndSequenceNumber(contest.getCandidateId(), contest.getNextEventSequence())
                .orElse(null);
        if (event == null) {
            completeReplayContest(contest);
            return;
        }

        Game game = candidate == null ? null : loadGame(candidate.getGameName());
        try {
            MessageChannel channel = CombatReplayDiscordPostService.getContestThreadOrChannel(contest);
            if (channel == null) {
                rescheduleReplay(contest, "Replay channel unavailable.");
                return;
            }
            if (contest.getReplayStatus() == CombatContestReplayStatus.PENDING && candidate != null && game != null) {
                announcePreReplayContextIfNeeded(channel, contest, candidate);
                replaySideBetService.refreshSideBetSummary(channel, contest, candidate);
                replayLeaderboardService.lockPredictionsAtReplayStart(game, contest, candidate);
                replayLeaderboardService.announceLockedPredictionsIfNeeded(channel, game, contest, candidate);
            }
            if (event.getEventType() != CombatCandidateEventType.START) {
                discordPostService.postReplayEvent(channel, game, candidate, event);
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
            MessageHelper.sendMessageToChannel(channel, message);
        }
        contest.setPreReplayContextPostedAt(LocalDateTime.now());
        replayContestRepository.save(contest);
    }

    private void announcePredictionLockCountdown(MessageChannel channel, CombatReplayContestEntity contest) {
        Duration votingWindow = votingWindowRemaining(contest);
        String title = RandomHelper.pickRandomFromList(PREDICTION_LOCK_TITLES);
        String subtitle = RandomHelper.pickRandomFromList(PREDICTION_LOCK_SUBTITLES);
        String message = votingWindow.isZero() || votingWindow.isNegative()
                ? "## " + title + "\n" + subtitle
                        + "\nPredictions and side bets are open. The combat begins immediately."
                : "## " + title + "\n" + subtitle + "\nPredictions and side bets are open for **"
                        + formatVotingWindow(votingWindow) + "**." + predictionLockTimeText();
        MessageHelper.sendMessageToChannel(channel, message);
    }

    private String predictionLockTimeText() {
        int dailyLockHourCentral = settings.getReplayExecution().getDailyLockHourCentral();
        if (dailyLockHourCentral < 0) return "";
        return "\nPredictions and side bets lock at **"
                + formatCentralLockTime(
                        dailyLockHourCentral, settings.getReplayExecution().getDailyLockMinuteCentral())
                + " Central**.";
    }

    private static String formatCentralLockTime(int hour, int minute) {
        int displayHour = hour % 12;
        if (displayHour == 0) displayHour = 12;
        return String.format("%d:%02d %s", displayHour, minute, hour < 12 ? "AM" : "PM");
    }

    private Duration votingWindowRemaining(CombatReplayContestEntity contest) {
        if (contest == null || contest.getReplayStartAt() == null) {
            return Duration.ofSeconds(settings.getReplayExecution().getStartDelaySeconds());
        }
        return Duration.between(LocalDateTime.now(), contest.getReplayStartAt());
    }

    private static String formatVotingWindow(Duration votingWindow) {
        long totalSeconds = Math.max(0, votingWindow.toSeconds());
        if (totalSeconds < 60) {
            return totalSeconds + " " + (totalSeconds == 1 ? "second" : "seconds");
        }
        if (totalSeconds < 3600) {
            long minutes = (totalSeconds + 59) / 60;
            return minutes + " " + (minutes == 1 ? "minute" : "minutes");
        }
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (minutes == 0) {
            return hours + " " + (hours == 1 ? "hour" : "hours");
        }
        return hours + " " + (hours == 1 ? "hour" : "hours") + " " + minutes + " "
                + (minutes == 1 ? "minute" : "minutes");
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

    private CombatCandidateEntity loadCandidate(Long candidateId) {
        return candidateRepository.findById(candidateId).orElse(null);
    }

    private static Game loadGame(String gameName) {
        var managedGame = GameManager.getManagedGame(gameName);
        return managedGame == null ? null : managedGame.getGame();
    }
}
