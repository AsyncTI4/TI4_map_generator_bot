package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Verydith;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class VerydithAbilitiesHandler {
    public static final String USE_MANDATE = "useMandatePresence";
    public static final String MANDATE_SYSTEM = "selectMandateSystem_";
    public static final String MANDATE_TARGET = "selectMandateTarget_";
    
    public static void getMandateButtons(GenericInteractionCreateEvent event, Player player, Game game) {
        String usedMandateThisActionPhase = game.getStoredValue("mandateUsedThisActionPhase_" + player.getFaction());

        if (player == null || !player.hasAbility("mandate_of_presence") || !usedMandateThisActionPhase.isEmpty()) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
            player.factionButtonChecker() + USE_MANDATE, "Use Mandate of Presence", FactionEmojis.verydith));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
            event.getMessageChannel(),
            player.getRepresentation()
                + ", you have _Mandate of Presence_ and may place 1 players command token in a system that contains 1 or more of your units, no legendary planets, and no other player's units:",
            buttons);
    }

    @ButtonHandler(USE_MANDATE)
    public static void startMandateOfPresence(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasAbility("mandate_of_presence")) {
            return;
        }
        ButtonHelper.deleteMessage(event);

        List<Button> eligibleSystems = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile == null || !FoWHelper.playerHasUnitsInSystem(player, tile) || tile.hasLegendary() || FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game)) {
                continue;
            }

            eligibleSystems.add(Buttons.green(player.factionButtonChecker() + MANDATE_SYSTEM + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
            event.getMessageChannel(),
            player.getRepresentation()
                + " please select the system you would like to place another player's command token in:",
            NewStuffHelper.buttonPagination(eligibleSystems, player.factionButtonChecker() + MANDATE_SYSTEM, 0));
    }

    @ButtonHandler(MANDATE_SYSTEM)
    public static void selectMandatePresenceTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasAbility("mandate_of_presence")) {
            return;
        }
        ButtonHelper.deleteMessage(event);
 
        game.setStoredValue("mandateUsedThisActionPhase_" + player.getFaction(), "true");

        String tilePos = buttonID.replace(MANDATE_SYSTEM, "");
        if (tilePos == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unable to locate selected tile.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> targets = new ArrayList<>();
        for (Player otherPlayer : game.getRealPlayers()) {
            if (otherPlayer == player) {
                continue;
            }

            targets.add(Buttons.gray(player.factionButtonChecker() + MANDATE_TARGET + otherPlayer.getColorID() + "|" + tilePos, otherPlayer.getFactionNameOrColor(), otherPlayer.getFactionEmojiOrColor()));
        }

        MessageHelper.sendMessageToChannelWithButtons(
            event.getMessageChannel(),
            player.getRepresentation()
                + " choose the player whose command token will be placed in the system:",
            targets);
    }

    @ButtonHandler(MANDATE_TARGET)
    public static void finishMandatePresence(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (player == null || game == null || !player.hasAbility("mandate_of_presence")) {
            return;
        }

        String payload = buttonID.substring(MANDATE_TARGET.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String faction = parts[0];
        String tilePos = parts[1];

        Player target = game.getPlayerFromColorOrFaction(faction);
        Tile tile = game.getTileByPosition(tilePos);

        if (target == null || tile == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        CommandCounterHelper.addCC(event, target, tile);
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
            event.getMessageChannel(),
            player.getRepresentation()
                + " used _Mandate of Presence_, to add "
                + target.getRepresentationNoPing() + "'s"
                + " command token to "
                + tile.getRepresentation());
    }
}
