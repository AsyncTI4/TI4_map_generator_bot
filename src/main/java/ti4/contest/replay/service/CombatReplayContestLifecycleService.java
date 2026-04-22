package ti4.contest.replay.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.*;
import ti4.contest.replay.entities.*;
import ti4.contest.replay.repository.*;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.image.TileGenerator;
import ti4.logging.BotLogger;
import ti4.model.ActionCardModel;
import ti4.model.LeaderModel;

@Service
@RequiredArgsConstructor
public class CombatReplayContestLifecycleService {

    private static final String CONTEST_CHANNEL_NAME = "lazax-war-archives-dev";
    private static final long SHADOW_DISCORD_ID = 0L;

    private final CombatCandidateRepository candidateRepository;
    private final CombatObservationRepository observationRepository;
    private final CombatReplayContestRepository replayContestRepository;
    private final CombatCandidateEventRepository candidateEventRepository;
    private final CombatReplayLeaderboardService replayLeaderboardService;
    private final CombatReplayEventPayloadSerde payloadSerde;

    public void promoteBestCandidateIfDue() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        if (now.getMinute() != 0) return;
        if (replayContestRepository.existsByPostedAtGreaterThanEqual(now.truncatedTo(ChronoUnit.HOURS))) return;

        List<CombatCandidateEntity> candidates = candidateRepository.findResolvedPromotionCandidates(
                CombatCandidateStatus.RESOLVED, CombatCandidatePromotionStatus.PENDING, now.minusHours(4));
        CombatCandidateEntity winner = selectPromotionWinner(candidates);
        if (winner == null) return;
        promoteCandidate(winner);
    }

    public ForcePromoteResult forcePromoteCandidate(Long candidateId) {
        if (candidateId == null) {
            return ForcePromoteResult.rejected("candidateId is required");
        }

        CombatCandidateEntity candidate =
                candidateRepository.findById(candidateId).orElse(null);
        if (candidate == null) {
            return ForcePromoteResult.rejected("Candidate not found");
        }
        if (candidate.getStatus() != CombatCandidateStatus.RESOLVED) {
            return ForcePromoteResult.rejected("Candidate must be RESOLVED before promotion");
        }

        CombatReplayContestEntity existingContest =
                replayContestRepository.findByCandidateId(candidateId).orElse(null);
        if (existingContest != null) {
            return ForcePromoteResult.rejected("Candidate already promoted", existingContest);
        }

        CombatReplayContestEntity contest = promoteCandidate(candidate);
        if (contest == null) {
            return ForcePromoteResult.rejected("Promotion failed");
        }
        return ForcePromoteResult.promoted(contest);
    }

    private CombatReplayContestEntity promoteCandidate(CombatCandidateEntity winner) {
        CombatObservationEntity observation =
                observationRepository.findById(winner.getObservationId()).orElse(null);
        if (observation == null) return null;

        Game game = loadGame(winner.getGameName());
        if (game == null) return null;

        String message = LazaxCombatSupport.formatReplayAnnouncement(game, observation, winner, getLazaxRoleMention());
        if (CombatReplayRuntimeSettings.SHADOW_MODE) {
            return createShadowReplayContest(game, observation, winner, message);
        }

        TextChannel contestChannel = getContestChannel();
        if (contestChannel == null) return null;

        try {
            Message posted = contestChannel.sendMessage(message).complete();
            ThreadChannel thread = createReplayThread(posted, winner);
            return persistPromotedReplayContest(
                    game, observation, winner, message, LocalDateTime.now(), contestChannel, posted, thread);
        } catch (Exception e) {
            BotLogger.error("Replay contest promotion failed.", e);
            return null;
        }
    }

    public void runReplayTick() {
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

        CombatCandidateEntity candidate =
                candidateRepository.findById(contest.getCandidateId()).orElse(null);
        Game game = candidate == null ? null : loadGame(candidate.getGameName());
        try {
            if (!CombatReplayRuntimeSettings.SHADOW_MODE) {
                MessageChannel channel = getContestThreadOrChannel(contest);
                if (channel == null) {
                    rescheduleReplay(contest, "Replay channel unavailable.");
                    return;
                }
                postReplayEvent(channel, game, event);
            }
            contest.setNextEventSequence(contest.getNextEventSequence() + 1);
            contest.setReplayStatus(CombatContestReplayStatus.REPLAYING);
            contest.setReplayError(null);
            contest.setNextReplayAt(LocalDateTime.now().plusSeconds(15));
        } catch (Exception e) {
            rescheduleReplay(contest, e.getMessage());
            return;
        }
        replayContestRepository.save(contest);
    }

    private void completeReplayContest(CombatReplayContestEntity contest) {
        CombatCandidateEntity candidate =
                candidateRepository.findById(contest.getCandidateId()).orElse(null);
        if (candidate != null && !CombatReplayRuntimeSettings.SHADOW_MODE) {
            Game game = loadGame(candidate.getGameName());
            if (game != null) {
                replayLeaderboardService.finalizeReplayLeaderboardContest(game, contest, candidate);
            }
        }

        contest.setReplayStatus(CombatContestReplayStatus.COMPLETED);
        contest.setReplayCompletedAt(LocalDateTime.now());
        contest.setReplayError(null);
        replayContestRepository.save(contest);
    }

    private void rescheduleReplay(CombatReplayContestEntity contest, String error) {
        contest.setReplayStatus(CombatContestReplayStatus.REPLAYING);
        contest.setReplayError(error);
        contest.setNextReplayAt(LocalDateTime.now().plusSeconds(15));
        replayContestRepository.save(contest);
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
        ThreadChannel thread = posted.createThreadChannel(
                        "combat-archive-" + winner.getGameName() + "-" + winner.getTilePosition())
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS)
                .complete();
        thread.sendMessage("Replay queued. The Archives will begin the reconstruction shortly.")
                .complete();
        return thread;
    }

    private CombatReplayContestEntity buildReplayContest(
            CombatCandidateEntity winner,
            long publicChannelId,
            long publicMessageId,
            Long publicThreadId,
            LocalDateTime promotedAt) {
        CombatReplayContestEntity contest = new CombatReplayContestEntity();
        contest.setCandidateId(winner.getId());
        contest.setPostedAt(promotedAt);
        contest.setPublicChannelId(publicChannelId);
        contest.setPublicMessageId(publicMessageId);
        contest.setPublicThreadId(publicThreadId);
        contest.setReplayStatus(CombatContestReplayStatus.PENDING);
        contest.setReplayStartAt(promotedAt.plusMinutes(10));
        contest.setNextReplayAt(promotedAt.plusMinutes(10));
        contest.setNextEventSequence(1);
        return contest;
    }

    private CombatReplayContestEntity createShadowReplayContest(
            Game game, CombatObservationEntity observation, CombatCandidateEntity winner, String message) {
        BotLogger.info("Combat replay shadow mode: promotion suppressed for candidate " + winner.getId());
        return persistPromotedReplayContest(game, observation, winner, message, LocalDateTime.now(), null, null, null);
    }

    private CombatReplayContestEntity persistPromotedReplayContest(
            Game game,
            CombatObservationEntity observation,
            CombatCandidateEntity winner,
            String message,
            LocalDateTime promotedAt,
            TextChannel contestChannel,
            Message posted,
            ThreadChannel thread) {
        long publicChannelId = contestChannel == null ? SHADOW_DISCORD_ID : contestChannel.getIdLong();
        long publicMessageId = posted == null ? SHADOW_DISCORD_ID : posted.getIdLong();
        Long publicThreadId = thread == null ? null : thread.getIdLong();
        CombatReplayContestEntity contest =
                buildReplayContest(winner, publicChannelId, publicMessageId, publicThreadId, promotedAt);

        Long legacyContestId = replayLeaderboardService.initializeReplayLeaderboardContest(
                game,
                observation,
                winner,
                contest.getPublicChannelId(),
                contest.getPublicMessageId(),
                contest.getPublicThreadId(),
                message);
        contest.setLegacyPredictorContestId(legacyContestId);
        CombatReplayContestEntity savedContest = replayContestRepository.save(contest);

        winner.setPromotionStatus(CombatCandidatePromotionStatus.PROMOTED);
        winner.setPromotedAt(promotedAt);
        candidateRepository.save(winner);
        return savedContest;
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
        return JdaService.guildPrimary.getTextChannelsByName(CONTEST_CHANNEL_NAME, true).stream()
                .findFirst()
                .orElse(null);
    }

    private String getLazaxRoleMention() {
        // if (JdaService.guildPrimary == null) return "";
        // Role role = JdaService.guildPrimary.getRolesByName(CombatContestService.LAZAX_MINIGAME_ROLE_NAME, true)
        //         .stream()
        //         .findFirst()
        //         .orElse(null);
        // return role == null ? "" : role.getAsMention();
        return "";
    }

    private Game loadGame(String gameName) {
        var managedGame = GameManager.getManagedGame(gameName);
        return managedGame == null ? null : managedGame.getGame();
    }

    private void postReplayEvent(MessageChannel channel, Game game, CombatCandidateEventEntity event) {
        if (event.getEventType() == CombatCandidateEventType.HIT_ASSIGN
                && postHitAssignmentReplayEvent(channel, event)) {
            return;
        }

        RenderedReplayEvent rendered = renderReplayEvent(game, event);
        if (rendered.embed() != null) {
            if (rendered.message() == null || rendered.message().isBlank()) {
                channel.sendMessageEmbeds(rendered.embed()).complete();
            } else {
                channel.sendMessage(rendered.message())
                        .addEmbeds(rendered.embed())
                        .complete();
            }
            return;
        }
        channel.sendMessage(rendered.message()).complete();
    }

    private boolean postHitAssignmentReplayEvent(MessageChannel channel, CombatCandidateEventEntity event) {
        CombatReplayEventPayload rawPayload = payloadSerde.read(event);
        if (!(rawPayload instanceof CombatReplayEventPayload.HitAssignPayload payload)) return false;

        String message = event.getSummaryText();
        String tilePosition = payload.tilePosition();
        String snapshotJson = payload.combatStateSnapshotJson();
        if (tilePosition == null || snapshotJson == null) {
            channel.sendMessage(message).complete();
            return true;
        }

        Game snapshotGame = CombatReplayRenderSnapshotSupport.restoreGame(snapshotJson);
        if (snapshotGame == null) {
            channel.sendMessage(message).complete();
            return true;
        }

        try (FileUpload fileUpload = new TileGenerator(snapshotGame, null, null, 0, tilePosition).createFileUpload()) {
            channel.sendMessage(new MessageCreateBuilder()
                            .addContent(message)
                            .addFiles(fileUpload)
                            .build())
                    .complete();
            return true;
        } catch (Exception e) {
            channel.sendMessage(message).complete();
            return true;
        }
    }

    private RenderedReplayEvent renderReplayEvent(Game game, CombatCandidateEventEntity event) {
        CombatReplayEventPayload payload = payloadSerde.read(event);
        if (payload == null) {
            return new RenderedReplayEvent(event.getSummaryText(), null);
        }

        MessageEmbed replayEmbed = resolveReplayEmbed(game, payload);
        String message =
                switch (event.getEventType()) {
                    case START -> event.getSummaryText();
                    case ROLL -> renderRollMessage(payload, event.getSummaryText());
                    case HIT_ASSIGN, CARD, AGENT, INFO, RESOLVED, CANCELLED -> event.getSummaryText();
                };
        return new RenderedReplayEvent(message, replayEmbed);
    }

    private MessageEmbed resolveReplayEmbed(Game game, CombatReplayEventPayload payload) {
        if (payload instanceof CombatReplayEventPayload.CardPayload card) {
            return resolveActionCardEmbed(game, card.componentId());
        }
        if (payload instanceof CombatReplayEventPayload.AgentPayload agent) {
            return resolveLeaderEmbed(game, agent.componentId());
        }
        if (payload instanceof CombatReplayEventPayload.InfoPayload info) {
            return resolveLeaderEmbed(game, info.componentId());
        }
        return null;
    }

    private MessageEmbed resolveActionCardEmbed(Game game, String componentId) {
        if (componentId == null || componentId.isBlank()) return null;
        ActionCardModel actionCard = Mapper.getActionCard(componentId);
        return actionCard == null ? null : actionCard.getRepresentationEmbed(false, true, game);
    }

    private MessageEmbed resolveLeaderEmbed(Game game, String componentId) {
        if (componentId == null || componentId.isBlank()) return null;
        LeaderModel leader = Mapper.getLeader(componentId);
        if (leader == null) return null;
        if ("hero".equalsIgnoreCase(leader.getType())) {
            boolean showFlavourText = game != null && Constants.VERBOSITY_VERBOSE.equals(game.getOutputVerbosity());
            boolean useTwilightsFallText = game != null && game.isTwilightsFallMode();
            return leader.getRepresentationEmbed(false, true, false, showFlavourText, useTwilightsFallText);
        }
        return leader.getRepresentationEmbed();
    }

    private String renderRollMessage(CombatReplayEventPayload payload, String fallback) {
        if (!(payload instanceof CombatReplayEventPayload.RollPayload roll)) return fallback;
        String summaryHeader = stringOrDefault(roll.summaryHeader(), null);
        List<String> modifierLines = emptyIfNull(roll.modifierLines());
        List<String> unitRollLines = emptyIfNull(roll.unitRollLines());
        List<String> specialLines = emptyIfNull(roll.specialLines());
        String totalHitsLine = stringOrDefault(roll.totalHitsLine(), null);
        if (summaryHeader == null || unitRollLines.isEmpty()) {
            return fallback;
        }

        StringBuilder builder = new StringBuilder(summaryHeader);
        if (!modifierLines.isEmpty()) {
            builder.append("\nWith modifiers: ");
            for (String modifierLine : modifierLines) {
                builder.append("\n").append(modifierLine);
            }
        }
        for (String unitRollLine : unitRollLines) {
            builder.append("\n").append(unitRollLine);
        }
        if (totalHitsLine != null) {
            builder.append("\n\n").append(totalHitsLine);
        }
        for (String specialLine : specialLines) {
            builder.append("\n").append(specialLine);
        }
        return builder.toString();
    }

    private String stringOrDefault(Object value, String fallback) {
        return value instanceof String string && !string.isBlank() ? string : fallback;
    }

    private List<String> emptyIfNull(List<String> values) {
        return values == null ? List.of() : values;
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

    private record RenderedReplayEvent(String message, MessageEmbed embed) {}
}
