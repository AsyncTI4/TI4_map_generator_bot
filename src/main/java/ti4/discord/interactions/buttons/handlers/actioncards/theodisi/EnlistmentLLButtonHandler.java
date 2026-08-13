package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
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
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class EnlistmentLLButtonHandler {
    private static final String RESOLVE = "resolveEnlistment";
    private static final String PLACE = "placeEnlistment_";

    @ButtonHandler(RESOLVE)
    public static void resolveEnlistment(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getEligiblePlanetButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " has no eligible non-home planet for _Enlistment_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String message = player.getRepresentationNoPing()
                + ", choose a non-home planet adjacent to one of your units for _Enlistment_.";
        MessageHelper.editMessageWithButtons(
                event, message, NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + PLACE, 0));
    }

    @ButtonHandler(PLACE)
    public static void placeEnlistment(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getEligiblePlanetButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", choose a non-home planet adjacent to one of your units for _Enlistment_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, player.factionButtonChecker() + PLACE, buttonID)) {
            return;
        }
        String planetName = buttonID.substring(PLACE.length());
        Planet planet = game.getPlanetsInfo().get(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null
                || tile == null
                || planet.isHomePlanet(game)
                || !isAdjacentToPlayersUnits(game, player, tile)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That planet is no longer eligible for _Enlistment_.");
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "2 infantry " + planetName);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed 2 infantry on "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " with _Enlistment_. Resolve any resulting ground combat now.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getEligiblePlanetButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Planet planet = game.getPlanetsInfo().get(planetName);
            Tile tile = game.getTileFromPlanet(planetName);
            if (planet == null
                    || tile == null
                    || planet.isHomePlanet(game)
                    || !isAdjacentToPlayersUnits(game, player, tile)) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE + planetName,
                    "Place 2 Infantry on " + Helper.getPlanetRepresentation(planetName, game)));
        }
        return buttons;
    }

    private static boolean isAdjacentToPlayersUnits(Game game, Player player, Tile tile) {
        return FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false).stream()
                .map(game::getTileByPosition)
                .anyMatch(adjacent -> FoWHelper.playerHasUnitsInSystem(player, adjacent));
    }
}
