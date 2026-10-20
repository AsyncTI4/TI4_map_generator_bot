package ti4.discord.interactions.buttons.handlers.actioncards.bluereverie;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.fow.PlanetTargetService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class PartisansBrActionCardHandler {
    private static final String RESOLVE = "resolvePartisansBr";
    private static final String SELECT_UNIT = "partisansSelectUnit_";
    private static final String PLACE_UNIT = "partisansPlaceUnit_";

    public static void offerResolution(Game game, Player player, String message) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                message,
                List.of(Buttons.green(player.factionButtonChecker() + RESOLVE, "Resolve Partisans")));
    }

    @ButtonHandler(RESOLVE)
    public static void resolvePartisans(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getUnitButtons(player);
        if (buttons.isEmpty() || getPlanetButtons(game, player, "infantry").isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " cannot resolve _Partisans_ because they have no eligible"
                            + " neighboring planets.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.editMessageWithButtons(
                event, player.getRepresentationNoPing() + ", choose units to place with _Partisans_.", buttons);
    }

    @ButtonHandler(SELECT_UNIT)
    public static void selectUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String unit = buttonID.substring(SELECT_UNIT.length());
        if (!isSupportedUnit(unit)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That is not a valid _Partisans_ unit choice.");
            return;
        }
        List<Button> buttons = getPlanetButtons(game, player, unit);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "There are no eligible neighboring planets for _Partisans_.");
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                placementMessage(player, unit),
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + PLACE_UNIT + unit + "|", 0));
    }

    @ButtonHandler(PLACE_UNIT)
    public static void placeUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(PLACE_UNIT.length()).split("\\|", 2);
        String unit = values.length == 2 ? values[0] : "";
        List<Button> buttons = getPlanetButtons(game, player, unit);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                placementMessage(player, unit),
                player.factionButtonChecker() + PLACE_UNIT + unit + "|",
                buttonID)) {
            return;
        }
        String planetName = values.length == 2 ? values[1] : "";
        if (!isSupportedUnit(unit) || !isEligiblePlanet(game, player, planetName)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That _Partisans_ placement is no longer eligible.");
            return;
        }
        Tile tile = game.getTileFromPlanet(planetName);
        String previousCoexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            AddUnitService.addUnits(
                    event, tile, game, player.getColor(), unitCount(unit) + " " + unit + " " + planetName);
        } finally {
            if (previousCoexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", previousCoexistenceFlag);
            }
        }
        ButtonHelperAbilities.oceanBoundCheck(game);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed " + unitCount(unit) + " " + unit + " into coexistence on "
                        + Helper.getPlanetRepresentation(planetName, game) + " with _Partisans_.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getUnitButtons(Player player) {
        return List.of(
                Buttons.green(player.factionButtonChecker() + SELECT_UNIT + "infantry", "Place 2 Infantry"),
                Buttons.green(player.factionButtonChecker() + SELECT_UNIT + "mech", "Place 1 Mech"));
    }

    private static List<Button> getPlanetButtons(Game game, Player player, String unit) {
        if (!isSupportedUnit(unit)) return List.of();
        Set<String> knownPlanets = PlanetTargetService.knownPlanetIds(game, player, Set.of());
        List<Button> buttons = new ArrayList<>();
        for (String planetName : knownPlanets.stream().sorted().toList()) {
            if (!isEligiblePlanet(game, player, planetName)) continue;
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE_UNIT + unit + "|" + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }
        return buttons;
    }

    private static boolean isEligiblePlanet(Game game, Player player, String planetName) {
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        Player controller = game.getPlayerThatControlsPlanet(planetName, true);
        return planet != null
                && tile != null
                && planet.getPlanetModel() != null
                && !planet.isSpaceStation()
                && !tile.isHomeSystem(game)
                && controller != null
                && player.getNeighbouringPlayers(true).contains(controller);
    }

    private static int unitCount(String unit) {
        return "infantry".equals(unit) ? 2 : 1;
    }

    private static boolean isSupportedUnit(String unit) {
        return "infantry".equals(unit) || "mech".equals(unit);
    }

    private static String placementMessage(Player player, String unit) {
        return player.getRepresentationNoPing() + ", choose a non-home planet a neighbor controls to place "
                + unitCount(unit) + " " + unit + " into coexistence with _Partisans_.";
    }
}
