package ti4.contest.replay.service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.*;
import ti4.contest.replay.dispatch.ReplayDispatchPayload;
import ti4.contest.replay.entities.*;
import ti4.contest.replay.repository.*;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.spring.service.contest.CombatContestType;

@Service
@RequiredArgsConstructor
/**
 * Observes live combats, scores them against the current replay thresholds, and records replay candidates/events.
 */
public class CombatReplayService {

    private static final Pattern SYSTEM_TILE_PATTERN = Pattern.compile("-system-([^-]+)-");

    private final CombatContestSettings settings;
    private final CombatObservationRepository observationRepository;
    private final CombatCandidateRepository candidateRepository;
    private final CombatCandidateEventRepository candidateEventRepository;
    private final CombatReplayEventAppender eventAppender;
    private volatile SelectionSnapshot selectionSnapshot;

    @PostConstruct
    void initializeSelectionSnapshot() {
        refreshSelectionSnapshot();
    }

    public void onSpaceCombatStarted(Game game, Player attacker, Player defender, Tile tile) {
        if (!LazaxCombatSupport.isEligibleGame(game)
                || !LazaxCombatSupport.isEligibleCombat(game, attacker, defender, tile)) {
            return;
        }

        LazaxCombatSupport.SpaceCombatSnapshot snapshot =
                LazaxCombatSupport.buildSpaceCombatSnapshot(game, attacker, defender, tile);
        if (snapshot == null) return;

        Evaluation evaluation = evaluateSelectionSnapshot(snapshot);
        CombatObservationEntity observation = buildObservation(game, attacker, defender, tile, snapshot, evaluation);
        boolean eligible = isEligibleCandidate(game, attacker, defender, tile, evaluation);
        observation.setEligibleAsCandidate(eligible);
        observationRepository.save(observation);

        if (!eligible) return;

        CombatCandidateEntity candidate = buildCandidate(
                observation,
                attacker,
                defender,
                CombatReplayRenderSnapshotSupport.captureHitAssignmentSnapshot(game, tile.getPosition()));
        candidateRepository.save(candidate);

        observation.setCandidateId(candidate.getId());
        observationRepository.save(observation);

        appendDiscordEvent(candidate, CombatCandidateEventType.START, null, null, snapshot.replaySummaryText());
    }

    public void onButtonInteractionSettled(Game game, Player player, ButtonInteractionEvent event) {
        for (CombatCandidateEntity candidate : getTrackingCandidates(game)) {
            if (evaluateCandidateCompletion(game, candidate)) continue;
            trackHitAssignments(game, player, event, candidate);
        }
    }

    public void mirrorCombatRoll(
            Game game, Player player, Player opponent, Tile tile, String message, String rollType, Integer hits) {
        CombatCandidateEntity candidate = getTrackingCandidate(game, tile.getPosition());
        if (candidate == null || !matchesParticipants(candidate, player, opponent)) return;

        int round = getCurrentRound(game, candidate);
        appendDiscordEvent(
                candidate, CombatCandidateEventType.ROLL, round, player.getFaction(), "## Roll Update\n" + message);
    }

    public void mirrorCombatEvent(
            Game game, Player player, String header, String name, MessageEmbed embed, String sourceChannelName) {
        CombatCandidateEventType eventType = mapEventType(header);
        CombatCandidateEntity candidate = resolveCandidateForMirrorEvent(game, player, sourceChannelName);
        if (candidate == null) return;

        appendDiscordEvent(
                candidate,
                eventType,
                null,
                player.getFaction(),
                "## " + header + "\n" + player.getRepresentation() + " " + name,
                embed);
    }

    private CombatCandidateEventType mapEventType(String header) {
        String normalized = header == null ? "" : header.trim().toLowerCase();
        return switch (normalized) {
            case "action card" -> CombatCandidateEventType.CARD;
            case "agent" -> CombatCandidateEventType.AGENT;
            default -> CombatCandidateEventType.INFO;
        };
    }

