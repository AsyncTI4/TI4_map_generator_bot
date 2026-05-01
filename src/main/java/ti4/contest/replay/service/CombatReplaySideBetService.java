package ti4.contest.replay.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.core.CombatSideState;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatContestSideBetEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.entities.CombatReplayLeaderboardEntryEntity;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatContestSideBetRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayLeaderboardEntryRepository;
import ti4.game.Game;

/**
 * Handles side-bet placement, point accounting, and resolution against completed replay contests.
 */
@Service
@RequiredArgsConstructor
public class CombatReplaySideBetService {

    private final CombatContestSettings settings;
    private final CombatReplayContestRepository replayContestRepository;
    private final CombatCandidateRepository candidateRepository;
    private final CombatContestSideBetRepository sideBetRepository;
    private final CombatReplayLeaderboardEntryRepository leaderboardEntryRepository;
    private final CombatReplaySideBetPayoutService payoutService;
    private final CombatReplaySideBetUiService sideBetUiService;

    public boolean shouldShowButtons(CombatCandidateEntity candidate) {
        return sideBetUiService.shouldShowButtons(candidate);
    }

    public void postSideBetButtonsIfNeeded(
            MessageChannel channel, Game game, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        sideBetUiService.postSideBetButtonsIfNeeded(channel, game, contest, candidate);
    }

    public PlacementResult placeSideBet(
            ButtonInteractionEvent event, Long contestId, CombatSideBetType betType, String targetFaction) {
        if (contestId == null || event == null || event.getUser() == null) {
            return PlacementResult.rejected("Side bet context is incomplete.");
        }

        String userId = event.getUser().getId();
        String userName = event.getUser().getEffectiveName();
        PlacementPersistenceResult persisted = persistSideBet(contestId, betType, targetFaction, userId, userName);
        if (!persisted.result().accepted()) return persisted.result();

        sideBetUiService.refreshSummaryMessage(event.getMessageChannel(), persisted.contest(), persisted.candidate());
        String personalSummary = sideBetUiService.renderUserSummary(
                persisted.contest(),
                persisted.candidate(),
                userId,
                persisted.result().totalPoints());
        return PlacementResult.accepted(personalSummary, persisted.result().totalPoints());
    }

    private synchronized PlacementPersistenceResult persistSideBet(
            Long contestId, CombatSideBetType betType, String targetFaction, String userId, String userName) {
        CombatReplayContestEntity contest =
                replayContestRepository.findById(contestId).orElse(null);
        if (contest == null) {
            return PlacementPersistenceResult.rejected("This combat contest is no longer available.");
        }
        if (!settings.getSideBets().isEnableSideBets()) {
            return PlacementPersistenceResult.rejected("Side bets are disabled.");
        }
        if (contest.getReplayStartAt() == null || !LocalDateTime.now().isBefore(contest.getReplayStartAt())) {
            return PlacementPersistenceResult.rejected("The side-bet window is closed.");
        }

        CombatCandidateEntity candidate =
                candidateRepository.findById(contest.getCandidateId()).orElse(null);
        if (candidate == null || !Boolean.TRUE.equals(candidate.getSideBetCompatible())) {
            return PlacementPersistenceResult.rejected("This combat does not support side bets.");
        }
        if (!isValidBet(candidate, betType, targetFaction)) {
            return PlacementPersistenceResult.rejected("That side bet is not available for this combat.");
        }

        CombatReplayLeaderboardEntryEntity entry =
                leaderboardEntryRepository.findByDiscordUserId(userId).orElse(null);
        if (entry == null) {
            return PlacementPersistenceResult.rejected("You do not have any points to bet.");
        }

        int existingCount = sideBetRepository.countByContestIdAndDiscordUserId(contestId, userId);
        if (existingCount >= settings.getSideBets().getMaxBetsPerUser()) {
            return PlacementPersistenceResult.rejected(
                    "You have already placed the maximum number of side bets for this combat.");
        }

        int costPoints = settings.getSideBets().getCostPoints();
        int currentPoints = safeInt(entry.getTotalPoints());
        if (currentPoints <= 0) {
            return PlacementPersistenceResult.rejected("You do not have any points to bet.");
        }
        if (currentPoints < costPoints) {
            return PlacementPersistenceResult.rejected("You do not have enough points to place that side bet.");
        }

        entry.setDiscordUserName(userName);
        entry.setTotalPoints(currentPoints - costPoints);
        entry.setUpdatedAt(LocalDateTime.now());
        leaderboardEntryRepository.save(entry);

        CombatContestSideBetEntity sideBet = new CombatContestSideBetEntity();
        sideBet.setContestId(contestId);
        sideBet.setDiscordUserId(userId);
        sideBet.setDiscordUserName(userName);
        sideBet.setBetType(betType);
        sideBet.setTargetFaction(targetFaction);
        sideBet.setOfferedProfitPoints(payoutService.offeredPayout(contest, candidate, betType, targetFaction));
        sideBet.setPlacedAt(LocalDateTime.now());
        sideBetRepository.save(sideBet);

        return new PlacementPersistenceResult(PlacementResult.accepted("", entry.getTotalPoints()), contest, candidate);
    }

