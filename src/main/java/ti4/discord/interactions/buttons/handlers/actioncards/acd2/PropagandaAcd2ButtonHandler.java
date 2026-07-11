package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.planet.PlanetButtonService;
import ti4.service.unit.AddUnitService;

@UtilityClass
class PropagandaAcd2ButtonHandler {

    @ButtonHandler("resolvePropagandaTe")
    public static void resolvePropagandaTe(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        sendPropagandaButtons(player, game, 2);
    }

    @ButtonHandler("propagandaTeChoose_")
    public static void resolvePropagandaTeChoose(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("propagandaTeChoose_", "");
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
        Planet uH = game.getPlanet(planet);
        Tile tile = game.getTileContainingPlanet(planet);
        ButtonHelper.deleteMessage(event);
        if (uH == null || tile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Propaganda_.");
            return;
        }

        player.exhaustPlanet(planet);
        int influence = uH.getInfluence();
        if (influence > 0) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), influence + " infantry " + planet);
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " exhausted " + Helper.getPlanetRepresentation(planet, game)
                        + " and placed " + influence + " infantry there for _Propaganda_.");

        if (remaining > 1) {
            sendPropagandaButtons(player, game, remaining - 1);
        }
    }

    private static void sendPropagandaButtons(Player player, Game game, int remaining) {
        List<Button> buttons = PlanetButtonService.buttonsForOwnedPlanets(
                player,
                game,
                location -> !player.isPlanetExhausted(location.planet().getName())
                        && !location.planet().isHomePlanet(game)
                        && !location.planet().isLegendary()
                        && FoWHelper.playerHasUnitsOnPlanet(player, location.planet()),
                location -> Buttons.green(
                        player.factionButtonChecker() + "propagandaTeChoose_" + remaining + "_"
                                + location.planet().getName(),
                        location.planet().getRepresentation(game) + " ("
                                + location.planet().getInfluence() + " influence)"));
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " has no eligible planets remaining for _Propaganda_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        String remainingText = remaining == 1 ? "1 planet remaining" : remaining + " planets remaining";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose a non-home, non-legendary planet to exhaust and"
                        + " reinforce for _Propaganda_ (" + remainingText + ").",
                buttons);
    }
}
