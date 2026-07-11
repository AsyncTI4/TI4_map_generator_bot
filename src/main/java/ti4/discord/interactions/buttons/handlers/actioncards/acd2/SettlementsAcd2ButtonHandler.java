package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AgendaRiderHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class SettlementsAcd2ButtonHandler {

    private static final String CARD_NAME = "Settlements";

    private static String winnerKey(Player player) {
        return "settlementsWinner" + player.getFaction();
    }

    @ButtonHandler("resolveSettlements")
    public static void resolveSettlements(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons;
        try {
            buttons = AgendaRiderHelper.getAgendaButtons(CARD_NAME, game, player.factionButtonChecker());
        } catch (Exception e) {
            buttons = new ArrayList<>();
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", could not generate outcome buttons for _Settlements_. Predict an outcome aloud, then"
                            + " place up to 2 infantry into coexistence on voters' non-home planets after the agenda"
                            + " resolves.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", predict an outcome of this agenda for _Settlements_. If your prediction wins, you may"
                        + " place up to 2 infantry into coexistence on voters' non-home planets.",
                buttons);
    }

    public static void resolveWinningSettlements(Game game, Player winningR, String winningOutcome) {
        if (votersFor(game, winningOutcome).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    winningR.getCorrectChannel(),
                    winningR.getRepresentationUnfogged()
                            + ", your _Settlements_ prediction was correct, but no players voted for the winning"
                            + " outcome.");
            return;
        }
        game.setStoredValue(winnerKey(winningR), winningOutcome);
        MessageHelper.sendMessageToChannel(
                winningR.getCorrectChannel(),
                winningR.getRepresentationUnfogged() + ", your _Settlements_ prediction was correct.");
        sendPlacementButtons(winningR, game, 2);
    }

    @ButtonHandler("settlementsPlaceOn_")
    public static void resolveSettlementsPlaceOn(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("settlementsPlaceOn_", "");
        int separator = payload.indexOf('_');
        if (separator < 0) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        int remaining;
        try {
            remaining = Integer.parseInt(payload.substring(0, separator));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String planet = payload.substring(separator + 1);
        Tile tile = game.getTileContainingPlanet(planet);
        ButtonHelper.deleteMessage(event);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Settlements_.");
            return;
        }

        game.setStoredValue("coexistFlag", "yes");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planet);
        game.removeStoredValue("coexistFlag");
        ButtonHelperAbilities.oceanBoundCheck(game);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed 1 infantry into coexistence on "
                        + Helper.getPlanetRepresentation(planet, game) + " via _Settlements_.");

        if (remaining > 1) {
            sendPlacementButtons(player, game, remaining - 1);
        } else {
            game.removeStoredValue(winnerKey(player));
        }
    }

    private static void sendPlacementButtons(Player player, Game game, int remaining) {
        String outcome = game.getStoredValue(winnerKey(player));
        Set<String> planets = new LinkedHashSet<>();
        for (Player voter : votersFor(game, outcome)) {
            for (String planet : voter.getPlanets()) {
                Planet uH = game.getPlanet(planet);
                if (uH != null && !uH.isHomePlanet(game)) {
                    planets.add(planet);
                }
            }
        }
        if (planets.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", there are no non-home planets controlled by voters to settle for _Settlements_.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String planet : planets) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "settlementsPlaceOn_" + remaining + "_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        String remainingText = remaining == 1 ? "your last infantry" : "an infantry (" + remaining + " remaining)";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose where to place " + remainingText
                        + " into coexistence for _Settlements_.",
                buttons);
    }

    private static Set<Player> votersFor(Game game, String outcome) {
        Set<Player> voters = new LinkedHashSet<>();
        if (outcome == null || outcome.isEmpty()) {
            return voters;
        }
        Map<String, String> votes = game.getCurrentAgendaVotes();
        String voteInfo = votes == null ? null : votes.get(outcome);
        if (voteInfo == null) {
            return voters;
        }
        for (String token : voteInfo.split(";")) {
            int underscore = token.indexOf('_');
            if (underscore < 0) {
                continue;
            }
            if (!NumberUtils.isDigits(token.substring(underscore + 1))) {
                continue; // skip riders / non-numeric entries
            }
            Player voter = game.getPlayerFromColorOrFaction(token.substring(0, underscore));
            if (voter != null) {
                voters.add(voter);
            }
        }
        return voters;
    }
}