    public synchronized SideBetResolution resolveSideBets(
            CombatCandidateEntity candidate, CombatReplayContestEntity replayContest) {
        if (candidate == null || replayContest == null || replayContest.getId() == null) {
            return SideBetResolution.empty();
        }
        List<CombatContestSideBetEntity> sideBets = sideBetRepository.findByContestId(replayContest.getId());
        if (sideBets.isEmpty()) return SideBetResolution.empty();
        Map<String, CombatReplayLeaderboardEntryEntity> entriesByUser = loadLeaderboardEntries(sideBets);

        LocalDateTime now = LocalDateTime.now();
        List<ResolvedSideBet> resolved = new ArrayList<>();
        for (CombatContestSideBetEntity sideBet : sideBets) {
            boolean won = isWinningBet(candidate, sideBet);
            boolean firstResolution = sideBet.getResolvedAt() == null;
            int profitPoints = payoutService.resolvedProfitPoints(sideBet);
            CombatReplayLeaderboardEntryEntity entry = entriesByUser.get(sideBet.getDiscordUserId());
            sideBet.setResolvedAt(now);

            if (entry != null) {
                entry.setDiscordUserName(sideBet.getDiscordUserName());
                if (won && firstResolution) {
                    entry.setTotalPoints(safeInt(entry.getTotalPoints()) + profitPoints);
                }
                entry.setUpdatedAt(now);
            }

            if (entry != null && won && firstResolution) {
                resolved.add(new ResolvedSideBet(
                        sideBet.getDiscordUserId(),
                        sideBet.getDiscordUserName(),
                        sideBet.getBetType().label(),
                        profitPoints));
            }
        }

        sideBetRepository.saveAll(sideBets);
        leaderboardEntryRepository.saveAll(entriesByUser.values());
        return new SideBetResolution(resolved, List.copyOf(entriesByUser.values()));
    }

    private Map<String, CombatReplayLeaderboardEntryEntity> loadLeaderboardEntries(
            List<CombatContestSideBetEntity> sideBets) {
        Set<String> userIds = new HashSet<>();
        for (CombatContestSideBetEntity sideBet : sideBets) {
            if (sideBet.getDiscordUserId() != null) {
                userIds.add(sideBet.getDiscordUserId());
            }
        }

        Map<String, CombatReplayLeaderboardEntryEntity> entriesByUser = new HashMap<>();
        for (CombatReplayLeaderboardEntryEntity entry : leaderboardEntryRepository.findByDiscordUserIdIn(userIds)) {
            entriesByUser.put(entry.getDiscordUserId(), entry);
        }
        return entriesByUser;
    }

    public record PlacementResult(boolean accepted, String message, int totalPoints) {
        public static PlacementResult accepted(String message, int totalPoints) {
            return new PlacementResult(true, message, totalPoints);
        }

        public static PlacementResult rejected(String message) {
            return new PlacementResult(false, message, 0);
        }
    }

    public record ResolvedSideBet(String discordUserId, String discordUserName, String label, int profitPoints) {}

    private record PlacementPersistenceResult(
            PlacementResult result, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        static PlacementPersistenceResult rejected(String message) {
            return new PlacementPersistenceResult(PlacementResult.rejected(message), null, null);
        }
    }

    public record SideBetResolution(
            List<ResolvedSideBet> resolvedSideBets, List<CombatReplayLeaderboardEntryEntity> leaderboardEntries) {
        static SideBetResolution empty() {
            return new SideBetResolution(List.of(), List.of());
        }
    }

    private boolean isValidBet(CombatCandidateEntity candidate, CombatSideBetType betType, String targetFaction) {
        CombatSideState state = CombatSideState.forFaction(candidate, targetFaction);
        if (state == null || !betType.isAvailable(state.destroyerCount())) return false;
        if (betType == CombatSideBetType.AFB_WHIFF) return payoutService.hasAfbUnits(candidate, targetFaction);
        return betType != CombatSideBetType.AFB_SKIPPED || isAfbSkippedAvailable(candidate, targetFaction);
    }

    private boolean isWinningBet(CombatCandidateEntity candidate, CombatContestSideBetEntity sideBet) {
        CombatSideState state = CombatSideState.forFaction(candidate, sideBet.getTargetFaction());
        if (state == null) return false;
        CombatSideBetType betType = sideBet.getBetType();
        return switch (betType) {
            case AFB_SKIPPED -> isAfbSkippedAvailable(candidate, sideBet.getTargetFaction()) && state.skippedAfb();
            case AFB_WHIFF -> betType.isAvailable(state.destroyerCount()) && state.afbWhiff();
            case ROUND_ONE_WHIFF -> state.roundOneWhiff();
            case ROUND_ONE_SLAM -> state.roundOneSlam();
            case MORALE_BOOST -> state.playedMoraleBoost();
            case SHIELDS_HOLDING -> state.playedShieldsHolding();
            case DIRECT_HIT -> state.playedDirectHit();
            case FIGHTER_PROTOTYPE -> state.playedFighterPrototype();
            case WINNER_ONE_HP ->
                Boolean.TRUE.equals(candidate.getWinnerOneHpRemaining())
                        && sideBet.getTargetFaction() != null
                        && sideBet.getTargetFaction().equalsIgnoreCase(candidate.getWinnerFaction());
        };
    }

    public boolean isAfbSkippedAvailable(CombatCandidateEntity candidate, String targetFaction) {
        if (candidate == null || targetFaction == null) return false;
        CombatSideState state = CombatSideState.forFaction(candidate, targetFaction);
        if (state == null || !CombatSideBetType.AFB_SKIPPED.isAvailable(state.destroyerCount())) return false;
        return !(state.destroyerCount() == 1 && opponentHasAssaultCannon(candidate, targetFaction));
    }

    private boolean opponentHasAssaultCannon(CombatCandidateEntity candidate, String targetFaction) {
        if (targetFaction.equalsIgnoreCase(candidate.getAttackerFaction())) {
            return Boolean.TRUE.equals(candidate.getDefenderHasAssaultCannon());
        }
        if (targetFaction.equalsIgnoreCase(candidate.getDefenderFaction())) {
            return Boolean.TRUE.equals(candidate.getAttackerHasAssaultCannon());
        }
        return false;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
