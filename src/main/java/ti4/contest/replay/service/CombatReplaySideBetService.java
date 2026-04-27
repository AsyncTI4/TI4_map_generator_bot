package ti4.contest.replay.service;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import org.springframework.stereotype.Service;
import ti4.contest.replay.buttons.CombatSideBetButtonIds;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatContestSideBetEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.entities.CombatReplayLeaderboardEntryEntity;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatContestSideBetRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.contest.replay.repository.CombatReplayLeaderboardEntryRepository;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@Service
@RequiredArgsConstructor
public class CombatReplaySideBetService {

    private final CombatContestSettings settings;
    private final CombatReplayContestRepository replayContestRepository;
    private final CombatCandidateRepository candidateRepository;
    private final CombatContestSideBetRepository sideBetRepository;
    private final CombatReplayLeaderboardEntryRepository leaderboardEntryRepository;
    private final CombatReplaySideBetPayoutService payoutService;

    public boolean shouldShowButtons(CombatCandidateEntity candidate) {
        return candidate != null
                && Boolean.TRUE.equals(candidate.getSideBetCompatible())
                && settings.getSideBets().isEnableSideBets();
    }

    public void postSideBetButtonsIfNeeded(
            MessageChannel channel, Game game, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        if (channel == null || game == null || contest == null || !shouldShowButtons(candidate)) return;
        MessageHelper.sendMessageToChannel(channel, "## Side Bets\nPlace up to 5 side bets before the replay begins.");
        ensureSummaryMessage(channel, game, contest);
        postFactionButtons(channel, game, contest, candidate, candidate.getAttackerFaction());
        postFactionButtons(channel, game, contest, candidate, candidate.getDefenderFaction());
    }

    private void postFactionButtons(
            MessageChannel channel,
            Game game,
            CombatReplayContestEntity contest,
            CombatCandidateEntity candidate,
            String faction) {
        List<Button> buttons = buttonsForFaction(game, contest, candidate, faction);
        if (buttons.isEmpty()) return;
        MessageHelper.sendMessageToChannelWithButtons(channel, factionSectionTitle(game, faction), buttons);
    }

    private List<Button> buttonsForFaction(
            Game game, CombatReplayContestEntity contest, CombatCandidateEntity candidate, String faction) {
        List<Button> buttons = new ArrayList<>();
        String factionLabel = buttonFactionIdLabel(game, faction);
        SideBetState state = stateFor(candidate, faction);
        if (state == null) return buttons;
        for (CombatSideBetType type : CombatSideBetType.values()) {
            if (!isAvailableForFaction(contest, type, faction, state.destroyerCount())) continue;
            int profitPoints = payoutService.offeredPayout(contest, candidate, type, faction);
            buttons.add(Buttons.gray(
                    buttonId(contest.getId(), type, faction),
                    buttonLabel(type, factionLabel, profitPoints),
                    type.emoji()));
        }
        return buttons;
    }

    private boolean isAvailableForFaction(
            CombatReplayContestEntity contest, CombatSideBetType type, String faction, int destroyerCount) {
        if (!type.isAvailable(destroyerCount)) return false;
        if (type != CombatSideBetType.AFB_SKIPPED) return true;
        if (contest == null || contest.getCandidateId() == null) return false;
        CombatCandidateEntity candidate =
                candidateRepository.findById(contest.getCandidateId()).orElse(null);
        return isAfbSkippedAvailable(candidate, faction);
    }

