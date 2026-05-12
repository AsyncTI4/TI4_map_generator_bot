package ti4.contest.replay.service;

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
import org.apache.commons.lang3.function.Consumers;
import org.springframework.stereotype.Service;
import ti4.contest.replay.buttons.CombatDoubleOrBustButtonIds;
import ti4.contest.replay.buttons.CombatSideBetButtonIds;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatContestSideBetEntity;
import ti4.contest.replay.entities.CombatDoubleOrBustEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.repository.CombatContestSideBetRepository;
import ti4.contest.replay.repository.CombatDoubleOrBustRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

/**
 * Owns Discord-facing side-bet buttons, summaries, and per-user bet display text.
 */
@Service
@RequiredArgsConstructor
class CombatReplaySideBetUiService {

    private final CombatContestSettings settings;
    private final CombatReplayContestRepository replayContestRepository;
    private final CombatContestSideBetRepository sideBetRepository;
    private final CombatDoubleOrBustRepository doubleOrBustRepository;
    private final CombatReplaySideBetPayoutService payoutService;
    private final CombatSideBetAvailabilityService availabilityService;

    public boolean shouldShowButtons(CombatCandidateEntity candidate) {
        return candidate != null
                && Boolean.TRUE.equals(candidate.getSideBetCompatible())
                && settings.getSideBets().isEnableSideBets();
    }

    public void postSideBetButtonsIfNeeded(
            MessageChannel channel, Game game, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        if (channel == null || game == null || contest == null || !shouldShowButtons(candidate)) return;
        MessageHelper.sendMessageToChannel(channel, sideBetPrompt());
        ensureSummaryMessage(channel, game, contest);
        postFactionButtons(channel, game, contest, candidate, candidate.getAttackerFaction());
        postFactionButtons(channel, game, contest, candidate, candidate.getDefenderFaction());
        postDoubleOrBustButton(channel, contest);
    }

    private String sideBetPrompt() {
        int maxBets = settings.getSideBets().getMaxBetsPerUser();
        return "## Side Bets\nPlace up to " + maxBets + " side bets before the replay begins.";
    }

    public void refreshSummaryMessage(
            MessageChannel channel, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        Game game = loadReplayGame(candidate);
        refreshSummaryMessage(channel, contest, game);
    }

    public String renderUserSummary(
            CombatReplayContestEntity contest, CombatCandidateEntity candidate, String userId, int remainingPoints) {
        return renderUserSummary(loadReplayGame(candidate), contest, userId, remainingPoints);
    }

    private void postFactionButtons(
            MessageChannel channel,
            Game game,
            CombatReplayContestEntity contest,
            CombatCandidateEntity candidate,
            String faction) {
        List<Button> buttons = buttonsForFaction(game, contest, candidate, faction);
        if (buttons.isEmpty()) return;
        MessageHelper.splitAndSentWithAction(
                factionSectionTitle(game, faction),
                channel,
                buttons,
                message -> saveSideBetButtonMessageId(contest, candidate, faction, message.getIdLong()));
    }

    private List<Button> buttonsForFaction(
            Game game, CombatReplayContestEntity contest, CombatCandidateEntity candidate, String faction) {
        return buttonsForFaction(game, contest, candidate, faction, sideBetWindowClosed(contest));
    }

    private List<Button> buttonsForFaction(
            Game game,
            CombatReplayContestEntity contest,
            CombatCandidateEntity candidate,
            String faction,
            boolean disabled) {
        List<Button> buttons = new ArrayList<>();
        for (CombatSideBetType type : availabilityService.availableTypes(candidate, faction)) {
            int profitPoints = payoutService.offeredPayout(contest, candidate, type, faction);
            Button button = Buttons.gray(
                    CombatSideBetButtonIds.format(contest.getId(), type, faction),
                    buttonLabel(type, profitPoints),
                    type.emoji());
            buttons.add(disabled ? button.asDisabled() : button);
        }
        return buttons;
    }

    private String buttonLabel(CombatSideBetType type, int profitPoints) {
        return type.label() + " +" + profitPoints + " pts";
    }

    private void saveSideBetButtonMessageId(
            CombatReplayContestEntity contest, CombatCandidateEntity candidate, String faction, long messageId) {
        if (contest == null || candidate == null || faction == null || messageId <= 0) return;
        if (faction.equalsIgnoreCase(candidate.getAttackerFaction())) {
            contest.setSideBetAttackerButtonMessageId(messageId);
        } else if (faction.equalsIgnoreCase(candidate.getDefenderFaction())) {
            contest.setSideBetDefenderButtonMessageId(messageId);
        } else {
            return;
        }
        replayContestRepository.save(contest);
    }

