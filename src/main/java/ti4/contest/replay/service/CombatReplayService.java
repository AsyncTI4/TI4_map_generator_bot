package ti4.contest.replay.service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatCandidateEventType;
import ti4.contest.replay.core.CombatCandidatePromotionStatus;
import ti4.contest.replay.core.CombatCandidateStatus;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayDecoys;
import ti4.contest.replay.core.CombatReplaySelection;
import ti4.contest.replay.core.CombatReplayTrackedEvent;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.core.CombatSideState;
import ti4.contest.replay.core.LazaxCombatSupport;
import ti4.contest.replay.core.renderers.CombatReplayTileRenderer;
import ti4.contest.replay.dispatch.ReplayDispatchPayload;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatObservationEntity;
import ti4.contest.replay.repository.CombatCandidateEventRepository;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatObservationRepository;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.game.persistence.GameManager;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.CombatUnitSelectionHelper;

/**
 * Observes live combats, scores them against the current replay thresholds, and records replay candidates/events.
 */
@Service
@RequiredArgsConstructor
public class CombatReplayService {

    private static final Pattern SYSTEM_TILE_PATTERN = Pattern.compile("-system-([^-]+)-");
    private static final Set<CombatCandidateStatus> OPEN_CANDIDATE_STATUSES =
            EnumSet.of(CombatCandidateStatus.TRACKING, CombatCandidateStatus.PENDING_RESOLUTION);
    private static final ThreadLocal<PreInteractionSnapshot> preInteractionSnapshot = new ThreadLocal<>();

    private final CombatContestSettings settings;
    private final CombatObservationRepository observationRepository;
    private final CombatCandidateRepository candidateRepository;
    private final CombatCandidateEventRepository candidateEventRepository;
    private final CombatReplayEventAppender eventAppender;
    private final CombatReplaySideBetTriggerService sideBetTriggerService;
    private CombatReplaySelection selection;

    @PostConstruct
    void initializeSelectionSnapshot() {
        selection = new CombatReplaySelection(settings);
        refreshSelectionSnapshot();
    }

    public PreInteractionSnapshot capturePreInteractionSnapshot(Game game) {
        if (game == null) return PreInteractionSnapshot.empty();

        Map<Long, CandidateInitialSnapshot> snapshots = new HashMap<>();
        for (CombatCandidateEntity candidate : getOpenCandidates(game)) {
            if (isInitialSnapshotCaptured(candidate)) continue;
            CandidateInitialSnapshot snapshot = captureCandidateInitialSnapshot(game, candidate);
            if (snapshot != null) {
                snapshots.put(candidate.getId(), snapshot);
            }
        }
        return new PreInteractionSnapshot(snapshots);
    }

    public void setPreInteractionSnapshot(PreInteractionSnapshot snapshot) {
        preInteractionSnapshot.set(snapshot);
    }

    public void clearPreInteractionSnapshot() {
        preInteractionSnapshot.remove();
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

        CombatReplaySelection.Evaluation evaluation = selection().evaluate(snapshot);
        CombatObservationEntity observation = buildObservation(game, attacker, defender, tile, snapshot, evaluation);
        boolean eligible = isEligibleCandidate(game, attacker, defender, tile, evaluation);
        observationRepository.save(observation);

        if (!eligible) return;

        CombatCandidateEntity candidate = buildCandidate(observation, attacker, defender, tile);
        candidateRepository.save(candidate);
    }

    public void onButtonInteractionSettled(Game game, Player player, ButtonInteractionEvent event) {
        for (CombatCandidateEntity candidate : getOpenCandidates(game)) {
            if (candidate.getStatus() == CombatCandidateStatus.PENDING_RESOLUTION
                    && !candidate
                            .getPendingResolutionStartedAt()
                            .plusSeconds(settings.getReplayExecution().getPendingResolutionWindowSeconds())
                            .isAfter(LocalDateTime.now())) {
                finalizePendingResolutionCandidate(game, candidate);
                continue;
            }

            boolean trackedHitAssignment = trackHitAssignments(game, player, event, candidate);
            if (candidate.getStatus() == CombatCandidateStatus.TRACKING) {
                evaluateCandidateCompletion(game, candidate);
            } else if (trackedHitAssignment) {
                cancelPendingResolutionIfDrawn(game, candidate);
            }
        }
    }

