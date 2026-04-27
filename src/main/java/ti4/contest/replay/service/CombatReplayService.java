package ti4.contest.replay.service;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
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
import ti4.service.combat.CombatRollType;
import ti4.service.combat.CombatUnitSelectionHelper;
import ti4.service.emoji.TechEmojis;
import ti4.spring.context.SpringContext;
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
    private final CombatReplaySideBetTriggerService sideBetTriggerService;
    private volatile SelectionSnapshot selectionSnapshot;

    @PostConstruct
    void initializeSelectionSnapshot() {
        refreshSelectionSnapshot();
    }

    public void onSpaceCombatStarted(Game game, Player attacker, Player defender, Tile tile) {
        boolean trackAllCombatsAsCandidates = settings.getRuntime().isTrackAllCombatsAsCandidates();
        if (!trackAllCombatsAsCandidates
                && (!LazaxCombatSupport.isEligibleGame(game)
                        || !LazaxCombatSupport.isEligibleCombat(game, attacker, defender, tile))) {
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
                tile,
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

    @Transactional
    public void mirrorCombatRoll(
            Game game,
            Player player,
            Player opponent,
            Tile tile,
            String message,
            CombatRollType rollType,
            boolean whiff,
            boolean slam) {
        CombatCandidateEntity candidate = getTrackingCandidate(game, tile.getPosition());
        if (candidate == null || !matchesParticipants(candidate, player, opponent)) return;

        int round = getCurrentRound(game, candidate);
        updateCandidateRollTracking(candidate, player, rollType, whiff, slam, round);
        appendDiscordEvent(
                candidate, CombatCandidateEventType.ROLL, round, player.getFaction(), "## Roll Update\n" + message);
        appendSideBetTriggerEvents(
                candidate, sideBetTriggerService.fromRoll(candidate, player, rollType, whiff, slam, round));
    }

    @Transactional
    public void mirrorCombatEvent(
            Game game, Player player, String header, String name, MessageEmbed embed, String sourceChannelName) {
        mirrorCombatEvent(game, player, header, name, embed, sourceChannelName, CombatReplayTrackedEvent.NONE);
    }

    @Transactional
    public void mirrorCombatEvent(
            Game game,
            Player player,
            String header,
            String name,
            MessageEmbed embed,
            String sourceChannelName,
            CombatReplayTrackedEvent trackedEvent) {
        CombatCandidateEntity candidate = resolveCandidateForMirrorEvent(game, player, sourceChannelName);
        if (candidate == null) return;
        updateCandidateEventTracking(candidate, player, trackedEvent);

        String message = "## " + header + "\n" + player.getRepresentation() + " " + name;
        appendDiscordEvent(candidate, CombatCandidateEventType.INFO, null, player.getFaction(), message, embed);
        appendSideBetTriggerEvents(candidate, sideBetTriggerService.fromTrackedEvent(candidate, player, trackedEvent));
    }

    public void mirrorRetreatDeclared(Game game, Player player, String sourceChannelName) {
        CombatCandidateEntity candidate = resolveCandidateForMirrorEvent(game, player, sourceChannelName);
        if (candidate == null) return;

        appendDiscordEvent(
                candidate,
                CombatCandidateEventType.INFO,
                getCurrentRound(game, candidate),
                player.getFaction(),
                "## Retreat\n" + player.getRepresentationNoPing() + " announced a retreat.");
    }

    public void mirrorRetreatResolved(Game game, Player player, String destination, String sourceChannelName) {
        CombatCandidateEntity candidate = resolveCandidateForMirrorEvent(game, player, sourceChannelName);
        if (candidate == null) return;

        appendDiscordEvent(
                candidate,
                CombatCandidateEventType.INFO,
                getCurrentRound(game, candidate),
                player.getFaction(),
                "## Retreat\n" + player.getRepresentationNoPing() + " retreated to " + destination + ".");
    }

    public void mirrorAssaultCannonAssigned(Game game, Player player, String sourceChannelName) {
        CombatCandidateEntity candidate = resolveCandidateForMirrorEvent(game, player, sourceChannelName);
        if (candidate == null) return;

        appendDiscordEvent(
                candidate,
                CombatCandidateEventType.INFO,
                getCurrentRound(game, candidate),
                player.getFaction(),
                "## Combat Ability\n"
                        + player.getRepresentationNoPing()
                        + " used _Assault Cannon_ "
                        + TechEmojis.WarfareTech
                        + ".");
    }

    public void mirrorGravitonExhausted(Game game, Player player, String sourceChannelName) {
        CombatCandidateEntity candidate = resolveCandidateForMirrorEvent(game, player, sourceChannelName);
        if (candidate == null) return;

        appendDiscordEvent(
                candidate,
                CombatCandidateEventType.INFO,
                getCurrentRound(game, candidate),
                player.getFaction(),
                "## Combat Ability\n"
                        + player.getRepresentationNoPing()
                        + " exhausted _Graviton Laser System_ "
                        + TechEmojis.CyberneticTech
                        + ".");
    }

    private boolean evaluateCandidateCompletion(Game game, CombatCandidateEntity candidate) {
        Tile tile = game.getTileByPosition(candidate.getTilePosition());
        if (tile == null) return false;

        List<Player> remainingShipPlayers = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile).stream()
                .filter(Player::isRealPlayer)
                .filter(player -> !player.isDummy())
                .toList();
        boolean hasRecordedRoll = hasRecordedRoll(candidate);
        return switch (remainingShipPlayers.size()) {
            case 0 -> {
                cancelCandidate(
                        game,
                        candidate,
                        hasRecordedRoll
                                ? "The tracked space combat ended with no ships remaining."
                                : "The tracked space combat ended before any dice were rolled.");
                yield true;
            }
            case 1 -> {
                if (!hasRecordedRoll) {
                    cancelCandidate(game, candidate, "The tracked space combat ended before any dice were rolled.");
                    yield true;
                }
                Player winner = remainingShipPlayers.getFirst();
                resolveCandidate(game, candidate, tile, winner, loserFaction(candidate, winner));
                yield true;
            }
            case 2 -> {
                if (containsOnlyOriginalParticipants(candidate, remainingShipPlayers)) {
                    yield false;
                }
                cancelCandidate(
                        game, candidate, "The tracked space combat drifted away from the original participants.");
                yield true;
            }
            default -> {
                cancelCandidate(game, candidate, "The tracked space combat no longer has exactly two sides.");
                yield true;
            }
        };
    }

    private boolean containsOnlyOriginalParticipants(
            CombatCandidateEntity candidate, List<Player> remainingShipPlayers) {
        return remainingShipPlayers.stream()
                .map(Player::getFaction)
                .allMatch(faction -> faction.equalsIgnoreCase(candidate.getAttackerFaction())
                        || faction.equalsIgnoreCase(candidate.getDefenderFaction()));
    }

    private String loserFaction(CombatCandidateEntity candidate, Player winner) {
        return winner.getFaction().equalsIgnoreCase(candidate.getAttackerFaction())
                ? candidate.getDefenderFaction()
                : candidate.getAttackerFaction();
    }

    private void resolveCandidate(
            Game game, CombatCandidateEntity candidate, Tile tile, Player winner, String loserFaction) {
        CombatObservationEntity observation =
                observationRepository.findById(candidate.getObservationId()).orElse(null);
        if (observation == null) return;
        ResolutionState resolution = buildResolutionState(game, candidate, tile);
        if (resolution == null) {
            cancelCandidate(game, candidate, "The tracked space combat could not be resolved cleanly.");
            return;
        }
        int roundsObserved = candidateEventRepository
                .findMaxRoundNumberByCandidateId(candidate.getId())
                .orElse(0);
        candidate.setStatus(CombatCandidateStatus.RESOLVED);
        candidate.setResolvedAt(LocalDateTime.now());
        candidate.setWinnerFaction(winner.getFaction());
        candidate.setLoserFaction(loserFaction);
        candidate.setResolutionReason("Winner determined from remaining fleets.");
        candidate.setWinnerOneHpRemaining(hasExactlyOneHpRemaining(resolution, candidate, winner.getFaction()));
        candidate.setPromotionScore(computePromotionScore(
                observation,
                resolution.attackerRemainingStrength(),
                resolution.defenderRemainingStrength(),
                winner.getFaction(),
                roundsObserved));
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
                        + "Game " + game.getName() + ": [Open Game](https://asyncti4.com/game/" + game.getName()
                        + ")");
        appendSideBetTriggerEvents(
                candidate, CombatCandidateEventType.RESOLVED, sideBetTriggerService.fromResolution(candidate, winner));

        if (settings.getRuntime().isImmediatePromotionOnResolve()) {
            SpringContext.getBean(CombatReplayContestLifecycleService.class).forcePromoteCandidate(candidate.getId());
        }
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

    private ResolutionState buildResolutionState(Game game, CombatCandidateEntity candidate, Tile tile) {
        Player attacker = game.getPlayerFromColorOrFaction(candidate.getAttackerFaction());
        Player defender = game.getPlayerFromColorOrFaction(candidate.getDefenderFaction());
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        if (attacker == null || defender == null || space == null) {
            return null;
        }
        return new ResolutionState(
                LazaxCombatSupport.calculateFleetStrength(game, attacker, defender, tile, space),
                LazaxCombatSupport.calculateFleetStrength(game, defender, attacker, tile, space));
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

    public static double computePromotionScore(
            CombatObservationEntity observation,
            LazaxCombatSupport.FleetStrength attackerRemainingStrength,
            LazaxCombatSupport.FleetStrength defenderRemainingStrength,
            String winnerFaction,
            int roundsObserved) {
        double weakerHp = Math.min(observation.getAttackerHp(), observation.getDefenderHp());
        double weakerStrength = Math.min(observation.getAttackerStrength(), observation.getDefenderStrength());
        double strongerStrength = Math.max(observation.getAttackerStrength(), observation.getDefenderStrength());
        double winnerRemainingHp = winnerFaction.equalsIgnoreCase(observation.getAttackerFaction())
                ? attackerRemainingStrength.hp()
                : defenderRemainingStrength.hp();
        double winnerInitialHp = winnerFaction.equalsIgnoreCase(observation.getAttackerFaction())
                ? observation.getAttackerHp()
                : observation.getDefenderHp();
        double sizeFactor = Math.min(1.0, weakerHp / 8.0);
        double strengthRatio = safeRatio(weakerStrength, strongerStrength);
        double winnerSurvivalRatio = safeRatio(winnerRemainingHp, winnerInitialHp);
        double roundScore = Math.sqrt(Math.max(0, roundsObserved)) * sizeFactor;
        double openingBalanceScore = 0.9 * Math.pow(strengthRatio, 3.0);
        double endingTensionScore = winnerRemainingHp <= 0 ? 0.0 : 5.0 * Math.exp(-6.0 * winnerSurvivalRatio);
        double defenderWinBonus = winnerFaction.equalsIgnoreCase(observation.getDefenderFaction()) ? 0.5 : 0.0;
        return roundScore + openingBalanceScore + endingTensionScore + defenderWinBonus;
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

    private void updateCandidateRollTracking(
            CombatCandidateEntity candidate,
            Player player,
            CombatRollType rollType,
            boolean whiff,
            boolean slam,
            int round) {
        if (candidate == null || player == null) return;
        boolean rolledAfb = rollType == CombatRollType.AFB;
        boolean afbWhiff = rolledAfb && whiff;
        boolean roundOneCombatRoll = rollType == CombatRollType.combatround && round == 1;
        boolean roundOneWhiff = roundOneCombatRoll && whiff;
        boolean roundOneSlam = roundOneCombatRoll && slam;
        if (!rolledAfb && !afbWhiff && !roundOneWhiff && !roundOneSlam) return;
        if (player.getFaction().equalsIgnoreCase(candidate.getAttackerFaction())) {
            markAttackerRollFlags(candidate, rolledAfb, afbWhiff, roundOneWhiff, roundOneSlam);
        } else {
            markDefenderRollFlags(candidate, rolledAfb, afbWhiff, roundOneWhiff, roundOneSlam);
        }
        candidateRepository.save(candidate);
    }

    private void appendSideBetTriggerEvents(
            CombatCandidateEntity candidate,
            List<CombatReplaySideBetTriggerService.SideBetTriggerAnnouncement> announcements) {
        appendSideBetTriggerEvents(candidate, CombatCandidateEventType.INFO, announcements);
    }

    private void appendSideBetTriggerEvents(
            CombatCandidateEntity candidate,
            CombatCandidateEventType eventType,
            List<CombatReplaySideBetTriggerService.SideBetTriggerAnnouncement> announcements) {
        for (CombatReplaySideBetTriggerService.SideBetTriggerAnnouncement announcement : announcements) {
            appendDiscordEvent(candidate, eventType, null, announcement.faction(), announcement.message());
        }
    }

    private void markAttackerRollFlags(
            CombatCandidateEntity candidate,
            boolean rolledAfb,
            boolean afbWhiff,
            boolean roundOneWhiff,
            boolean roundOneSlam) {
        candidate.setAttackerRolledAfb(Boolean.TRUE.equals(candidate.getAttackerRolledAfb()) || rolledAfb);
        candidate.setAttackerAfbWhiff(Boolean.TRUE.equals(candidate.getAttackerAfbWhiff()) || afbWhiff);
        candidate.setAttackerRoundOneWhiff(Boolean.TRUE.equals(candidate.getAttackerRoundOneWhiff()) || roundOneWhiff);
        candidate.setAttackerRoundOneSlam(Boolean.TRUE.equals(candidate.getAttackerRoundOneSlam()) || roundOneSlam);
    }

    private void markDefenderRollFlags(
            CombatCandidateEntity candidate,
            boolean rolledAfb,
            boolean afbWhiff,
            boolean roundOneWhiff,
            boolean roundOneSlam) {
        candidate.setDefenderRolledAfb(Boolean.TRUE.equals(candidate.getDefenderRolledAfb()) || rolledAfb);
        candidate.setDefenderAfbWhiff(Boolean.TRUE.equals(candidate.getDefenderAfbWhiff()) || afbWhiff);
        candidate.setDefenderRoundOneWhiff(Boolean.TRUE.equals(candidate.getDefenderRoundOneWhiff()) || roundOneWhiff);
        candidate.setDefenderRoundOneSlam(Boolean.TRUE.equals(candidate.getDefenderRoundOneSlam()) || roundOneSlam);
    }

    private void updateCandidateEventTracking(
            CombatCandidateEntity candidate, Player player, CombatReplayTrackedEvent trackedEvent) {
        if (candidate == null
                || player == null
                || trackedEvent == null
                || trackedEvent == CombatReplayTrackedEvent.NONE) return;
        boolean moraleBoost = trackedEvent == CombatReplayTrackedEvent.MORALE_BOOST;
        boolean shieldsHolding = trackedEvent == CombatReplayTrackedEvent.SHIELDS_HOLDING;
        boolean rout = trackedEvent == CombatReplayTrackedEvent.ROUT;
        if (rout) {
            candidate.setPromotionStatus(CombatCandidatePromotionStatus.EXPIRED);
            candidate.setCancellationReason(firstNonBlank(
                    candidate.getCancellationReason(), "Ineligible for promotion because Rout was played."));
        }
        boolean attacker = player.getFaction().equalsIgnoreCase(candidate.getAttackerFaction());
        if (attacker) {
            candidate.setAttackerPlayedMoraleBoost(
                    Boolean.TRUE.equals(candidate.getAttackerPlayedMoraleBoost()) || moraleBoost);
            candidate.setAttackerPlayedShieldsHolding(
                    Boolean.TRUE.equals(candidate.getAttackerPlayedShieldsHolding()) || shieldsHolding);
        } else {
            candidate.setDefenderPlayedMoraleBoost(
                    Boolean.TRUE.equals(candidate.getDefenderPlayedMoraleBoost()) || moraleBoost);
            candidate.setDefenderPlayedShieldsHolding(
                    Boolean.TRUE.equals(candidate.getDefenderPlayedShieldsHolding()) || shieldsHolding);
        }
        candidateRepository.save(candidate);
    }

    private boolean hasExactlyOneHpRemaining(
            ResolutionState resolution, CombatCandidateEntity candidate, String winnerFaction) {
        if (winnerFaction == null || resolution == null) return false;
        double hp = winnerFaction.equalsIgnoreCase(candidate.getAttackerFaction())
                ? resolution.attackerRemainingStrength().hp()
                : resolution.defenderRemainingStrength().hp();
        return Math.abs(hp - 1.0) < 0.0001;
    }

    private int countDestroyersInCombat(Tile tile, Player player) {
        if (tile == null || player == null) return 0;
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        if (space == null) return 0;
        return CombatUnitSelectionHelper.collectCombatRoundUnits(tile, space, player).entrySet().stream()
                .filter(entry -> entry.getValue() != null
                        && entry.getValue() > 0
                        && entry.getKey() != null
                        && "destroyer".equalsIgnoreCase(entry.getKey().getBaseType()))
                .mapToInt(entry -> entry.getValue())
                .sum();
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
        if (settings.getRuntime().isTrackAllCombatsAsCandidates()) {
            return getTrackingCandidate(game, tile.getPosition()) == null;
        }
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
            CombatObservationEntity observation,
            Player attacker,
            Player defender,
            Tile tile,
            String initialRenderSnapshotJson) {
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
        candidate.setPreReplayContextText(LazaxCombatSupport.formatCombatTechSummary(
                attacker.getGame().getTileByPosition(observation.getTilePosition()), attacker, defender));
        candidate.setInitialRenderSnapshotJson(initialRenderSnapshotJson);
        candidate.setSideBetCompatible(true);
        candidate.setAttackerDestroyerCount(countDestroyersInCombat(tile, attacker));
        candidate.setDefenderDestroyerCount(countDestroyersInCombat(tile, defender));
        candidate.setAttackerHasAssaultCannon(hasAssaultCannon(attacker));
        candidate.setDefenderHasAssaultCannon(hasAssaultCannon(defender));
        return candidate;
    }

    private boolean hasAssaultCannon(Player player) {
        return player != null && player.hasTech("asc");
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

    private static double safeRatio(double weaker, double stronger) {
        if (stronger <= 0) return 0.0;
        return Math.max(0.0, Math.min(1.0, weaker / stronger));
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
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

    private record ResolutionState(
            LazaxCombatSupport.FleetStrength attackerRemainingStrength,
            LazaxCombatSupport.FleetStrength defenderRemainingStrength) {}
}
