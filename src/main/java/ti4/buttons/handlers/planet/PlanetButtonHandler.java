package ti4.buttons.handlers.planet;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.planet.PlanetExhaustAbility;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.explore.ExploreService;

import java.util.List;

@UtilityClass
class PlanetButtonHandler {

    @ButtonHandler("garboziaAbilityExhaust_")
    public static void garboziaAbilityExhaust(ButtonInteractionEvent event, Player player, Game game) {
        String planet = "garbozia";
        player.exhaustPlanetAbility(planet);
        ExploreService.explorePlanet(event, game.getTileFromPlanet(planet), planet, "INDUSTRIAL", player, true, game, 1,
            false);
    }

    @ButtonHandler("planetAbilityExhaust_")
    public static void planetAbilityExhaust(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.replace("planetAbilityExhaust_", "");
        PlanetExhaustAbility.doAction(event, player, planet, game, true);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("diploSystem")
    public static void diploSystem(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + ", please choose the system you wish to Diplo.";
        List<Button> buttons = Helper.getPlanetSystemDiploButtons(player, game, false, null);
        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, message, buttons);
    }

    @ButtonHandler("starChartsStep1_")
    public static void starChartsStep1(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        ButtonHelper.starChartStep1(game, player, buttonID.split("_")[1]);
    }

    @ButtonHandler("absolX89Nuke_")
    public static void absolX89Nuke(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        String planet = buttonID.split("_")[1];
        MessageHelper.sendMessageToChannel(event.getChannel(),
            player.getFaction() + " used _X-89 Bacterial Weapon_ to remove all ground forces on " + planet + ".");

        Tile tile = game.getTileFromPlanet(planet);
        ButtonHelper.removeAllGroundForcesFromPlanet(planet, tile, game);
    }
}