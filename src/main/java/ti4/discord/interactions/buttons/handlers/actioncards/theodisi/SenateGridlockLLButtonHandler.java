package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaSummaryHelper;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class SenateGridlockLLButtonHandler {
    private static final String RESOLVE = "resolveSenateGridlock";
    private static final String SELECT = "senateGridlockTarget_";

    @ButtonHandler(RESOLVE)
    public static void resolveSenateGridlock(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getTargetButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " has no eligible vote to copy for _Senate Gridlock_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing()
                        + ", choose a player who voted for a different outcome. You will cast additional votes equal to their votes.",
                buttons);
    }

    @ButtonHandler(SELECT)
    public static void selectSenateGridlockTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT.length()));
        Vote playerVote = getVote(game, player);
        Vote targetVote = target == null ? null : getVote(game, target);
        if (playerVote == null
                || targetVote == null
                || playerVote.outcome.equals(targetVote.outcome)
                || target == player) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That player is no longer eligible for _Senate Gridlock_.");
            return;
        }

        String existing = game.getCurrentAgendaVotes().get(playerVote.outcome);
        game.setCurrentAgendaVote(playerVote.outcome, existing + ";" + player.getFaction() + "_" + targetVote.votes);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " cast " + targetVote.votes
                        + " additional vote" + (targetVote.votes == 1 ? "" : "s")
                        + " for their existing outcome with _Senate Gridlock_.\n"
                        + AgendaSummaryHelper.getSummaryOfVotes(game, true));
        sendUpdatedResolutionButtons(game, event);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getTargetButtons(Game game, Player player) {
        Vote playerVote = getVote(game, player);
        List<Button> buttons = new ArrayList<>();
        if (playerVote == null) return buttons;
        for (Player target : game.getRealPlayers()) {
            Vote targetVote = getVote(game, target);
            if (target != player && targetVote != null && !playerVote.outcome.equals(targetVote.outcome)) {
                String targetName = game.isFowMode()
                        ? target.getFactionNameOrColor()
                        : target.getFactionModel().getShortName();
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + SELECT + target.getFaction(),
                        "Copy " + targetVote.votes + " vote" + (targetVote.votes == 1 ? "" : "s") + " from "
                                + targetName,
                        target.fogSafeEmoji()));
            }
        }
        return buttons;
    }

    private static Vote getVote(Game game, Player player) {
        for (Map.Entry<String, String> entry : game.getCurrentAgendaVotes().entrySet()) {
            int votes = 0;
            StringTokenizer tokens = new StringTokenizer(entry.getValue(), ";");
            while (tokens.hasMoreTokens()) {
                String vote = tokens.nextToken();
                int separator = vote.indexOf('_');
                if (separator > 0 && vote.substring(0, separator).equals(player.getFaction())) {
                    String value = vote.substring(separator + 1);
                    if (NumberUtils.isDigits(value)) votes += Integer.parseInt(value);
                }
            }
            if (votes > 0) return new Vote(entry.getKey(), votes);
        }
        return null;
    }

    private static void sendUpdatedResolutionButtons(Game game, ButtonInteractionEvent event) {
        List<Map.Entry<String, Integer>> outcomes = new ArrayList<>(
                AgendaSummaryHelper.getCurrentOutcomeVoteCounts(game).entrySet());
        int highest = outcomes.stream()
                .map(Map.Entry::getValue)
                .max(Comparator.naturalOrder())
                .orElse(0);
        List<Map.Entry<String, Integer>> leaders = outcomes.stream()
                .filter(entry -> entry.getValue() == highest && highest > 0)
                .toList();
        List<Button> buttons = new ArrayList<>();
        if (leaders.size() == 1) {
            buttons.add(Buttons.blue("agendaResolution_" + leaders.getFirst().getKey(), "Resolve with Current Winner"));
        } else {
            for (Map.Entry<String, Integer> leader : leaders) {
                buttons.add(Buttons.blue("agendaResolution_" + leader.getKey(), "Resolve " + leader.getKey()));
            }
        }
        buttons.add(Buttons.red("autoresolve_manual", "Resolve it Manually"));
        MessageHelper.sendMessageToChannelWithButtons(
                game.getMainGameChannel(),
                "_Senate Gridlock_ changed the vote result. Use these updated resolution buttons.",
                buttons);
    }

    private record Vote(String outcome, int votes) {}
}
