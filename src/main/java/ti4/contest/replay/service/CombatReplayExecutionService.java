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
    private final CombatReplayContestRepository replayContestRepository;
    private final CombatCandidateEventRepository candidateEventRepository;
    private final CombatReplayLeaderboardService replayLeaderboardService;
    private final CombatReplaySideBetService replaySideBetService;
    private final CombatReplayDiscordPostService discordPostService;

    public void runReplayTick() {
        List<CombatReplayContestEntity> dueContests =
                replayContestRepository.findByReplayStatusInAndNextReplayAtLessThanEqualOrderByNextReplayAtAsc(
                        Set.of(CombatContestReplayStatus.PENDING, CombatContestReplayStatus.REPLAYING),
                        LocalDateTime.now());
        for (CombatReplayContestEntity contest : dueContests) {
            replaySingleContest(contest);
        }
    }

    public void postPromotionContext(
            MessageChannel replayChannel, CombatReplayContestEntity contest, CombatCandidateEntity winner) {
        try {
            announcePreReplayContextIfNeeded(replayChannel, contest, winner);
            Game game = loadGame(winner.getGameName());
            replaySideBetService.postSideBetButtonsIfNeeded(replayChannel, game, contest, winner);
            markSideBetButtonsPosted(contest, LocalDateTime.now());
            announcePredictionLockCountdown(replayChannel);
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
            MessageChannel channel = discordPostService.getContestThreadOrChannel(contest);
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

    private void announcePredictionLockCountdown(MessageChannel channel) {
        int startDelaySeconds = replayStartDelaySeconds();
        String title = RandomHelper.pickRandomFromList(PREDICTION_LOCK_TITLES);
        String subtitle = RandomHelper.pickRandomFromList(PREDICTION_LOCK_SUBTITLES);
        String message = startDelaySeconds <= 0
                ? "## " + title + "\n" + subtitle + "\nVoting is now open. The combat begins immediately."
                : "## " + title + "\n" + subtitle + "\nVoting is now open for **"
                        + formatVotingWindow(startDelaySeconds) + "**.";
        MessageHelper.sendMessageToChannel(channel, message);
    }

    private String formatVotingWindow(int startDelaySeconds) {
        if (startDelaySeconds < 60) {
            return startDelaySeconds + " " + (startDelaySeconds == 1 ? "second" : "seconds");
        }
        if (startDelaySeconds % 60 == 0) {
            int minutes = startDelaySeconds / 60;
            return minutes + " " + (minutes == 1 ? "minute" : "minutes");
        }
        int minutes = startDelaySeconds / 60;
        int seconds = startDelaySeconds % 60;
        return minutes + " " + (minutes == 1 ? "minute" : "minutes") + " " + seconds + " "
                + (seconds == 1 ? "second" : "seconds");
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

    private int replayStartDelaySeconds() {
        return settings.getReplayExecution().getStartDelaySeconds();
    }

    private CombatCandidateEntity loadCandidate(Long candidateId) {
        return candidateRepository.findById(candidateId).orElse(null);
    }

    private Game loadGame(String gameName) {
        var managedGame = GameManager.getManagedGame(gameName);
        return managedGame == null ? null : managedGame.getGame();
    }
}