    private void postDoubleOrBustButton(MessageChannel channel, CombatReplayContestEntity contest) {
        if (channel == null || contest == null || contest.getId() == null) return;
        Button button = Buttons.green(CombatDoubleOrBustButtonIds.format(contest.getId()), "Double or Bust");
        if (sideBetWindowClosed(contest)) button = button.asDisabled();
        MessageHelper.sendMessageToChannelWithButton(
                channel, "## Double or Bust\nSet your Double or Bust before the replay begins.", button);
    }

    private void ensureSummaryMessage(MessageChannel channel, Game game, CombatReplayContestEntity contest) {
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
        List<CombatContestSideBetEntity> sideBets = sideBetRepository.findByContestId(contest.getId());
        List<CombatDoubleOrBustEntity> doubleOrBusts =
                doubleOrBustRepository.findByContestIdAndEnabledTrue(contest.getId());
        sideBets.sort(sideBetOrder());
        StringBuilder message = new StringBuilder();
        message.append("```text\n");
        message.append(String.format("%-3s | %s%n", "Qty", "Bet"));
        message.append("----+------------------------------\n");
        if (sideBets.isEmpty()) {
            message.append(" -  | No side bets placed\n");
        } else {
            Map<String, Long> countsByBet = summarizeBetCounts(sideBets, game);
            for (Map.Entry<String, Long> entry :
                    sortBetCountsByQuantityDesc(countsByBet).entrySet()) {
                message.append(String.format("%-3s | %s%n", entry.getValue() + "x", entry.getKey()));
            }
        }

        message.append("\n");
        message.append(String.format("%-3s | %s%n", "Qty", "Double or Bust"));
        message.append("----+------------------------------\n");
        if (doubleOrBusts.isEmpty()) {
            message.append(" -  | No Double or Bust entries\n");
        } else {
            message.append(String.format("%-3s | People enabled%n", doubleOrBusts.size() + "x"));
        }
        message.append("```");
        return message.toString();
    }

    private String renderUserSummary(Game game, CombatReplayContestEntity contest, String userId, int remainingPoints) {
        List<CombatContestSideBetEntity> bets = new ArrayList<>();
        for (CombatContestSideBetEntity sideBet : sideBetRepository.findByContestId(contest.getId())) {
            if (Objects.equals(sideBet.getDiscordUserId(), userId)) {
                bets.add(sideBet);
            }
        }
        bets.sort(sideBetOrder());

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

    private Comparator<CombatContestSideBetEntity> sideBetOrder() {
        return Comparator.comparing(
                        CombatContestSideBetEntity::getPlacedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CombatContestSideBetEntity::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Map<String, Long> summarizeBetCounts(List<CombatContestSideBetEntity> sideBets, Game game) {
        Map<String, Long> countsByBet = new LinkedHashMap<>();
        for (CombatContestSideBetEntity sideBet : sideBets) {
            String label = formatFriendlyBetLabel(game, sideBet, true);
            countsByBet.merge(label, 1L, Long::sum);
        }
        return countsByBet;
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
        List<Map.Entry<String, Long>> entries = new ArrayList<>(countsByBet.entrySet());
        entries.sort(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()));

        Map<String, Long> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : entries) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return sorted;
    }

    private boolean sideBetWindowClosed(CombatReplayContestEntity contest) {
        return contest.getReplayStartAt() != null && !LocalDateTime.now().isBefore(contest.getReplayStartAt());
    }

    private String formatFriendlyBetLabel(Game game, CombatContestSideBetEntity sideBet, boolean useShortFactionId) {
        String faction = useShortFactionId
                ? buttonFactionIdLabel(game, sideBet.getTargetFaction())
                : buttonFactionDisplayName(game, sideBet.getTargetFaction());
        String label = faction + " " + sideBet.getBetType().label();
        return label + " +" + payoutService.resolvedProfitPoints(sideBet) + " pts";
    }

    private String factionSectionTitle(Game game, String faction) {
        String emoji = getFactionEmoji(game, faction);
        String label = buttonFactionDisplayName(game, faction);
        if (emoji.isBlank()) return "### " + label;
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
}