    @Transactional
    public PlacementResult placeSideBet(
            ButtonInteractionEvent event, Long contestId, CombatSideBetType betType, String targetFaction) {
        if (contestId == null || event == null || event.getUser() == null) {
            return PlacementResult.rejected("Side bet context is incomplete.");
        }

        CombatReplayContestEntity contest =
                replayContestRepository.findById(contestId).orElse(null);
        if (contest == null) return PlacementResult.rejected("This combat contest is no longer available.");
        if (!settings.getSideBets().isEnableSideBets()) return PlacementResult.rejected("Side bets are disabled.");
        if (contest.getReplayStartAt() == null || !LocalDateTime.now().isBefore(contest.getReplayStartAt())) {
            return PlacementResult.rejected("The side-bet window is closed.");
        }

        CombatCandidateEntity candidate =
                candidateRepository.findById(contest.getCandidateId()).orElse(null);
        if (candidate == null || !Boolean.TRUE.equals(candidate.getSideBetCompatible())) {
            return PlacementResult.rejected("This combat does not support side bets.");
        }
        if (!isValidBet(candidate, betType, targetFaction)) {
            return PlacementResult.rejected("That side bet is not available for this combat.");
        }

        String userId = event.getUser().getId();
        String userName = event.getUser().getEffectiveName();

        CombatReplayLeaderboardEntryEntity entry =
                leaderboardEntryRepository.findByDiscordUserIdForUpdate(userId).orElse(null);
        if (entry == null) {
            return PlacementResult.rejected("You do not have any points to bet.");
        }

        int existingCount = sideBetRepository.countByContestIdAndDiscordUserId(contestId, userId);
        if (existingCount >= settings.getSideBets().getMaxBetsPerUser()) {
            return PlacementResult.rejected("You have already placed the maximum number of side bets for this combat.");
        }

        int costPoints = settings.getSideBets().getCostPoints();
        int currentPoints = safeInt(entry.getTotalPoints());
        if (currentPoints <= 0) {
            return PlacementResult.rejected("You do not have any points to bet.");
        }
        if (currentPoints < costPoints) {
            return PlacementResult.rejected("You do not have enough points to place that side bet.");
        }

        entry.setDiscordUserName(userName);
        entry.setTotalPoints(currentPoints - costPoints);
        entry.setUpdatedAt(LocalDateTime.now());
        leaderboardEntryRepository.save(entry);

        CombatContestSideBetEntity sideBet = new CombatContestSideBetEntity();
        sideBet.setContestId(contestId);
        sideBet.setCandidateId(candidate.getId());
        sideBet.setDiscordUserId(userId);
        sideBet.setDiscordUserName(userName);
        sideBet.setBetType(betType);
        sideBet.setTargetFaction(targetFaction);
        sideBet.setPointsSpent(costPoints);
        sideBet.setOfferedProfitPoints(payoutService.offeredPayout(contest, candidate, betType, targetFaction));
        sideBet.setPlacedAt(LocalDateTime.now());
        sideBetRepository.save(sideBet);

        refreshSummaryMessage(event.getMessageChannel(), contest, candidate);
        String personalSummary = renderUserSummary(loadReplayGame(candidate), contest, userId, entry.getTotalPoints());
        return PlacementResult.accepted(personalSummary, entry.getTotalPoints());
    }

    public SideBetResolution resolveSideBets(CombatCandidateEntity candidate, CombatReplayContestEntity replayContest) {
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
            sideBet.setWon(won);
            sideBet.setProfitAwarded(won && entry != null ? profitPoints : 0);
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
        List<String> userIds = sideBets.stream()
                .map(CombatContestSideBetEntity::getDiscordUserId)
                .filter(userId -> userId != null)
                .distinct()
                .toList();
        return leaderboardEntryRepository.findByDiscordUserIdIn(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        CombatReplayLeaderboardEntryEntity::getDiscordUserId, entry -> entry));
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

    public record SideBetResolution(
            List<ResolvedSideBet> resolvedSideBets, List<CombatReplayLeaderboardEntryEntity> leaderboardEntries) {
        static SideBetResolution empty() {
            return new SideBetResolution(List.of(), List.of());
        }
    }

    private String buttonLabel(CombatSideBetType type, String factionLabel, int profitPoints) {
        return "[" + factionLabel + "] " + type.label() + " +" + profitPoints + " pts";
    }

    private void ensureSummaryMessage(MessageChannel channel, Game game, CombatReplayContestEntity contest) {
        if (channel == null || contest == null) return;
        Long summaryMessageId = contest.getSideBetSummaryMessageId();
        if (summaryMessageId != null && summaryMessageId > 0) {
            refreshSummaryMessage(channel, contest, game);
            return;
        }

        try {
            Message summary =
                    channel.sendMessage(renderSummaryMessage(game, contest)).complete();
            contest.setSideBetSummaryMessageId(summary.getIdLong());
            replayContestRepository.save(contest);
        } catch (Exception e) {
            BotLogger.error("Failed to create side bet summary message.", e);
        }
    }

    private void refreshSummaryMessage(
            MessageChannel channel, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        refreshSummaryMessage(channel, contest, loadReplayGame(candidate));
    }

    private void refreshSummaryMessage(MessageChannel channel, CombatReplayContestEntity contest, Game game) {
        if (channel == null || contest == null) return;
        Long summaryMessageId = contest.getSideBetSummaryMessageId();
        if (summaryMessageId == null || summaryMessageId <= 0) {
            ensureSummaryMessage(channel, game, contest);
            return;
        }

        channel.retrieveMessageById(summaryMessageId)
                .queue(
                        message -> message.editMessage(renderSummaryMessage(game, contest))
                                .queue(Consumers.nop(), BotLogger::catchRestError),
                        error -> {
                            contest.setSideBetSummaryMessageId(null);
                            replayContestRepository.save(contest);
                            ensureSummaryMessage(channel, game, contest);
                        });
    }