    private boolean evaluateCandidateCompletion(Game game, CombatCandidateEntity candidate) {
        Tile tile = game.getTileByPosition(candidate.getTilePosition());
        if (tile == null) return false;

        List<Player> remainingShipPlayers = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile).stream()
                .filter(Player::isRealPlayer)
                .filter(player -> !player.isDummy())
                .toList();
        if (remainingShipPlayers.size() == 1) {
            if (!hasRecordedRoll(candidate)) {
                cancelCandidate(game, candidate, "The tracked space combat ended before any dice were rolled.");
                return true;
            }
            Player winner = remainingShipPlayers.getFirst();
            String loserFaction = winner.getFaction().equalsIgnoreCase(candidate.getAttackerFaction())
                    ? candidate.getDefenderFaction()
                    : candidate.getAttackerFaction();
            resolveCandidate(game, candidate, tile, winner, loserFaction);
            return true;
        }
        if (remainingShipPlayers.isEmpty()) {
            String reason = hasRecordedRoll(candidate)
                    ? "The tracked space combat ended with no ships remaining."
                    : "The tracked space combat ended before any dice were rolled.";
            cancelCandidate(game, candidate, reason);
            return true;
        }
        if (remainingShipPlayers.size() > 2) {
            cancelCandidate(game, candidate, "The tracked space combat no longer has exactly two sides.");
            return true;
        }
        boolean containsBothParticipants = remainingShipPlayers.stream()
                .map(Player::getFaction)
                .allMatch(faction -> faction.equalsIgnoreCase(candidate.getAttackerFaction())
                        || faction.equalsIgnoreCase(candidate.getDefenderFaction()));
        if (!containsBothParticipants) {
            cancelCandidate(game, candidate, "The tracked space combat drifted away from the original participants.");
            return true;
        }
        return false;
    }

    private void resolveCandidate(
            Game game, CombatCandidateEntity candidate, Tile tile, Player winner, String loserFaction) {
        CombatObservationEntity observation =
                observationRepository.findById(candidate.getObservationId()).orElse(null);
        if (observation == null) return;
        Player attacker = game.getPlayerFromColorOrFaction(candidate.getAttackerFaction());
        Player defender = game.getPlayerFromColorOrFaction(candidate.getDefenderFaction());
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        if (attacker == null || defender == null || space == null) {
            cancelCandidate(game, candidate, "The tracked space combat could not be resolved cleanly.");
            return;
        }

        double attackerRemaining = LazaxCombatSupport.calculateFleetStrength(game, attacker, defender, tile, space)
                .value();
        double defenderRemaining = LazaxCombatSupport.calculateFleetStrength(game, defender, attacker, tile, space)
                .value();
        double attackerLossRatio = computeLossRatio(observation.getAttackerStrength(), attackerRemaining);
        double defenderLossRatio = computeLossRatio(observation.getDefenderStrength(), defenderRemaining);
        int roundsObserved = candidateEventRepository
                .findMaxRoundNumberByCandidateId(candidate.getId())
                .orElse(0);
        double mutualLossScore =
                Math.min(attackerLossRatio, defenderLossRatio) + ((attackerLossRatio + defenderLossRatio) / 2.0);

        candidate.setStatus(CombatCandidateStatus.RESOLVED);
        candidate.setResolvedAt(LocalDateTime.now());
        candidate.setWinnerFaction(winner.getFaction());
        candidate.setLoserFaction(loserFaction);
        candidate.setResolutionReason("Winner determined from remaining fleets.");
        candidate.setPromotionScore(roundsObserved + mutualLossScore);
        candidateRepository.save(candidate);

        appendTileRenderEvent(
                candidate,
                CombatCandidateEventType.RESOLVED,
                roundsObserved,
                winner.getFaction(),
                tile.getPosition(),
                CombatReplayRenderSnapshotSupport.captureHitAssignmentSnapshot(game, tile.getPosition()),
                "## Contest Result\n"
                        + winner.getFactionEmoji() + " " + winner.getUserName() + " won the space combat in "
                        + tile.getRepresentationForButtons() + ".\n"
                        + "Game Link: [Open Game](https://asyncti4.com/game/" + game.getName() + ")");
    }

    private void cancelCandidate(Game game, CombatCandidateEntity candidate, String reason) {
        candidate.setStatus(CombatCandidateStatus.CANCELLED);
        candidate.setResolvedAt(LocalDateTime.now());
        candidate.setCancellationReason(reason);
        candidate.setPromotionStatus(CombatCandidatePromotionStatus.EXPIRED);
        candidateRepository.save(candidate);

        appendDiscordEvent(candidate, CombatCandidateEventType.CANCELLED, null, null, "## Contest Closed\n" + reason);
    }

    private void trackHitAssignments(
            Game game, Player player, ButtonInteractionEvent event, CombatCandidateEntity candidate) {
        if (player == null || !isSpaceCombatHitAssignment(game, player, event)) return;
        if (!candidate
                .getTilePosition()
                .equals(getTilePosition(event.getButton().getCustomId()))) return;
        if (!matchesParticipant(candidate, player)) return;

        int round = getCurrentRound(game, candidate);
        if (candidateEventRepository.existsByCandidateIdAndEventTypeAndActorFactionAndRoundNumber(
                candidate.getId(), CombatCandidateEventType.HIT_ASSIGN, player.getFaction(), round)) {
            return;
        }

        String roundLabel = round > 0 ? "round #" + round : "the current exchange";
        String message = "## Combat Update\n"
                + player.getFactionEmoji() + " " + player.getFaction() + " " + player.getUserName()
                + " assigned hits for " + roundLabel + ".";
        eventAppender.appendEvent(
                candidate,
                CombatCandidateEventType.HIT_ASSIGN,
                round,
                player.getFaction(),
                message,
                ReplayDispatchPayload.hitAssign(
                        candidate.getTilePosition(),
                        CombatReplayRenderSnapshotSupport.captureHitAssignmentSnapshot(
                                game, candidate.getTilePosition())));
    }

    public void refreshSelectionSnapshot() {
        LocalDateTime now = LocalDateTime.now();
        List<CombatObservationEntity> window = observationRepository.findByStartedAtGreaterThanEqualOrderByStartedAtAsc(
                now.minusMinutes(settings.getCandidateSelection().getWindow().getLookbackMinutes()));
        List<Double> fairnessValues =
                window.stream().map(CombatObservationEntity::getFairnessRatio).toList();
        List<Double> weakerStrengthValues = window.stream()
                .map(observation -> Math.min(observation.getAttackerStrength(), observation.getDefenderStrength()))
                .toList();
        double jointScoreCutoff = computeCutoff(window, fairnessValues, weakerStrengthValues);
        List<SelectionObservationDebugView> observations = window.stream()
                .map(observation -> toSelectionObservationDebugView(observation, fairnessValues, weakerStrengthValues))
                .sorted((left, right) -> right.observation()
                        .getStartedAt()
                        .compareTo(left.observation().getStartedAt()))
                .toList();
        selectionSnapshot = new SelectionSnapshot(
                window.size(),
                List.copyOf(fairnessValues),
                List.copyOf(weakerStrengthValues),
                jointScoreCutoff,
                List.copyOf(observations));
    }

    public SelectionDebugView getSelectionDebugView() {
        SelectionSnapshot snapshot = getSelectionSnapshot();
        return new SelectionDebugView(
                settings.getCandidateSelection().getWindow().getLookbackMinutes(),
                settings.getCandidateSelection().getTargetCandidatesPerHour(),
                snapshot.windowSize(),
                snapshot.jointScoreCutoff(),
                average(snapshot.fairnessValues()),
                average(snapshot.weakerStrengthValues()),
                snapshot.observations());
    }

    private boolean isSpaceCombatHitAssignment(Game game, Player player, ButtonInteractionEvent event) {
        String buttonId = stripFactionChecker(event.getButton().getCustomId());
        if (buttonId.startsWith("autoAssignSpaceHits_") || buttonId.startsWith("autoAssignSpaceCannonOffenceHits_")) {
            return true;
        }
        if (!buttonId.startsWith("assignHits_") && !buttonId.startsWith("assignDamage_")) return false;
        String assignHitsType =
                game.getStoredValue(player.getFaction() + "latestAssignHits").toLowerCase();
        return "spacecombat".equals(assignHitsType) || "pds".equals(assignHitsType);
    }

    private int getCurrentRound(Game game, CombatCandidateEntity candidate) {
        return Math.max(
                getCurrentRound(game, candidate.getAttackerFaction(), candidate.getTilePosition()),
                getCurrentRound(game, candidate.getDefenderFaction(), candidate.getTilePosition()));
    }

    private int getCurrentRound(Game game, String faction, String tilePosition) {
        String tracker = game.getStoredValue("combatRoundTracker" + faction + tilePosition + Constants.SPACE);
        return tracker.isBlank() ? 0 : Integer.parseInt(tracker);
    }

    private CombatObservationEntity buildObservation(
            Game game,
            Player attacker,
            Player defender,
            Tile tile,
            LazaxCombatSupport.SpaceCombatSnapshot snapshot,
            Evaluation evaluation) {
        CombatObservationEntity observation = new CombatObservationEntity();
        observation.setStartedAt(LocalDateTime.now());
        observation.setGameName(game.getName());
        observation.setTilePosition(tile.getPosition());
        observation.setCombatType(snapshot.combatType());
        observation.setAttackerFaction(attacker.getFaction());
        observation.setDefenderFaction(defender.getFaction());
        observation.setAttackerStrength(snapshot.attackerStrength());
        observation.setDefenderStrength(snapshot.defenderStrength());
        observation.setAttackerHp(snapshot.attackerHp());
        observation.setDefenderHp(snapshot.defenderHp());
        observation.setAttackerExpectedHits(snapshot.attackerExpectedHits());
        observation.setDefenderExpectedHits(snapshot.defenderExpectedHits());
        observation.setFairnessRatio(evaluation.fairnessRatio());
        observation.setJointScore(evaluation.jointScore());
        return observation;
    }

    private boolean isEligibleCandidate(Game game, Player attacker, Player defender, Tile tile, Evaluation evaluation) {
        return evaluation.eligible()
                && !LazaxCombatSupport.hasExcludedFlagship(attacker, defender)
                && getTrackingCandidate(game, tile.getPosition()) == null;
    }

    private Evaluation evaluateSelectionSnapshot(LazaxCombatSupport.SpaceCombatSnapshot snapshot) {
        SelectionSnapshot currentSnapshot = getSelectionSnapshot();
        double fairnessRatio = LazaxCombatSupport.computeFairnessRatio(
                snapshot.attackerStrength(),
                snapshot.defenderStrength(),
                snapshot.attackerHp(),
                snapshot.defenderHp(),
                snapshot.attackerExpectedHits(),
                snapshot.defenderExpectedHits());
        double weakerStrength = Math.min(snapshot.attackerStrength(), snapshot.defenderStrength());
        double jointScore = computeJointScore(
                currentSnapshot.fairnessValues(),
                fairnessRatio,
                currentSnapshot.weakerStrengthValues(),
                weakerStrength);
        boolean eligible = settings.getCandidateSelection().getTargetCandidatesPerHour() > 0
                && currentSnapshot.hasWindow()
                && jointScore >= currentSnapshot.jointScoreCutoff();
        return new Evaluation(fairnessRatio, jointScore, eligible, currentSnapshot.windowSize());
    }

    private SelectionSnapshot getSelectionSnapshot() {
        SelectionSnapshot currentSnapshot = selectionSnapshot;
        if (currentSnapshot != null) return currentSnapshot;
        synchronized (this) {
            if (selectionSnapshot == null) {
                refreshSelectionSnapshot();
            }
            return selectionSnapshot;
        }
    }

    private double computeJointScore(
            List<Double> fairnessValues,
            double fairnessRatio,
            List<Double> weakerStrengthValues,
            double weakerStrength) {
        return percentileRank(fairnessValues, fairnessRatio) * percentileRank(weakerStrengthValues, weakerStrength);
    }

    private SelectionObservationDebugView toSelectionObservationDebugView(
            CombatObservationEntity observation, List<Double> fairnessValues, List<Double> weakerStrengthValues) {
        double weakerStrength = Math.min(observation.getAttackerStrength(), observation.getDefenderStrength());
        double fairnessPercentile = percentileRank(fairnessValues, observation.getFairnessRatio());
        double weakerStrengthPercentile = percentileRank(weakerStrengthValues, weakerStrength);
        return new SelectionObservationDebugView(
                observation,
                weakerStrength,
                fairnessPercentile,
                weakerStrengthPercentile,
                fairnessPercentile * weakerStrengthPercentile,
                Boolean.TRUE.equals(observation.getEligibleAsCandidate()));
    }

    private double computeCutoff(
            List<CombatObservationEntity> window, List<Double> fairnessValues, List<Double> weakerStrengthValues) {
        if (window.isEmpty()) return 1.0;
        int targetCandidatesPerHour = settings.getCandidateSelection().getTargetCandidatesPerHour();
        if (targetCandidatesPerHour <= 0) return 1.0;
        List<Double> sortedJointScores = window.stream()
                .map(observation -> percentileRank(fairnessValues, observation.getFairnessRatio())
                        * percentileRank(
                                weakerStrengthValues,
                                Math.min(observation.getAttackerStrength(), observation.getDefenderStrength())))
                .sorted(java.util.Comparator.reverseOrder())
                .toList();
        int index = Math.max(0, Math.min(sortedJointScores.size() - 1, targetCandidatesPerHour - 1));
        return sortedJointScores.get(index);
    }

    private double percentileRank(List<Double> values, double value) {
        if (values.isEmpty()) return 0.0;
        long count = values.stream().filter(candidate -> candidate <= value).count();
        return count / (double) values.size();
    }

    private double average(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private CombatCandidateEntity buildCandidate(
            CombatObservationEntity observation, Player attacker, Player defender, String initialRenderSnapshotJson) {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setObservationId(observation.getId());
        candidate.setStatus(CombatCandidateStatus.TRACKING);
        candidate.setPromotionStatus(CombatCandidatePromotionStatus.PENDING);
        candidate.setNextEventSequence(1);
        candidate.setStartedAt(observation.getStartedAt());
        candidate.setGameName(observation.getGameName());
        candidate.setTilePosition(observation.getTilePosition());
        candidate.setCombatType(observation.getCombatType());
        candidate.setAttackerFaction(attacker.getFaction());
        candidate.setDefenderFaction(defender.getFaction());
        candidate.setPreReplayContextText(LazaxCombatSupport.formatCombatTechSummary(attacker, defender));
        candidate.setInitialRenderSnapshotJson(initialRenderSnapshotJson);
        return candidate;
    }

    private List<CombatCandidateEntity> getTrackingCandidates(Game game) {
        return candidateRepository.findByGameNameAndStatus(game.getName(), CombatCandidateStatus.TRACKING);
    }

    private CombatCandidateEntity getTrackingCandidate(Game game, String tilePosition) {
        return candidateRepository.findFirstByGameNameAndTilePositionAndCombatTypeAndStatus(
                game.getName(), tilePosition, CombatContestType.SPACE, CombatCandidateStatus.TRACKING);
    }

    private boolean hasRecordedRoll(CombatCandidateEntity candidate) {
        return candidateEventRepository.existsByCandidateIdAndEventType(
                candidate.getId(), CombatCandidateEventType.ROLL);
    }

    private void appendDiscordEvent(
            CombatCandidateEntity candidate,
            CombatCandidateEventType eventType,
            Integer roundNumber,
            String actorFaction,
            String message) {
        eventAppender.appendEvent(
                candidate,
                eventType,
                roundNumber,
                actorFaction,
                message,
                ReplayDispatchPayload.discordMessage(message));
    }

    private void appendDiscordEvent(
            CombatCandidateEntity candidate,
            CombatCandidateEventType eventType,
            Integer roundNumber,
            String actorFaction,
            String message,
            MessageEmbed embed) {
        eventAppender.appendEvent(
                candidate,
                eventType,
                roundNumber,
                actorFaction,
                message,
                ReplayDispatchPayload.discordMessage(message, embed));
    }

    private void appendTileRenderEvent(
            CombatCandidateEntity candidate,
            CombatCandidateEventType eventType,
            Integer roundNumber,
            String actorFaction,
            String tilePosition,
            String snapshotJson,
            String message) {
        eventAppender.appendEvent(
                candidate,
                eventType,
                roundNumber,
                actorFaction,
                message,
                ReplayDispatchPayload.tileRenderMessage(tilePosition, snapshotJson, message));
    }

    private CombatCandidateEntity resolveCandidateForMirrorEvent(Game game, Player player, String sourceChannelName) {
        String tilePosition = extractTilePosition(sourceChannelName);
        if (tilePosition != null) {
            CombatCandidateEntity candidate = getTrackingCandidate(game, tilePosition);
            if (candidate != null && matchesParticipant(candidate, player)) return candidate;
        }

        List<CombatCandidateEntity> candidates = candidateRepository.findTrackingCandidatesForFaction(
                game.getName(), CombatCandidateStatus.TRACKING, player.getFaction());
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    private String extractTilePosition(String sourceChannelName) {
        if (sourceChannelName == null || sourceChannelName.isBlank()) return null;
        Matcher matcher = SYSTEM_TILE_PATTERN.matcher(sourceChannelName);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean matchesParticipants(CombatCandidateEntity candidate, Player player, Player opponent) {
        return matchesParticipant(candidate, player) && matchesParticipant(candidate, opponent);
    }

    private boolean matchesParticipant(CombatCandidateEntity candidate, Player player) {
        return player.getFaction().equalsIgnoreCase(candidate.getAttackerFaction())
                || player.getFaction().equalsIgnoreCase(candidate.getDefenderFaction());
    }

    private String getTilePosition(String buttonId) {
        String sanitized = stripFactionChecker(buttonId).replace("deleteThis", "");
        String[] parts = sanitized.split("_");
        return parts.length > 1 ? parts[1] : "";
    }

    private String stripFactionChecker(String buttonId) {
        if (!buttonId.startsWith("FFCC_")) return buttonId;
        int secondUnderscore = buttonId.indexOf('_', 5);
        if (secondUnderscore < 0) return buttonId;
        return buttonId.substring(secondUnderscore + 1);
    }

    private double computeLossRatio(double initialStrength, double remainingStrength) {
        if (initialStrength <= 0) return 0.0;
        return Math.max(0.0, Math.min(1.0, (initialStrength - remainingStrength) / initialStrength));
    }

    private record SelectionSnapshot(
            int windowSize,
            List<Double> fairnessValues,
            List<Double> weakerStrengthValues,
            double jointScoreCutoff,
            List<SelectionObservationDebugView> observations) {
        private boolean hasWindow() {
            return windowSize > 0;
        }
    }

    public record Evaluation(double fairnessRatio, double jointScore, boolean eligible, int windowSize) {}

    public record SelectionDebugView(
            int lookbackMinutes,
            int targetCandidatesPerHour,
            int windowSize,
            double jointScoreCutoff,
            double averageFairnessRatio,
            double averageWeakerStrength,
            List<SelectionObservationDebugView> observations) {}

    public record SelectionObservationDebugView(
            CombatObservationEntity observation,
            double weakerStrength,
            double fairnessPercentile,
            double weakerStrengthPercentile,
            double jointScore,
            boolean eligibleAsCandidate) {}
}
