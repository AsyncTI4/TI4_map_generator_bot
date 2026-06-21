package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.Collections;
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
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
public class PublicOutrageAcd2ButtonHandler {

    private static final String CARD_NAME = "Public Outrage";

    private static String revealedKey(Player player) {
        return "publicOutrageRevealed" + player.getFaction();
    }

    @ButtonHandler("resolvePublicOutrage")
    public static void resolvePublicOutrage(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons;
        try {
            buttons = AgendaHelper.getAgendaButtons(CARD_NAME, game, player.factionButtonChecker());
        } catch (Exception e) {
            buttons = new ArrayList<>();
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", could not generate outcome buttons for _Public Outrage_. Predict an outcome aloud and"
                            + " resolve manually.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", predict an outcome of this agenda for _Public Outrage_. If your prediction wins, you will"
                        + " reveal a random action card from each other player who voted for it.",
                buttons);
    }

    public static void resolveWinningPublicOutrage(
            Game game, Player winningR, String winningOutcome, String specificVote) {
        if (!specificVote.contains("Public Outrage")) return;

        Map<String, String> votes = game.getCurrentAgendaVotes();
        String voteInfo = votes == null ? null : votes.get(winningOutcome);
        if (voteInfo == null) {
            return;
        }

        List<String> revealedEntries = new ArrayList<>();
        StringBuilder revealMsg = new StringBuilder();
        List<Button> buttons = new ArrayList<>();
        for (String token : voteInfo.split(";")) {
            int underscore = token.indexOf('_');
            if (underscore < 0) {
                continue;
            }
            String value = token.substring(underscore + 1);
            if (!NumberUtils.isDigits(value)) {
                continue; // skip riders / non-numeric entries
            }
            Player voter = game.getPlayerFromColorOrFaction(token.substring(0, underscore));
            if (voter == null || voter == winningR) {
                continue;
            }
            Map<String, Integer> hand = voter.getActionCards();
            if (hand == null || hand.isEmpty()) {
                continue;
            }
            List<String> keys = new ArrayList<>(hand.keySet());
            Collections.shuffle(keys);
            String acKey = keys.getFirst();
            int acNum = hand.get(acKey);
            String acName = Mapper.getActionCard(acKey).getName();
            revealedEntries.add(voter.getFaction() + ":" + acNum);
            revealMsg
                    .append("\n> ")
                    .append(voter.getRepresentationNoPing())
                    .append(" revealed _")
                    .append(acName)
                    .append("_");
            buttons.add(Buttons.green(
                    winningR.factionButtonChecker() + "publicOutrageTake_" + voter.getFaction() + "_" + acNum,
                    "Take " + voter.getFactionModel().getShortName() + "'s " + acName));
        }

        if (revealedEntries.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    winningR.getCorrectChannel(),
                    winningR.getRepresentationUnfogged()
                            + ", no other player who voted for the winning outcome has an action card to reveal for"
                            + " _Public Outrage_.");
            return;
        }

        game.setStoredValue(revealedKey(winningR), String.join(";", revealedEntries));
        MessageHelper.sendMessageToChannel(
                winningR.getCorrectChannel(),
                winningR.getRepresentationUnfogged()
                        + ", your _Public Outrage_ prediction was correct. The following action cards were revealed:"
                        + revealMsg);
        MessageHelper.sendMessageToChannelWithButtons(
                winningR.getCorrectChannel(),
                winningR.getRepresentationUnfogged() + ", choose 1 revealed card to take; the rest are discarded.",
                buttons);
    }

    @ButtonHandler("publicOutrageTake_")
    public static void resolvePublicOutrageTake(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("publicOutrageTake_", "").split("_");
        ButtonHelper.deleteMessage(event);
        if (parts.length < 2) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        int takenNum;
        try {
            takenNum = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return;
        }
        if (target == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Public Outrage_.");
            return;
        }

        String revealed = game.getStoredValue(revealedKey(player));
        // Take the chosen card.
        String acID = acIDFor(target, takenNum);
        if (acID != null) {
            target.removeActionCard(takenNum);
            player.setActionCard(acID);
            ActionCardHelper.sendActionCardInfo(game, target);
            ActionCardHelper.sendActionCardInfo(game, player);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " took an action card from " + target.getRepresentationNoPing()
                            + " for _Public Outrage_.");
        }

        // Discard every other revealed card.
        if (revealed != null && !revealed.isEmpty()) {
            for (String entry : revealed.split(";")) {
                String[] entryParts = entry.split(":");
                if (entryParts.length < 2) {
                    continue;
                }
                Player voter = game.getPlayerFromColorOrFaction(entryParts[0]);
                int acNum;
                try {
                    acNum = Integer.parseInt(entryParts[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (voter == null || (voter == target && acNum == takenNum)) {
                    continue;
                }
                ActionCardHelper.discardAC(event, game, voter, acNum);
            }
        }
        game.removeStoredValue(revealedKey(player));
    }

    private static String acIDFor(Player player, int numericalID) {
        for (Map.Entry<String, Integer> ac : player.getActionCards().entrySet()) {
            if (ac.getValue() == numericalID) {
                return ac.getKey();
            }
        }
        return null;
    }
}
