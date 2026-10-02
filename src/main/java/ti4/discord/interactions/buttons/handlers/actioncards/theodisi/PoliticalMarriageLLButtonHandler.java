package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;

@UtilityClass
public class PoliticalMarriageLLButtonHandler {
    private static final String RESOLVE = "resolvePoliticalMarriage";
    private static final String SELECT = "politicalMarriageTarget_";
    private static final String STATE = "politicalMarriage_";

    @ButtonHandler(RESOLVE)
    public static void resolvePoliticalMarriage(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target != player) {
                buttons.add(FoWHelper.fogSafeTargetButton(
                        player.factionButtonChecker() + SELECT + target.getFaction(), "gray", target));
            }
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing()
                        + ", choose the player whose current turn is restricted by _Political Marriage_.",
                buttons);
    }

    @ButtonHandler(SELECT)
    public static void selectPoliticalMarriageTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT.length()));
        if (target == null || target == player || !target.getUserID().equals(game.getActivePlayerID())) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That player is not currently taking a turn.");
            return;
        }
        game.setStoredValue(STATE + target.getFaction(), player.getFaction() + "|" + target.getInRoundTurnCount());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " used _Political Marriage_ on "
                        + target.getRepresentationNoPing()
                        + ". During this turn, they cannot activate systems containing "
                        + player.getRepresentationNoPing() + "'s units.");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean blocksActivation(Game game, Player activatingPlayer, Tile tile) {
        String[] state =
                game.getStoredValue(STATE + activatingPlayer.getFaction()).split("\\|", 2);
        Player owner = state.length == 2 ? game.getPlayerFromColorOrFaction(state[0]) : null;
        return owner != null && FoWHelper.playerHasUnitsInSystem(owner, tile);
    }

    public static void clearExpiredRestriction(Game game, Player startingPlayer) {
        String key = STATE + startingPlayer.getFaction();
        String[] state = game.getStoredValue(key).split("\\|", 2);
        if (state.length != 2) return;
        try {
            if (Integer.parseInt(state[1]) < startingPlayer.getInRoundTurnCount()) {
                game.removeStoredValue(key);
            }
        } catch (NumberFormatException e) {
            game.removeStoredValue(key);
        }
    }
}