    public void finalizeExpiredPendingResolutionCandidates() {
        LocalDateTime cutoff =
                LocalDateTime.now().minusSeconds(settings.getReplayExecution().getPendingResolutionWindowSeconds());
        for (CombatCandidateEntity candidate : candidateRepository.findByStatusAndPendingResolutionStartedAtBefore(
                CombatCandidateStatus.PENDING_RESOLUTION, cutoff)) {
            Game game = loadGame(candidate.getGameName());
            finalizePendingResolutionCandidate(game, candidate);
        }
    }

    public void mirrorCombatRoll(
            Game game,
            Player player,
            Player opponent,
            Tile tile,
            String message,
            CombatRollType rollType,
            boolean whiff,
            boolean slam,
            CombatRollPayload payload) {
        CombatCandidateEntity candidate = getTrackingCandidate(game, tile.getPosition());
        if (candidate == null || !isReplayRoll(rollType)) return;
        if (rollType != CombatRollType.SpaceCannonOffence && !matchesParticipants(candidate, player, opponent)) return;

        ensureInitialSnapshot(candidate, game);
        int round = getCurrentRound(game, candidate);
        updateCandidateRollTracking(candidate, player, rollType, whiff, slam, round);
        appendDiscordEvent(
                candidate,
                CombatCandidateEventType.ROLL,
                round,
                player.getFaction(),
                "## Roll Update\n" + message,
                payload);
        appendSideBetTriggerEvents(
                candidate, sideBetTriggerService.fromRoll(candidate, player, rollType, whiff, slam, round));
    }

    private boolean isReplayRoll(CombatRollType rollType) {
        return rollType == CombatRollType.combatround
                || rollType == CombatRollType.AFB
                || rollType == CombatRollType.SpaceCannonOffence;
    }

    public void mirrorCombatEvent(
            Game game, Player player, String header, String name, MessageEmbed embed, String sourceChannelName) {
        mirrorCombatEvent(game, player, header, name, embed, sourceChannelName, CombatReplayTrackedEvent.NONE);
    }

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
        ensureInitialSnapshot(candidate, game);
        updateCandidateEventTracking(candidate, player, trackedEvent);

