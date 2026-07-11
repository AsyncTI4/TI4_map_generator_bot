package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.List;
import java.util.stream.Stream;
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
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.planet.PlanetService;
import ti4.service.unit.AddUnitService;

@UtilityClass
class LiberationAcd2ButtonHandler {

    @ButtonHandler("resolveLiberation")
    public static void resolveLiberation(Player player, Game game, ButtonInteractionEvent event) {
        Tile activeSystem = game.getTileByPosition(game.getCurrentActiveSystem());
        Stream<String> liberationPlanets = activeSystem == null
                ? player.getPlanets().stream()
                : activeSystem.getPlanetUnitHolders().stream().map(Planet::getName);
        List<Button> buttons = liberationPlanets
                .map(planet ->
                        Buttons.green("resolveLiberationStep2_" + planet, Helper.getPlanetRepresentation(planet, game)))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.toString() + " has no planets to target with _Liberation_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.toString() + ", choose the planet you just gained for _Liberation_.",
                buttons);
    }

    @ButtonHandler("resolveLiberationStep2_")
    public static void resolveLiberationStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("resolveLiberationStep2_", "");
        Tile tile = game.getTileContainingPlanet(planet);
        if (tile == null || !player.hasPlanet(planet)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not resolve _Liberation_ for that planet.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "2 inf " + planet);
        PlanetService.refreshPlanet(player, planet);

        List<Button> buttons = ButtonHelper.getPlanetExplorationButtons(game, game.getPlanet(planet), player);
        if (buttons != null && !buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getFactionEmoji() + ", please press the button to explore "
                            + Helper.getPlanetRepresentation(planet, game) + ".",
                    buttons);
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.toString() + " placed 2 infantry on and readied "
                        + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game)
                        + " via _Liberation_.");
    }
}
