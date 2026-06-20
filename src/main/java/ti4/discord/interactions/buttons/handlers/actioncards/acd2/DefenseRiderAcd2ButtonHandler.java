package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
class DefenseRiderAcd2ButtonHandler {

    @ButtonHandler("resolveDefenseRider")
    public static void resolveDefenseRider(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        sendDefenseRiderButtons(player, game, 2);
    }

    @ButtonHandler("resolveDefenseRiderStep2_")
    public static void resolveDefenseRiderStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("resolveDefenseRiderStep2_", "");
        String[] parts = payload.split("_", 2);
        if (parts.length < 2) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Defense Rider_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        int remaining;
        try {
            remaining = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Defense Rider_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String planet = parts[1];
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "pds " + planet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " put 1 PDS on " + Helper.getPlanetRepresentation(planet, game)
                        + " with _Defense Rider_.");
        ButtonHelper.deleteMessage(event);
        if (remaining > 1) {
            sendDefenseRiderButtons(player, game, remaining - 1);
        }
    }

    private static void sendDefenseRiderButtons(Player player, Game game, int remainingPds) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            buttons.add(Buttons.green(
                    "resolveDefenseRiderStep2_" + remainingPds + "_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no planets available for _Defense Rider_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));

        String message = remainingPds > 1
                ? player.getRepresentationUnfogged() + ", choose a planet to place a PDS on with _Defense Rider_."
                : player.getRepresentationUnfogged() + ", you may place 1 more PDS with _Defense Rider_.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }
}
