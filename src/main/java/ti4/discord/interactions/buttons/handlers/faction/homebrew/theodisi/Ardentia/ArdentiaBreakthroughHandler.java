package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Ardentia;

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
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
public class ArdentiaBreakthroughHandler {
    private static final String ARDENTIA_BT_TARGET = "ardentiaBtTarget_";
    private static final String ARDENTIA_BT_REMOVE = "ardentiaBtRemove_";
    private static final String ARDENTIA_BT_PAYMENT_DONE = "ardentiaBtPaymentDone_";

    public static void startSubjugate(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        List<Button> targets = new ArrayList<>();
        for (Player otherPlayer : game.getRealPlayers()) {
            if (otherPlayer != player
                    && !ButtonHelper.getTilesWithYourCC(otherPlayer, game, event)
                            .isEmpty()) {
                targets.add(Buttons.gray(
                        player.factionButtonChecker() + ARDENTIA_BT_TARGET + otherPlayer.getFaction(),
                        otherPlayer.getFactionNameOrColor(),
                        otherPlayer.getFactionEmojiOrColor()));
            }
        }
        targets.add(Buttons.red("deleteButtons", "Done"));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose the player whose CC you will remove for _Subjugate_:",
                targets);
    }

    @ButtonHandler(ARDENTIA_BT_TARGET)
    public static void selectArdentiaBtTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || player == null || game == null) {
            return;
        }

        String faction = buttonID.replace(ARDENTIA_BT_TARGET, "");
        Player target = game.getPlayerFromColorOrFaction(faction);

        if (target == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that player.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> tileButtons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesWithYourCC(target, game, event)) {
            tileButtons.add(Buttons.green(
                    player.factionButtonChecker() + ARDENTIA_BT_REMOVE + target.getFaction() + "|" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", choose which of " + target.getRepresentationNoPing()
                        + "'s command token to return to reinforcements:",
                tileButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(ARDENTIA_BT_REMOVE)
    public static void resolveRemoveArdentiaBtTargetCC(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || player == null || game == null) {
            return;
        }

        String payload = buttonID.substring(ARDENTIA_BT_REMOVE.length());
        String[] parts = payload.split("\\|", 2);

        String faction = parts[0];
        String tile = parts[1];

        Player target = game.getPlayerFromColorOrFaction(faction);
        Tile tilePos = game.getTileByPosition(tile);

        if (target == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not fint that player.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (tilePos == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not fint that tile.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!tilePos.hasPlayerCC(target)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Selected player does not have a CC in that system.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> paymentButtons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, "inf"));
        paymentButtons.add(Buttons.red(
                player.factionButtonChecker() + ARDENTIA_BT_PAYMENT_DONE + target.getFaction() + "|" + tile,
                "Done Paying 3 Influence"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please use the buttons below to pay 3 influence:",
                paymentButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(ARDENTIA_BT_PAYMENT_DONE)
    public static void resolveSubjugatePayment(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(ARDENTIA_BT_PAYMENT_DONE.length()).split("\\|", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        Tile tile = game.getTileByPosition(parts[1]);
        if (target == null || tile == null || !tile.hasPlayerCC(target)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "That command token is no longer available to remove.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        tile.removeCC(Mapper.getCCID(target.getColor()));
        ButtonHelperTacticalAction.resetStoredValuesForTacticalAction(game);
        game.removeStoredValue("fortuneSeekers");
        game.setComponentAction(true);
        game.setWarfareAction(true);
        game.setStoredValue("ardentiaSubjugate", "true");
        ButtonHelperTacticalAction.beginTacticalAction(game, player);
        ButtonHelper.deleteMessage(event);
    }
}
