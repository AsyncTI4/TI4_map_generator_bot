package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;

@UtilityClass
class PublicSupportAcd2ButtonHandler {

    private static String claimedKey(String outcome) {
        return "publicSupportClaimed_" + outcome;
    }

    @ButtonHandler("resolvePublicSupport")
    public static void resolvePublicSupport(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        if (isPlanetElectAgenda(game)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", choose an outcome of this elect-planet agenda aloud for _Public Support_. Each player"
                            + " who casts votes for that outcome casts 3 additional votes and draws 1 action card"
                            + " (resolve manually).");
            return;
        }
        List<Button> buttons;
        try {
            buttons = AgendaHelper.getAgendaButtons(null, game, player.factionButtonChecker() + "publicSupportChoose");
        } catch (Exception e) {
            buttons = new ArrayList<>();
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", could not generate outcome buttons for _Public Support_. Choose an outcome aloud and"
                            + " resolve manually.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose 1 outcome of this agenda for _Public Support_.",
                buttons);
    }

    @ButtonHandler("publicSupportChoose_")
    public static void resolvePublicSupportChoose(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String outcome = buttonID.replace("publicSupportChoose_", "");
        if (outcome.startsWith("_")) {
            outcome = outcome.substring(1);
        }
        game.removeStoredValue(claimedKey(outcome));
        ButtonHelper.deleteMessage(event);
        Button claimButton =
                Buttons.green("publicSupportClaim_" + outcome, "Claim _Public Support_ (+3 votes, draw 1 AC)");
        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                player.getRepresentationNoPing() + " chose **" + outcome + "** for _Public Support_. Each player who"
                        + " casts votes for that outcome may press this button to cast 3 additional votes and draw 1"
                        + " action card.",
                List.of(claimButton));
    }

    @ButtonHandler("publicSupportClaim_")
    public static void resolvePublicSupportClaim(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String outcome = buttonID.replace("publicSupportClaim_", "");
        String identifier = game.isFowMode() ? player.getColor() : player.getFaction();

        String outcomeKey = resolveOutcomeKey(game, outcome);
        if (outcomeKey == null || !votedFor(game, outcomeKey, identifier)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you have not cast votes for **" + outcome
                            + "**, so you cannot claim _Public Support_.");
            return;
        }

        String claimed = game.getStoredValue(claimedKey(outcome));
        if (alreadyClaimed(claimed, identifier)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you have already claimed _Public Support_.");
            return;
        }
        game.setStoredValue(
                claimedKey(outcome), (claimed == null || claimed.isEmpty()) ? identifier : claimed + ";" + identifier);

        addVotes(game, outcomeKey, identifier, 3);
        ActionCardHelper.drawActionCards(player, 1);
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.getRepresentationNoPing() + " cast 3 additional votes for **" + outcome
                        + "** and drew 1 action card from _Public Support_.");
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(), AgendaHelper.getSummaryOfVotes(game, true) + "\n \n");
    }

    private static boolean isPlanetElectAgenda(Game game) {
        try {
            String details = game.getCurrentAgendaInfo().split("_")[1];
            return details.toLowerCase().contains("planet");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean alreadyClaimed(String claimed, String identifier) {
        if (claimed == null || claimed.isEmpty()) {
            return false;
        }
        for (String token : claimed.split(";")) {
            if (token.equals(identifier)) {
                return true;
            }
        }
        return false;
    }

    private static String resolveOutcomeKey(Game game, String outcome) {
        Map<String, String> votes = game.getCurrentAgendaVotes();
        if (votes == null) {
            return null;
        }
        if (votes.containsKey(outcome)) {
            return outcome;
        }
        for (String key : votes.keySet()) {
            if (key.equalsIgnoreCase(outcome)) {
                return key;
            }
        }
        return null;
    }

    private static boolean votedFor(Game game, String outcomeKey, String identifier) {
        Map<String, String> votes = game.getCurrentAgendaVotes();
        String voteInfo = votes == null ? null : votes.get(outcomeKey);
        if (voteInfo == null) {
            return false;
        }
        for (String token : voteInfo.split(";")) {
            int underscore = token.indexOf('_');
            if (underscore < 0) {
                continue;
            }
            if (token.substring(0, underscore).equalsIgnoreCase(identifier)
                    && NumberUtils.isDigits(token.substring(underscore + 1))) {
                return true;
            }
        }
        return false;
    }

    private static void addVotes(Game game, String outcomeKey, String identifier, int add) {
        Map<String, String> votes = game.getCurrentAgendaVotes();
        String voteInfo = votes == null ? null : votes.get(outcomeKey);
        if (voteInfo == null) {
            return;
        }
        List<String> rebuilt = new ArrayList<>();
        for (String token : voteInfo.split(";")) {
            int underscore = token.indexOf('_');
            if (underscore >= 0
                    && token.substring(0, underscore).equalsIgnoreCase(identifier)
                    && NumberUtils.isDigits(token.substring(underscore + 1))) {
                int newVotes = Integer.parseInt(token.substring(underscore + 1)) + add;
                rebuilt.add(token.substring(0, underscore) + "_" + newVotes);
            } else {
                rebuilt.add(token);
            }
        }
        game.setCurrentAgendaVote(outcomeKey, String.join(";", rebuilt));
    }
}
