package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.CommanderUnlockCheckService;

class ButtonHelperRelics {

    @ButtonHandler("jrResolution_")
    public static void jrResolution(Player player, String buttonID, Game game, ButtonInteractionEvent event) {
        String faction2 = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction2);
        if (p2 != null) {
            Button sdButton = Buttons.green("jrStructure_sd", "Place 1 Space Dock", UnitEmojis.spacedock);
            Button pdsButton = Buttons.green("jrStructure_pds", "Place 1 PDS", UnitEmojis.pds);
            Button tgButton = Buttons.green("jrStructure_tg", "Gain 1 Trade Good");
            List<Button> buttons = new ArrayList<>();
            buttons.add(sdButton);
            buttons.add(pdsButton);
            buttons.add(tgButton);
            String msg = p2.getRepresentationUnfogged() + ", please choose which structure to build.";
            MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), msg, buttons);
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler("prophetsTears_")
    public static void prophetsTears(Player player, String buttonID, Game game, ButtonInteractionEvent event) {
        player.addExhaustedRelic("prophetstears");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getFactionEmoji() + " is exhausting _The Prophet's Tears_.");
        if (buttonID.contains("AC")) {
            String message;
            if (player.hasAbility("scheming")) {
                game.drawActionCard(player.getUserID());
                game.drawActionCard(player.getUserID());
                message = player.getFactionEmoji()
                        + " drew 2 action cards with **Scheming**. Please discard 1 action card with the blue buttons.";
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        player.getRepresentationUnfogged() + " use buttons to discard",
                        ActionCardHelper.getDiscardActionCardButtons(player, false));
            } else if (player.hasAbility("autonetic_memory")) {
                ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
                message = player.getFactionEmoji() + " triggered **Autonetic Memory Option**.";
            } else {
                game.drawActionCard(player.getUserID());
                message = player.getFactionEmoji() + " drew 1 action card.";
                ActionCardHelper.sendActionCardInfo(game, player, event);
            }
            CommanderUnlockCheckService.checkPlayer(player, "yssaril");

            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            ButtonHelper.checkACLimit(game, player);
            ButtonHelper.deleteTheOneButton(event);
        } else {
            String msg = " exhausted _The Prophet's Tears_.";
            String exhaustedMessage = event.getMessage().getContentRaw();
            List<ActionRow> actionRow2 = new ArrayList<>();
            for (ActionRow row : event.getMessage().getActionRows()) {
                List<ItemComponent> buttonRow = row.getComponents();
                int buttonIndex = buttonRow.indexOf(event.getButton());
                if (buttonIndex > -1) {
                    buttonRow.remove(buttonIndex);
                }
                if (!buttonRow.isEmpty()) {
                    actionRow2.add(ActionRow.of(buttonRow));
                }
            }
            if (!exhaustedMessage.contains("Please choose the")) {
                exhaustedMessage += ", " + msg;
            } else {
                exhaustedMessage = player.getRepresentation() + msg;
            }
            event.getMessage()
                    .editMessage(exhaustedMessage)
                    .setComponents(actionRow2)
                    .queue();
        }
    }

    public static void offerNanoforgeButtons(Player player, Game game, GenericInteractionCreateEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanetsAllianceMode()) {
            if (game.getPlanetsInfo().get(planet) == null) continue;

            boolean legendaryOrHome = ButtonHelper.isPlanetLegendaryOrHome(planet, game, false, null);
            if (!legendaryOrHome) {
                buttons.add(Buttons.green("nanoforgePlanet_" + planet, Helper.getPlanetRepresentation(planet, game)));
            }
        }
        String message = "Please choose which planet you wish to attach _Nano-Forge_ to.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }

    @ButtonHandler("nanoforgePlanet_")
    public static void nanoforgePlanet(ButtonInteractionEvent event, String buttonID, Game game) {
        String planet = buttonID.replace("nanoforgePlanet_", "");
        Planet planetReal = game.getPlanetsInfo().get(planet);
        planetReal.addToken("attachment_nanoforge.png");
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Attached _Nano-Forge_ to " + Helper.getPlanetRepresentation(planet, game) + ".");
        ButtonHelper.deleteMessage(event);
    }
}