    private String renderSummaryMessage(Game game, CombatReplayContestEntity contest) {
        List<CombatContestSideBetEntity> sideBets = sideBetRepository.findByContestId(contest.getId()).stream()
                .sorted(Comparator.comparing(
                                CombatContestSideBetEntity::getPlacedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(
                                CombatContestSideBetEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        StringBuilder message = new StringBuilder();
        if (sideBets.isEmpty()) {
            message.append("```text\n");
            message.append(String.format("%-3s | %s%n", "Qty", "Bet"));
            message.append("----+------------------------------\n");
            message.append(" -  | Waiting for the first bet\n");
            message.append("```");
            return message.toString();
        }

        Map<String, Long> countsByBet = summarizeBetCounts(sideBets, game);
        message.append("```text\n");
        message.append(String.format("%-3s | %s%n", "Qty", "Bet"));
        message.append("----+------------------------------\n");
        for (Map.Entry<String, Long> entry : countsByBet.entrySet()) {
            message.append(String.format("%-3s | %s%n", entry.getValue() + "x", entry.getKey()));
        }
        message.append("```");
        return message.toString();
    }

    private String renderUserSummary(Game game, CombatReplayContestEntity contest, String userId, int remainingPoints) {
        List<CombatContestSideBetEntity> bets = sideBetRepository.findByContestId(contest.getId()).stream()
                .filter(sideBet -> Objects.equals(sideBet.getDiscordUserId(), userId))
                .sorted(Comparator.comparing(
                                CombatContestSideBetEntity::getPlacedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(
                                CombatContestSideBetEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        StringBuilder message = new StringBuilder("## Your Side Bets\n");
        message.append("Remaining points: **").append(remainingPoints).append("**\n");
        if (bets.isEmpty()) {
            message.append("No side bets recorded.");
            return message.toString();
        }

        for (Map.Entry<String, Long> entry : summarizeUserBetCounts(bets, game).entrySet()) {
            message.append("- ")
                    .append(entry.getValue())
                    .append("x ")
                    .append(entry.getKey())
                    .append("\n");
        }
        return message.toString().trim();
    }

    private Map<String, Long> summarizeBetCounts(List<CombatContestSideBetEntity> sideBets, Game game) {
        Map<String, Long> countsByBet = new LinkedHashMap<>();
        for (CombatContestSideBetEntity sideBet : sideBets) {
            String label = formatFriendlyBetLabel(game, sideBet, true);
            countsByBet.merge(label, 1L, Long::sum);
        }
        return sortBetCountsByQuantityDesc(countsByBet);
    }

    private Map<String, Long> summarizeUserBetCounts(List<CombatContestSideBetEntity> sideBets, Game game) {
        Map<String, Long> countsByBet = new LinkedHashMap<>();
        for (CombatContestSideBetEntity sideBet : sideBets) {
            String label = formatFriendlyBetLabel(game, sideBet, false);
            countsByBet.merge(label, 1L, Long::sum);
        }
        return countsByBet;
    }

    private Map<String, Long> sortBetCountsByQuantityDesc(Map<String, Long> countsByBet) {
        return countsByBet.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
    }

    private String formatFriendlyBetLabel(Game game, CombatContestSideBetEntity sideBet) {
        return formatFriendlyBetLabel(game, sideBet, false);
    }

    private String formatFriendlyBetLabel(Game game, CombatContestSideBetEntity sideBet, boolean useShortFactionId) {
        String faction = useShortFactionId
                ? buttonFactionIdLabel(game, sideBet.getTargetFaction())
                : buttonFactionDisplayName(game, sideBet.getTargetFaction());
        return faction + " " + sideBet.getBetType().label() + " +" + payoutService.resolvedProfitPoints(sideBet);
    }

    private String factionSectionTitle(Game game, String faction) {
        String emoji = getFactionEmoji(game, faction);
        String label = buttonFactionDisplayName(game, faction);
        if (emoji == null || emoji.isBlank()) return "### " + label;
        return "### " + emoji + " " + label;
    }

    private String buttonFactionIdLabel(Game game, String faction) {
        if (faction == null || faction.isBlank()) return "?";
        if (game == null) return faction;
        Player target = game.getPlayerFromColorOrFaction(faction);
        if (target == null) return faction;
        return target.getFaction();
    }

    private String buttonFactionDisplayName(Game game, String faction) {
        if (faction == null || faction.isBlank()) return "?";
        if (game == null) return faction;
        Player target = game.getPlayerFromColorOrFaction(faction);
        if (target == null) return faction;
        return target.getFactionModel() == null
                ? target.getFaction()
                : target.getFactionModel().getFactionName();
    }

    private String buttonId(Long contestId, CombatSideBetType type, String faction) {
        return CombatSideBetButtonIds.format(contestId, type, faction);
    }

    private boolean isValidBet(CombatCandidateEntity candidate, CombatSideBetType betType, String targetFaction) {
        SideBetState state = stateFor(candidate, targetFaction);
        if (state == null || !betType.isAvailable(state.destroyerCount())) return false;
        return betType != CombatSideBetType.AFB_SKIPPED || isAfbSkippedAvailable(candidate, targetFaction);
    }

    private boolean isWinningBet(CombatCandidateEntity candidate, CombatContestSideBetEntity sideBet) {
        SideBetState state = stateFor(candidate, sideBet.getTargetFaction());
        if (state == null) return false;
        CombatSideBetType betType = sideBet.getBetType();
        return switch (betType) {
            case AFB_SKIPPED -> isAfbSkippedAvailable(candidate, sideBet.getTargetFaction()) && !state.rolledAfb();
            case AFB_WHIFF -> betType.isAvailable(state.destroyerCount()) && state.afbWhiff();
            case ROUND_ONE_WHIFF -> state.roundOneWhiff();
            case ROUND_ONE_SLAM -> state.roundOneSlam();
            case MORALE_BOOST -> state.playedMoraleBoost();
            case SHIELDS_HOLDING -> state.playedShieldsHolding();
            case WINNER_ONE_HP ->
                Boolean.TRUE.equals(candidate.getWinnerOneHpRemaining())
                        && sideBet.getTargetFaction() != null
                        && sideBet.getTargetFaction().equalsIgnoreCase(candidate.getWinnerFaction());
        };
    }

    private SideBetState stateFor(CombatCandidateEntity candidate, String faction) {
        if (candidate == null || faction == null) return null;
        if (faction.equalsIgnoreCase(candidate.getAttackerFaction())) {
            return new SideBetState(
                    safeInt(candidate.getAttackerDestroyerCount()),
                    Boolean.TRUE.equals(candidate.getAttackerRolledAfb()),
                    Boolean.TRUE.equals(candidate.getAttackerAfbWhiff()),
                    Boolean.TRUE.equals(candidate.getAttackerRoundOneWhiff()),
                    Boolean.TRUE.equals(candidate.getAttackerRoundOneSlam()),
                    Boolean.TRUE.equals(candidate.getAttackerPlayedMoraleBoost()),
                    Boolean.TRUE.equals(candidate.getAttackerPlayedShieldsHolding()));
        }
        if (faction.equalsIgnoreCase(candidate.getDefenderFaction())) {
            return new SideBetState(
                    safeInt(candidate.getDefenderDestroyerCount()),
                    Boolean.TRUE.equals(candidate.getDefenderRolledAfb()),
                    Boolean.TRUE.equals(candidate.getDefenderAfbWhiff()),
                    Boolean.TRUE.equals(candidate.getDefenderRoundOneWhiff()),
                    Boolean.TRUE.equals(candidate.getDefenderRoundOneSlam()),
                    Boolean.TRUE.equals(candidate.getDefenderPlayedMoraleBoost()),
                    Boolean.TRUE.equals(candidate.getDefenderPlayedShieldsHolding()));
        }
        return null;
    }

    public boolean isAfbSkippedAvailable(CombatCandidateEntity candidate, String targetFaction) {
        if (candidate == null || targetFaction == null) return false;
        SideBetState state = stateFor(candidate, targetFaction);
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

    private String getFactionEmoji(Game game, String faction) {
        if (game == null || faction == null) return "";
        Player target = game.getPlayerFromColorOrFaction(faction);
        return target == null ? "" : target.getFactionEmoji();
    }

    private Game loadReplayGame(CombatCandidateEntity candidate) {
        if (candidate == null || candidate.getGameName() == null) return null;
        if (!GameManager.isValid(candidate.getGameName())) return null;
        return GameManager.getManagedGame(candidate.getGameName()).getGame();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String safeName(String userName) {
        if (userName == null || userName.isBlank()) return "Unknown User";
        return userName.replace("@", "@\u200B");
    }

    private record SideBetState(
            int destroyerCount,
            boolean rolledAfb,
            boolean afbWhiff,
            boolean roundOneWhiff,
            boolean roundOneSlam,
            boolean playedMoraleBoost,
            boolean playedShieldsHolding) {}
}
