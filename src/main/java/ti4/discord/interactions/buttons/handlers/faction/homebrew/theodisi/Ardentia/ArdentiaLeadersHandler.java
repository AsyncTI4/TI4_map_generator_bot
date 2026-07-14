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
import ti4.helpers.ButtonHelperAgents;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
public class ArdentiaLeadersHandler {
    private static final String AGENT_TARGET = "ardentiaAgentTarget_";
    private static final String AGENT_PAYMENT_DONE = "ardentiaAgentPaymentDone_";
    private static final String ARDENTIA_HERO_TARGET = "ardentiaHeroTarget_";
    private static final String ARDENTIA_HERO_REMOVE = "ardentiaHeroRemoveCC_";

    // Agent
    public static void startArdentiaAgentStep1(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + AGENT_TARGET + target.getFaction(),
                    target.getFactionNameOrColor(),
                    target.getFactionEmojiOrColor()));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                player.getRepresentation()
                        + ", please select the player that will spend 1 influence to gain 1 command token:",
                buttons);
    }

    @ButtonHandler(AGENT_TARGET)
    public static void startArdentiaAgentStep2(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null) {
            return;
        }
        ButtonHelper.deleteMessage(event);

        String targetFaction = buttonID.replace(AGENT_TARGET, "");
        Player targetPlayer = game.getPlayerFromColorOrFaction(targetFaction);

        if (targetPlayer == null) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Could not find selected player.");
            return;
        }

        List<Button> buttons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, targetPlayer));
        buttons.add(Buttons.red(targetPlayer.factionButtonChecker() + AGENT_PAYMENT_DONE, "Done"));

        MessageHelper.sendMessageToChannelWithButtons(
                targetPlayer.getCardsInfoThread(),
                targetPlayer.getRepresentation() + ", please use the buttons below to spend 1 influence:",
                buttons);
    }

    @ButtonHandler(AGENT_PAYMENT_DONE)
    public static void startArdentiaAgentStep3(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null) {
            return;
        }
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(), "Use these buttons to gain 1 CC:", ButtonHelper.getGainCCButtons(player));

        if (player.hasAbility("seize_command")) {
            MessageHelper.sendMessageToChannelWithButton(
                    player.getCardsInfoThread(),
                    "You main use _Seize Command_:",
                    ArdentiaAbilityHandler.getSeizeCommandButton(player));
        }
    }

    // Hero
    public static void startArdentiaHero(GenericInteractionCreateEvent event, Game game, Player player) {
        if (event == null || game == null || player == null) {
            return;
        }

        List<Button> targets = new ArrayList<>();
        for (Player otherPlayer : game.getRealPlayers()) {
            if (!ButtonHelper.getTilesWithYourCC(otherPlayer, game, event).isEmpty()) {
                targets.add(Buttons.gray(
                        player.factionButtonChecker() + ARDENTIA_HERO_TARGET + otherPlayer.getFaction(),
                        otherPlayer.getFactionNameOrColor(),
                        otherPlayer.getFactionEmojiOrColor()));
            }
        }
        targets.add(Buttons.red("deleteButtons", "Done"));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", you may remove a CC from a system containing another player's CC to gain 1 CC and 1 TG per other player's CC removed:",
                targets);
    }

    @ButtonHandler(ARDENTIA_HERO_TARGET)
    public static void selectArdentiaHeroTarget(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null) {
            return;
        }

        String faction = buttonID.replace(ARDENTIA_HERO_TARGET, "");
        Player target = game.getPlayerFromColorOrFaction(faction);

        if (target == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that player.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> tileButtons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesWithYourCC(target, game, event)) {
            tileButtons.add(Buttons.green(
                    player.factionButtonChecker()
                            + ARDENTIA_HERO_REMOVE
                            + target.getFaction() + "|"
                            + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", choose which of " + target.getRepresentationNoPing()
                        + "'s command token to return to reinforcements:",
                tileButtons);

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(ARDENTIA_HERO_REMOVE)
    public static void resolveRemoveArdentiaHeroTargetCC(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null) {
            return;
        }

        String payload = buttonID.substring(ARDENTIA_HERO_REMOVE.length());
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

        String ccId = Mapper.getCCID(target.getColor());
        tilePos.removeCC(ccId);
        String tgGain = player.gainTG(1, true);
        ButtonHelperAgents.resolveArtunoCheck(player, 1);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), player.getRepresentation() + " gained 1 trade good " + tgGain + ".");

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Please gain 1 CC from removing " + target.getRepresentationNoPing() + "'s CC from the chosen system.",
                ButtonHelper.getGainCCButtons(player));

        ButtonHelper.deleteMessage(event);
    }
}