        String message = "## " + header + "\n" + player.getRepresentation() + " " + name;
        appendDiscordEvent(candidate, CombatCandidateEventType.INFO, null, player.getFaction(), message, embed);
        appendSideBetTriggerEvents(candidate, sideBetTriggerService.fromTrackedEvent(candidate, player, trackedEvent));
    }

    public void mirrorLeaderPlayed(Game game, Player player, String leaderId, String sourceChannelName) {
        CombatCandidateEntity candidate = resolveCandidateForMirrorEvent(game, player, sourceChannelName);
        if (candidate == null) return;
        ensureInitialSnapshot(candidate, game);

        appendStructuredEvent(
                candidate,
                CombatCandidateEventType.INFO,
                null,
                player.getFaction(),
                "## Leader\n" + player.getRepresentation() + " played a leader.",
                ReplayDispatchPayload.leaderPlayed(leaderId));
    }

    public void mirrorActionCardPlayed(
            Game game,
            Player player,
            String actionCardId,
            String sourceChannelName,
            CombatReplayTrackedEvent trackedEvent) {
        CombatCandidateEntity candidate = resolveCandidateForMirrorEvent(game, player, sourceChannelName);
        if (candidate == null) return;
        ensureInitialSnapshot(candidate, game);
        updateCandidateEventTracking(candidate, player, trackedEvent);

        appendStructuredEvent(
                candidate,
                CombatCandidateEventType.INFO,
                null,
                player.getFaction(),
                "## Action Card\n" + player.getRepresentation() + " played an action card.",
                ReplayDispatchPayload.actionCardPlayed(actionCardId));
        appendSideBetTriggerEvents(candidate, sideBetTriggerService.fromTrackedEvent(candidate, player, trackedEvent));
    }

    public void mirrorRetreatDeclared(Game game, Player player, String sourceChannelName) {
        CombatCandidateEntity candidate = resolveCandidateForMirrorEvent(game, player, sourceChannelName);
        if (candidate == null) return;
        ensureInitialSnapshot(candidate, game);

        appendStructuredEvent(
                candidate,
                CombatCandidateEventType.INFO,
                getCurrentRound(game, candidate),
                player.getFaction(),
                "## Retreat\n" + player.getRepresentationNoPing() + " announced a retreat.",
                ReplayDispatchPayload.retreatDeclared());
    }

    public void mirrorRetreatResolved(Game game, Player player, String destination, String sourceChannelName) {
        CombatCandidateEntity candidate = resolveCandidateForMirrorEvent(game, player, sourceChannelName);
        if (candidate == null) return;
        ensureInitialSnapshot(candidate, game);

        appendStructuredEvent(
                candidate,
                CombatCandidateEventType.INFO,
                getCurrentRound(game, candidate),
                player.getFaction(),
                "## Retreat\n" + player.getRepresentationNoPing() + " retreated to " + destination + ".",
                ReplayDispatchPayload.retreatResolved(destination));
    }

    public void mirrorAssaultCannonAssigned(Game game, Player player, String sourceChannelName) {
        CombatCandidateEntity candidate = resolveCandidateForMirrorEvent(game, player, sourceChannelName);
        if (candidate == null) return;
        ensureInitialSnapshot(candidate, game);

        appendStructuredEvent(
                candidate,
                CombatCandidateEventType.INFO,
                getCurrentRound(game, candidate),
                player.getFaction(),
                "## Combat Ability\n" + player.getRepresentationNoPing() + " used _Assault Cannon_.",
                ReplayDispatchPayload.techPlayed("asc"));
    }

    public void mirrorGravitonExhausted(Game game, Player player, String sourceChannelName) {
        CombatCandidateEntity candidate = resolveCandidateForMirrorEvent(game, player, sourceChannelName);
        if (candidate == null) return;
        ensureInitialSnapshot(candidate, game);

        appendStructuredEvent(
                candidate,
                CombatCandidateEventType.INFO,
                getCurrentRound(game, candidate),
                player.getFaction(),
                "## Combat Ability\n" + player.getRepresentationNoPing() + " exhausted _Graviton Laser System_.",
                ReplayDispatchPayload.techExhausted("gls"));
    }

    private boolean evaluateCandidateCompletion(Game game, CombatCandidateEntity candidate) {
        Tile tile = game.getTileByPosition(candidate.getTilePosition());
        if (tile == null) return false;

        List<Player> remainingShipPlayers = remainingShipPlayers(game, tile);
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
                markCandidatePendingResolution(candidate);
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

    private void markCandidatePendingResolution(CombatCandidateEntity candidate) {
        candidate.setStatus(CombatCandidateStatus.PENDING_RESOLUTION);
        candidate.setPendingResolutionStartedAt(LocalDateTime.now());
        candidate.setResolutionReason("Combat pending final hit assignment.");
        candidateRepository.save(candidate);
    }

    private void cancelPendingResolutionIfDrawn(Game game, CombatCandidateEntity candidate) {
        Tile tile = game.getTileByPosition(candidate.getTilePosition());
        if (tile != null && remainingShipPlayers(game, tile).isEmpty()) {
            resolveDrawCandidate(game, candidate, tile);
        }
    }

    private void finalizePendingResolutionCandidate(Game game, CombatCandidateEntity candidate) {
        if (game == null) {
            cancelCandidate(null, candidate, "The tracked space combat could not be loaded for resolution.");
            return;
        }

        Tile tile = game.getTileByPosition(candidate.getTilePosition());
        if (tile == null) {
            cancelCandidate(game, candidate, "The tracked space combat tile could not be found.");
            return;
        }

        List<Player> remainingShipPlayers = remainingShipPlayers(game, tile);
        switch (remainingShipPlayers.size()) {
            case 0 -> resolveDrawCandidate(game, candidate, tile);
            case 1 -> {
                Player winner = remainingShipPlayers.getFirst();
                resolveCandidate(game, candidate, tile, winner, loserFaction(candidate, winner));
            }
            case 2 -> {
                if (containsOnlyOriginalParticipants(candidate, remainingShipPlayers)) {
                    cancelCandidate(
                            game,
                            candidate,
                            "The tracked space combat was still unresolved after the pending-resolution window.");
                } else {
                    cancelCandidate(
                            game, candidate, "The tracked space combat drifted away from the original participants.");
                }
            }
            default -> cancelCandidate(game, candidate, "The tracked space combat no longer has exactly two sides.");
        }
    }

    private List<Player> remainingShipPlayers(Game game, Tile tile) {
        List<Player> remainingShipPlayers = new ArrayList<>();
        for (Player player : ButtonHelper.getPlayersWithShipsInTheSystem(game, tile)) {
            if (player.isRealPlayer() && !player.isDummy()) {
                remainingShipPlayers.add(player);
            }
        }
        return remainingShipPlayers;
    }

    private boolean containsOnlyOriginalParticipants(
            CombatCandidateEntity candidate, List<Player> remainingShipPlayers) {
        for (Player player : remainingShipPlayers) {
            String faction = player.getFaction();
            if (!faction.equalsIgnoreCase(candidate.getAttackerFaction())
                    && !faction.equalsIgnoreCase(candidate.getDefenderFaction())) {
                return false;
            }
        }
        return true;
    }

    private String loserFaction(CombatCandidateEntity candidate, Player winner) {
        return winner.getFaction().equalsIgnoreCase(candidate.getAttackerFaction())
                ? candidate.getDefenderFaction()
                : candidate.getAttackerFaction();
    }

    private void resolveCandidate(
            Game game, CombatCandidateEntity candidate, Tile tile, Player winner, String loserFaction) {
        InitialCombatStats initialStats = initialCombatStats(candidate);
        if (initialStats == null) {
            cancelCandidate(
                    game, candidate, "The tracked space combat initial snapshot could not be resolved cleanly.");
            return;
        }
        applyInitialCombatStats(candidate, initialStats);
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
                candidate,
                initialStats,
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
                CombatReplayTileRenderer.captureUnitStateSnapshot(game, tile.getPosition()),
                "## Contest Result\n"
                        + winner.getFactionEmoji() + " " + winner.getUserName() + " won the space combat in "
                        + tile.getRepresentationForButtons() + ".\n"
                        + "Game " + game.getName() + ": [Open Game](https://asyncti4.com/game/" + game.getName()
                        + ")");
        appendSideBetTriggerEvents(
                candidate, CombatCandidateEventType.RESOLVED, sideBetTriggerService.fromResolution(candidate, winner));
    }

    private void resolveDrawCandidate(Game game, CombatCandidateEntity candidate, Tile tile) {
        int roundsObserved = candidateEventRepository
                .findMaxRoundNumberByCandidateId(candidate.getId())
                .orElse(0);
        InitialCombatStats initialStats = initialCombatStats(candidate);

        candidate.setStatus(CombatCandidateStatus.RESOLVED);
        candidate.setResolvedAt(LocalDateTime.now());
        candidate.setWinnerFaction(null);
        candidate.setLoserFaction(null);
        candidate.setResolutionReason("Combat ended in a draw after pending hit assignment.");
        candidate.setWinnerOneHpRemaining(false);
        if (initialStats != null) {
            applyInitialCombatStats(candidate, initialStats);
            candidate.setPromotionScore(computeDrawPromotionScore(initialStats, roundsObserved));
        }
        candidateRepository.save(candidate);

        appendTileRenderEvent(
                candidate,
                CombatCandidateEventType.RESOLVED,
                roundsObserved,
                null,
                tile.getPosition(),
                CombatReplayTileRenderer.captureUnitStateSnapshot(game, tile.getPosition()),
                "## Contest Result\nThe space combat in " + tile.getRepresentationForButtons()
                        + " ended in a draw with no ships remaining.\n"
                        + "Game " + game.getName() + ": [Open Game](https://asyncti4.com/game/" + game.getName()
                        + ")");
    }

    private void cancelCandidate(Game game, CombatCandidateEntity candidate, String reason) {
        candidate.setStatus(CombatCandidateStatus.CANCELLED);
        candidate.setResolvedAt(LocalDateTime.now());
        candidate.setCancellationReason(reason);
        candidate.setPromotionStatus(CombatCandidatePromotionStatus.EXPIRED);
        candidateRepository.save(candidate);

        appendDiscordEvent(candidate, CombatCandidateEventType.CANCELLED, null, null, "## Contest Closed\n" + reason);
    }

    private boolean trackHitAssignments(
            Game game, Player player, ButtonInteractionEvent event, CombatCandidateEntity candidate) {
        if (player == null || !isSpaceCombatHitAssignment(game, player, event)) return false;
        if (!candidate
                .getTilePosition()
                .equals(getTilePosition(event.getButton().getCustomId()))) return false;
        if (!matchesParticipant(candidate, player)) return false;

        ensureInitialSnapshot(candidate, game);
        int round = getCurrentRound(game, candidate);
        if (candidate.getStatus() == CombatCandidateStatus.TRACKING
                && candidateEventRepository.existsByCandidateIdAndEventTypeAndActorFactionAndRoundNumber(
                        candidate.getId(), CombatCandidateEventType.HIT_ASSIGN, player.getFaction(), round)) {
            return true;
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
                        CombatReplayTileRenderer.captureUnitStateSnapshot(game, candidate.getTilePosition())));
        return true;
    }

    private void ensureInitialSnapshot(CombatCandidateEntity candidate, Game fallbackGame) {
        if (isInitialSnapshotCaptured(candidate)) return;

        CandidateInitialSnapshot snapshot = null;
        PreInteractionSnapshot currentSnapshot = preInteractionSnapshot.get();
        if (currentSnapshot != null) {
            snapshot = currentSnapshot.forCandidate(candidate.getId());
        }
        if (snapshot == null) {
            snapshot = captureCandidateInitialSnapshot(fallbackGame, candidate);
        }
        if (snapshot == null) return;

        candidate.setPreReplayContextText(snapshot.preReplayContextText());
        candidate.setInitialRenderSnapshotJson(snapshot.initialRenderSnapshotJson());
        candidate.setReplayAbilitiesJson(snapshot.replayAbilitiesJson());
        candidate.setAttackerDestroyerCount(snapshot.attackerDestroyerCount());
        candidate.setDefenderDestroyerCount(snapshot.defenderDestroyerCount());
        candidate.setAttackerHasAssaultCannon(snapshot.attackerHasAssaultCannon());
        candidate.setDefenderHasAssaultCannon(snapshot.defenderHasAssaultCannon());
        candidate.setAttackerHp(snapshot.attackerHp());
        candidate.setDefenderHp(snapshot.defenderHp());
        candidate.setAttackerStrength(snapshot.attackerStrength());
        candidate.setDefenderStrength(snapshot.defenderStrength());
        candidateRepository.save(candidate);
    }

    private boolean isInitialSnapshotCaptured(CombatCandidateEntity candidate) {
        return candidate.getInitialRenderSnapshotJson() != null
                && !candidate.getInitialRenderSnapshotJson().isBlank();
    }

    private CandidateInitialSnapshot captureCandidateInitialSnapshot(Game game, CombatCandidateEntity candidate) {
        if (game == null) return null;
        Tile tile = game.getTileByPosition(candidate.getTilePosition());
        Player attacker = game.getPlayerFromColorOrFaction(candidate.getAttackerFaction());
        Player defender = game.getPlayerFromColorOrFaction(candidate.getDefenderFaction());
        if (tile == null || attacker == null || defender == null) return null;

        LazaxCombatSupport.SpaceCombatSnapshot combatSnapshot =
                LazaxCombatSupport.buildSpaceCombatSnapshot(game, attacker, defender, tile);
        if (combatSnapshot == null) return null;
        return new CandidateInitialSnapshot(
                LazaxCombatSupport.formatCombatTechSummary(tile, attacker, defender),
                CombatReplayTileRenderer.captureInitialSnapshot(game, tile.getPosition()),
                CombatReplayDecoys.buildJson(attacker, defender, tile, settings.isDecoysEnabled()),
                countDestroyersInCombat(tile, attacker),
                countDestroyersInCombat(tile, defender),
                hasAssaultCannon(attacker),
                hasAssaultCannon(defender),
                combatSnapshot.attackerStrength(),
                combatSnapshot.defenderStrength(),
                combatSnapshot.attackerHp(),
                combatSnapshot.defenderHp());
    }

    private ResolutionState buildResolutionState(Game game, CombatCandidateEntity candidate, Tile tile) {
        Player attacker = game.getPlayerFromColorOrFaction(candidate.getAttackerFaction());
        Player defender = game.getPlayerFromColorOrFaction(candidate.getDefenderFaction());
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        if (attacker == null || defender == null || space == null) {
            return null;
        }
        return new ResolutionState(
                LazaxCombatSupport.calculateFleetStrength(attacker, defender, tile, space),
                LazaxCombatSupport.calculateFleetStrength(defender, attacker, tile, space));
    }

    public void refreshSelectionSnapshot() {
        LocalDateTime now = LocalDateTime.now();
        List<CombatObservationEntity> window = observationRepository.findByStartedAtGreaterThanEqualOrderByStartedAtAsc(
                now.minusMinutes(settings.getCandidateSelection().getWindow().getLookbackMinutes()));
        selection().refresh(window);
    }

    public static double computePromotionScore(
            CombatCandidateEntity candidate,
            InitialCombatStats initialStats,
            LazaxCombatSupport.FleetStrength attackerRemainingStrength,
            LazaxCombatSupport.FleetStrength defenderRemainingStrength,
            String winnerFaction,
            int roundsObserved) {
        double weakerHp = Math.min(initialStats.attackerHp(), initialStats.defenderHp());
        double weakerStrength = Math.min(initialStats.attackerStrength(), initialStats.defenderStrength());
        double strongerStrength = Math.max(initialStats.attackerStrength(), initialStats.defenderStrength());
        double winnerRemainingHp = winnerFaction.equalsIgnoreCase(candidate.getAttackerFaction())
                ? attackerRemainingStrength.hp()
                : defenderRemainingStrength.hp();
        double winnerInitialHp = winnerFaction.equalsIgnoreCase(candidate.getAttackerFaction())
                ? initialStats.attackerHp()
                : initialStats.defenderHp();
        double sizeFactor = Math.min(1.0, weakerHp / 8.0);
        double strengthRatio = safeRatio(weakerStrength, strongerStrength);
        double winnerSurvivalRatio = safeRatio(winnerRemainingHp, winnerInitialHp);
        double roundScore = Math.sqrt(Math.max(0, roundsObserved)) * sizeFactor;
        double openingBalanceScore = 0.9 * Math.pow(strengthRatio, 3.0);
        double endingTensionScore = winnerRemainingHp <= 0 ? 0.0 : 5.0 * Math.exp(-6.0 * winnerSurvivalRatio);
        double defenderWinBonus = winnerFaction.equalsIgnoreCase(candidate.getDefenderFaction()) ? 0.5 : 0.0;
        return roundScore + openingBalanceScore + endingTensionScore + defenderWinBonus;
    }

    private static double computeDrawPromotionScore(InitialCombatStats initialStats, int roundsObserved) {
        double weakerHp = Math.min(initialStats.attackerHp(), initialStats.defenderHp());
        double weakerStrength = Math.min(initialStats.attackerStrength(), initialStats.defenderStrength());
        double strongerStrength = Math.max(initialStats.attackerStrength(), initialStats.defenderStrength());
        double sizeFactor = Math.min(1.0, weakerHp / 8.0);
        double strengthRatio = safeRatio(weakerStrength, strongerStrength);
        double roundScore = Math.sqrt(Math.max(0, roundsObserved)) * sizeFactor;
        double openingBalanceScore = 0.9 * Math.pow(strengthRatio, 3.0);
        return roundScore + openingBalanceScore + 5.0;
    }

    public static InitialCombatStats initialCombatStats(CombatCandidateEntity candidate) {
        if (candidate == null) return null;
        if (candidate.getAttackerStrength() != null
                && candidate.getDefenderStrength() != null
                && candidate.getAttackerHp() != null
                && candidate.getDefenderHp() != null) {
            return new InitialCombatStats(
                    candidate.getAttackerStrength(),
                    candidate.getDefenderStrength(),
                    candidate.getAttackerHp(),
                    candidate.getDefenderHp());
        }
        if (!CombatReplayTileRenderer.canRender(candidate.getInitialRenderSnapshotJson())) return null;

        Game snapshotGame = CombatReplayTileRenderer.render(
                candidate.getInitialRenderSnapshotJson(), candidate.getInitialRenderSnapshotJson());
        if (snapshotGame == null) return null;
        Tile tile = snapshotGame.getTileByPosition(candidate.getTilePosition());
        Player attacker = snapshotGame.getPlayerFromColorOrFaction(candidate.getAttackerFaction());
        Player defender = snapshotGame.getPlayerFromColorOrFaction(candidate.getDefenderFaction());
        UnitHolder space = tile == null ? null : tile.getUnitHolders().get(Constants.SPACE);
        if (tile == null || attacker == null || defender == null || space == null) return null;

        LazaxCombatSupport.FleetStrength attackerStrength =
                LazaxCombatSupport.calculateFleetStrength(attacker, defender, tile, space);
        LazaxCombatSupport.FleetStrength defenderStrength =
                LazaxCombatSupport.calculateFleetStrength(defender, attacker, tile, space);
        return new InitialCombatStats(
                attackerStrength.value(), defenderStrength.value(), attackerStrength.hp(), defenderStrength.hp());
    }

    private void applyInitialCombatStats(CombatCandidateEntity candidate, InitialCombatStats initialStats) {
        candidate.setAttackerStrength(initialStats.attackerStrength());
        candidate.setDefenderStrength(initialStats.defenderStrength());
        candidate.setAttackerHp(initialStats.attackerHp());
        candidate.setDefenderHp(initialStats.defenderHp());
    }

    public CombatReplaySelection.SelectionDebugView getSelectionDebugView() {
        return selection().debugView();
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
        boolean roundOneCombatRoll = rollType == CombatRollType.combatround && round == 1;
        if (!rolledAfb && !roundOneCombatRoll) return;
        CombatSideState.markRollFlags(
                candidate,
                player.getFaction(),
                rolledAfb,
                rolledAfb && whiff,
                roundOneCombatRoll && whiff,
                roundOneCombatRoll && slam);
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

    private void updateCandidateEventTracking(
            CombatCandidateEntity candidate, Player player, CombatReplayTrackedEvent trackedEvent) {
        if (candidate == null
                || player == null
                || trackedEvent == null
                || trackedEvent == CombatReplayTrackedEvent.NONE) return;
        if (trackedEvent == CombatReplayTrackedEvent.ROUT) {
            candidate.setPromotionStatus(CombatCandidatePromotionStatus.EXPIRED);
            candidate.setCancellationReason(firstNonBlank(
                    candidate.getCancellationReason(), "Ineligible for promotion because Rout was played."));
        }
        CombatSideState.markEventFlag(candidate, player.getFaction(), trackedEvent);
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
        int destroyers = 0;
        for (var entry : CombatUnitSelectionHelper.collectCombatRoundUnits(tile, space, player)
                .entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) continue;
            if ("destroyer".equalsIgnoreCase(entry.getKey().getBaseType())) {
                destroyers += entry.getValue();
            }
        }
        return destroyers;
    }

    private CombatObservationEntity buildObservation(
            Game game,
            Player attacker,
            Player defender,
            Tile tile,
            LazaxCombatSupport.SpaceCombatSnapshot snapshot,
            CombatReplaySelection.Evaluation evaluation) {
        CombatObservationEntity observation = new CombatObservationEntity();
        observation.setStartedAt(LocalDateTime.now());
        observation.setGameName(game.getName());
        observation.setTilePosition(tile.getPosition());
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

    private boolean isEligibleCandidate(
            Game game, Player attacker, Player defender, Tile tile, CombatReplaySelection.Evaluation evaluation) {
        if (settings.getRuntime().isTrackAllCombatsAsCandidates()) {
            return getOpenCandidate(game, tile.getPosition()) == null;
        }
        return evaluation.eligible()
                && !LazaxCombatSupport.hasExcludedFlagship(attacker, defender)
                && getOpenCandidate(game, tile.getPosition()) == null;
    }

    private CombatReplaySelection selection() {
        if (selection == null) {
            selection = new CombatReplaySelection(settings);
        }
        return selection;
    }

    private CombatCandidateEntity buildCandidate(
            CombatObservationEntity observation, Player attacker, Player defender, Tile tile) {
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setObservationId(observation.getId());
        candidate.setStatus(CombatCandidateStatus.TRACKING);
        candidate.setPromotionStatus(CombatCandidatePromotionStatus.PENDING);
        candidate.setNextEventSequence(1);
        candidate.setStartedAt(observation.getStartedAt());
        candidate.setGameName(observation.getGameName());
        candidate.setTilePosition(observation.getTilePosition());
        candidate.setAttackerFaction(attacker.getFaction());
        candidate.setDefenderFaction(defender.getFaction());
        candidate.setSideBetCompatible(true);
        candidate.setAttackerDestroyerCount(countDestroyersInCombat(tile, attacker));
        candidate.setDefenderDestroyerCount(countDestroyersInCombat(tile, defender));
        candidate.setAttackerHasAssaultCannon(hasAssaultCannon(attacker));
        candidate.setDefenderHasAssaultCannon(hasAssaultCannon(defender));
        candidate.setAttackerHp(observation.getAttackerHp());
        candidate.setDefenderHp(observation.getDefenderHp());
        return candidate;
    }

    private boolean hasAssaultCannon(Player player) {
        return player != null && player.hasTech("asc");
    }

    private List<CombatCandidateEntity> getOpenCandidates(Game game) {
        return candidateRepository.findByGameNameAndStatusIn(game.getName(), OPEN_CANDIDATE_STATUSES);
    }

    private CombatCandidateEntity getTrackingCandidate(Game game, String tilePosition) {
        return candidateRepository.findFirstByGameNameAndTilePositionAndStatus(
                game.getName(), tilePosition, CombatCandidateStatus.TRACKING);
    }

    private CombatCandidateEntity getOpenCandidate(Game game, String tilePosition) {
        return candidateRepository.findFirstByGameNameAndTilePositionAndStatusIn(
                game.getName(), tilePosition, OPEN_CANDIDATE_STATUSES);
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
                ReplayDispatchPayload.genericMessage(message));
    }

    private void appendDiscordEvent(
            CombatCandidateEntity candidate,
            CombatCandidateEventType eventType,
            Integer roundNumber,
            String actorFaction,
            String message,
            CombatRollPayload payload) {
        eventAppender.appendEvent(
                candidate,
                eventType,
                roundNumber,
                actorFaction,
                message,
                ReplayDispatchPayload.combatRoll(message, payload));
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
                ReplayDispatchPayload.genericMessage(message, embed));
    }

    private void appendStructuredEvent(
            CombatCandidateEntity candidate,
            CombatCandidateEventType eventType,
            Integer roundNumber,
            String actorFaction,
            String summaryText,
            ReplayDispatchPayload payload) {
        eventAppender.appendEvent(candidate, eventType, roundNumber, actorFaction, summaryText, payload);
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
            if (candidate != null) return candidate;
        }

        List<CombatCandidateEntity> candidates = candidateRepository.findTrackingCandidatesForFaction(
                game.getName(), CombatCandidateStatus.TRACKING, player.getFaction());
        if (candidates.size() == 1) return candidates.getFirst();

        List<CombatCandidateEntity> activeCandidates =
                candidateRepository.findByGameNameAndStatus(game.getName(), CombatCandidateStatus.TRACKING);
        return activeCandidates.size() == 1 ? activeCandidates.getFirst() : null;
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

    private Game loadGame(String gameName) {
        var managedGame = GameManager.getManagedGame(gameName);
        return managedGame == null ? null : managedGame.getGame();
    }

    public record PreInteractionSnapshot(Map<Long, CandidateInitialSnapshot> snapshotsByCandidateId) {

        public PreInteractionSnapshot {
            snapshotsByCandidateId = Map.copyOf(snapshotsByCandidateId);
        }

        public static PreInteractionSnapshot empty() {
            return new PreInteractionSnapshot(Map.of());
        }

        private CandidateInitialSnapshot forCandidate(Long candidateId) {
            return snapshotsByCandidateId.get(candidateId);
        }
    }

    public record CandidateInitialSnapshot(
            String preReplayContextText,
            String initialRenderSnapshotJson,
            String replayAbilitiesJson,
            int attackerDestroyerCount,
            int defenderDestroyerCount,
            boolean attackerHasAssaultCannon,
            boolean defenderHasAssaultCannon,
            double attackerStrength,
            double defenderStrength,
            double attackerHp,
            double defenderHp) {}

    public record InitialCombatStats(
            double attackerStrength, double defenderStrength, double attackerHp, double defenderHp) {}

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
