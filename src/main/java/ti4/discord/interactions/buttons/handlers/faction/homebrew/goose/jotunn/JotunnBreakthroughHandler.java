package ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.jotunn;

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
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitType;
import ti4.helpers.adjacency.Adjacency;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class JotunnBreakthroughHandler {
    private static final String GLACIAL_DRIFT = "jotunnbt";
    private static final String SELECT_SOURCE = "selectGlacialDriftSource_";
    private static final String SELECT_DESTINATION = "selectGlacialDriftDestination_";

    public static void offerGlacialDrift(Game game, Player player) {
        if (!player.hasReadyBreakthrough(GLACIAL_DRIFT)) {
            return;
        }
        List<Button> sourceButtons = getSourceButtons(game, player);
        if (sourceButtons.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                sourceMessage(player),
                displayButtons(sourceButtons, player.factionButtonChecker() + SELECT_SOURCE));
    }

    @ButtonHandler(SELECT_SOURCE)
    public static void selectGlacialDriftSource(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> sourceButtons = getSourceButtons(game, player);
        String sourcePrefix = player.factionButtonChecker() + SELECT_SOURCE;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                sourceButtons,
                List.of(Buttons.DECLINE_DELETE_BUTTONS),
                sourceMessage(player),
                sourcePrefix,
                buttonID)) {
            return;
        }
        String sourcePlanetName = buttonID.substring(SELECT_SOURCE.length());
        Planet sourcePlanet = game.getUnitHolderFromPlanet(sourcePlanetName);
        if (!player.hasReadyBreakthrough(GLACIAL_DRIFT) || !isEligibleSource(game, player, sourcePlanet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> destinationButtons = getDestinationButtons(game, player, sourcePlanet);
        if (destinationButtons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                destinationMessage(player, sourcePlanetName, game),
                displayButtons(
                        destinationButtons,
                        player.factionButtonChecker() + SELECT_DESTINATION + sourcePlanetName + "|"));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_DESTINATION)
    public static void selectGlacialDriftDestination(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] planets = buttonID.substring(SELECT_DESTINATION.length()).split("\\|", 2);
        Planet sourcePlanet = planets.length == 2 ? game.getUnitHolderFromPlanet(planets[0]) : null;
        if (sourcePlanet != null) {
            List<Button> destinationButtons = getDestinationButtons(game, player, sourcePlanet);
            String destinationPrefix =
                    player.factionButtonChecker() + SELECT_DESTINATION + sourcePlanet.getName() + "|";
            if (NewStuffHelper.checkAndHandlePaginationChange(
                    event,
                    event.getMessageChannel(),
                    destinationButtons,
                    List.of(Buttons.DECLINE_DELETE_BUTTONS),
                    destinationMessage(player, sourcePlanet.getName(), game),
                    destinationPrefix,
                    buttonID)) {
                return;
            }
        }
        Planet destinationPlanet = planets.length == 2 ? game.getUnitHolderFromPlanet(planets[1]) : null;
        if (!player.hasReadyBreakthrough(GLACIAL_DRIFT)
                || !isEligibleDestination(game, player, sourcePlanet, destinationPlanet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile sourceTile = game.getTileFromPlanet(sourcePlanet.getName());
        List<RemovedUnit> removed =
                RemoveUnitService.removeUnit(event, sourceTile, game, player, sourcePlanet, UnitType.Pds, 1);
        if (removed.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        BreakthroughCommandHelper.exhaustBreakthrough(player, GLACIAL_DRIFT);
        AddUnitService.addUnits(
                event,
                game,
                removed.stream()
                        .map(unit -> unit.onUnitHolder(destinationPlanet))
                        .toList());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " moved 1 PDS from "
                        + Helper.getPlanetRepresentation(sourcePlanet.getName(), game) + " to "
                        + Helper.getPlanetRepresentation(destinationPlanet.getName(), game) + " with _Glacial Drift_.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getSourceButtons(Game game, Player player) {
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .filter(planet -> isEligibleSource(game, player, planet))
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + SELECT_SOURCE + planet.getName(),
                        "Move PDS from " + Helper.getPlanetRepresentation(planet.getName(), game),
                        FactionEmojis.jotunn))
                .toList();
    }

    private static List<Button> getDestinationButtons(Game game, Player player, Planet sourcePlanet) {
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .filter(destination -> isEligibleDestination(game, player, sourcePlanet, destination))
                .map(destination -> Buttons.green(
                        player.factionButtonChecker() + SELECT_DESTINATION + sourcePlanet.getName() + "|"
                                + destination.getName(),
                        "Move PDS to " + Helper.getPlanetRepresentation(destination.getName(), game),
                        FactionEmojis.jotunn))
                .toList();
    }

    private static boolean isEligibleSource(Game game, Player player, Planet sourcePlanet) {
        return sourcePlanet != null
                && player.getPlanets().contains(sourcePlanet.getName())
                && sourcePlanet.getUnitCount(UnitType.Pds, player) > 0
                && !getDestinationButtons(game, player, sourcePlanet).isEmpty();
    }

    private static boolean isEligibleDestination(
            Game game, Player player, Planet sourcePlanet, Planet destinationPlanet) {
        if (sourcePlanet == null
                || destinationPlanet == null
                || sourcePlanet == destinationPlanet
                || !player.getPlanets().contains(destinationPlanet.getName())) {
            return false;
        }
        Tile sourceTile = game.getTileFromPlanet(sourcePlanet.getName());
        Tile destinationTile = game.getTileFromPlanet(destinationPlanet.getName());
        return sourceTile != null
                && destinationTile != null
                && (sourceTile == destinationTile
                        || Adjacency.of(game, player)
                                .forEffects()
                                .from(sourceTile.getPosition())
                                .contains(destinationTile.getPosition()));
    }

    private static String sourceMessage(Player player) {
        return player.getRepresentationNoPing()
                + ", you may exhaust _Glacial Drift_ to move 1 PDS from a planet you control to another planet you control in the same or an adjacent system.";
    }

    private static String destinationMessage(Player player, String sourcePlanetName, Game game) {
        return player.getRepresentationNoPing() + ", choose where to place the PDS from "
                + Helper.getPlanetRepresentation(sourcePlanetName, game) + ".";
    }

    private static List<Button> displayButtons(List<Button> buttons, String prefix) {
        return buttons.size() < 25
                ? appendDeclineButton(buttons)
                : NewStuffHelper.buttonPagination(
                        buttons, List.of(Buttons.DECLINE_DELETE_BUTTONS), prefix, 25, 0, false);
    }

    private static List<Button> appendDeclineButton(List<Button> buttons) {
        List<Button> displayedButtons = new java.util.ArrayList<>(buttons);
        displayedButtons.add(Buttons.DECLINE_DELETE_BUTTONS);
        return displayedButtons;
    }
}
