package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Thrones;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class ThronesLeadersHandler {
    private static final String AGENT_ID = "thronesagent";
    private static final String SELECT_TARGET = "thronesAgentSelectTarget";
    private static final String SELECT_TARGET_PREFIX = "thronesAgentUseOn_";
    private static final String SELECT_SHIP_PREFIX = "thronesAgentChooseShip_";
    private static final String PLACE_SHIP_PREFIX = "thronesAgentPlaceShip_";
    private static final String CHOOSE_THRONE = "chooseThronePlanet_";
    private static final List<String> THRONE_PLANETS = List.of("cineron", "gyraxis", "lethara", "skarnath");
    private static final String DONE_CHOOSING_THRONE = "doneChoosingThronePlanets";

    // Veythros
    public static boolean veythrosIgnoresAnomalies(Game game, Player player) {
        return game != null && player != null && game.playerHasLeaderUnlockedOrAlliance(player, "thronescommander");
    }

    // Malrik
    public static Button getThronesAgentButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + SELECT_TARGET, "Use Malrik the Defiler", FactionEmojis.thrones);
    }

    @ButtonHandler(SELECT_TARGET)
    public static void selectThronesAgentTarget(ButtonInteractionEvent event, Game game, Player agentOwner) {
        if (game == null || agentOwner == null || !agentOwner.hasUnexhaustedLeader(AGENT_ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "**Malrik the Defiler** is no longer available.");
            return;
        }

        List<Button> buttons = game.getRealPlayers().stream()
                .filter(target -> !getEligibleShipModels(target).isEmpty())
                .filter(target -> !getEligibleDestinationTiles(game, target).isEmpty())
                .map(target -> Buttons.gray(
                        agentOwner.factionButtonChecker() + SELECT_TARGET_PREFIX + target.getFaction(),
                        target.getColorDisplayName(),
                        target.fogSafeEmoji()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "No player currently has both a ship choice and an eligible space-dock system.");
            return;
        }

        MessageHelper.sendMessageToEventChannelWithEphemeralButtons(
                event,
                agentOwner.getRepresentationUnfogged()
                        + ", please choose the player whose destroyed ship Malrik the Defiler, the Thrones agent, should place.",
                buttons);
    }

    @ButtonHandler(SELECT_TARGET_PREFIX)
    public static void useThronesAgentOnTarget(
            ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        if (game == null || agentOwner == null || !agentOwner.hasUnexhaustedLeader(AGENT_ID)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "**Malrik the Defiler** is no longer available.");
            return;
        }

        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_TARGET_PREFIX.length()));
        if (target == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find that player.");
            return;
        }

        List<UnitModel> ships = getEligibleShipModels(target);
        if (ships.isEmpty() || getEligibleDestinationTiles(game, target).isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That player no longer has an eligible ship choice and space-dock system.");
            return;
        }

        List<Button> buttons = ships.stream()
                .map(ship -> Buttons.gray(
                        target.factionButtonChecker()
                                + SELECT_SHIP_PREFIX
                                + agentOwner.getFaction()
                                + "~"
                                + ship.getAsyncId(),
                        "Place " + ship.getName(),
                        ship.getUnitEmoji()))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged()
                        + ", please choose which destroyed ship Malrik the Defiler, the Thrones agent, should place.",
                buttons);
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "Sent ship-choice buttons to " + target.getRepresentationUnfoggedNoPing() + ".");
    }

    @ButtonHandler(SELECT_SHIP_PREFIX)
    public static void selectThronesAgentDestination(
            ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        if (game == null || target == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not resolve that ship choice.");
            return;
        }
        String[] parts = buttonID.substring(SELECT_SHIP_PREFIX.length()).split("~", 2);
        if (parts.length != 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not resolve that ship choice.");
            return;
        }

        Player agentOwner = game.getPlayerFromColorOrFaction(parts[0]);
        UnitModel ship = target.getUnitFromAsyncID(parts[1]);
        if (agentOwner == null
                || !agentOwner.hasUnexhaustedLeader(AGENT_ID)
                || !isEligibleShipModel(ship)
                || getEligibleDestinationTiles(game, target).isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That **Malrik the Defiler** choice is no longer available.");
            return;
        }

        String message = target.getRepresentationUnfogged()
                + ", please choose the space dock system in which to place your "
                + ship.getName()
                + ".";
        List<Button> buttons = getDestinationButtons(game, target, agentOwner, ship);
        String buttonPrefix = target.factionButtonChecker()
                + PLACE_SHIP_PREFIX
                + agentOwner.getFaction()
                + "~"
                + ship.getAsyncId()
                + "~";
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(), message, NewStuffHelper.buttonPagination(buttons, buttonPrefix, 0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_SHIP_PREFIX)
    public static void placeThronesAgentShip(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        if (game == null || target == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not resolve that placement choice.");
            return;
        }
        String[] parts = buttonID.substring(PLACE_SHIP_PREFIX.length()).split("~", 3);
        if (parts.length != 3) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not resolve that placement choice.");
            return;
        }

        Player agentOwner = game.getPlayerFromColorOrFaction(parts[0]);
        UnitModel ship = target.getUnitFromAsyncID(parts[1]);
        if (agentOwner == null || !agentOwner.hasUnexhaustedLeader(AGENT_ID) || !isEligibleShipModel(ship)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That **Malrik the Defiler** choice is no longer available.");
            return;
        }

        String message = target.getRepresentationUnfogged()
                + ", please choose the space dock system in which to place your "
                + ship.getName()
                + ".";
        List<Button> buttons = getDestinationButtons(game, target, agentOwner, ship);
        String buttonPrefix = target.factionButtonChecker()
                + PLACE_SHIP_PREFIX
                + agentOwner.getFaction()
                + "~"
                + ship.getAsyncId()
                + "~";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }

        Tile destination = game.getTileByPosition(parts[2]);
        if (destination == null
                || getEligibleDestinationTiles(game, target).stream()
                        .noneMatch(tile -> tile.getPosition().equals(destination.getPosition()))) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That space-dock system is no longer eligible.");
            return;
        }

        Leader agent = agentOwner.getLeader(AGENT_ID).orElse(null);
        if (agent == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find **Malrik the Defiler**.");
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
        AddUnitService.addUnits(event, destination, game, target.getColor(), "1 " + ship.getAsyncId());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                agentOwner.getRepresentation()
                        + " exhausted **Malrik the Defiler**, the Thrones agent, to place "
                        + target.getRepresentation()
                        + "'s "
                        + ship.getName()
                        + " in "
                        + destination.getRepresentationForButtons(game, target)
                        + ".");
    }

    private static List<UnitModel> getEligibleShipModels(Player player) {
        return player.getUnitModels().stream()
                .filter(ThronesLeadersHandler::isEligibleShipModel)
                .sorted(Comparator.comparing(UnitModel::getName))
                .toList();
    }

    private static boolean isEligibleShipModel(UnitModel unitModel) {
        return unitModel != null && unitModel.getIsShip();
    }

    private static List<Tile> getEligibleDestinationTiles(Game game, Player target) {
        List<Tile> tiles = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, target, UnitType.Spacedock)) {
            boolean otherPlayerHasUnits = game.getPlayers().values().stream()
                    .anyMatch(otherPlayer ->
                            otherPlayer != target && FoWHelper.playerHasUnitsInSystem(otherPlayer, tile));
            if (!otherPlayerHasUnits) tiles.add(tile);
        }
        return tiles;
    }

    private static List<Button> getDestinationButtons(Game game, Player target, Player agentOwner, UnitModel ship) {
        return getEligibleDestinationTiles(game, target).stream()
                .map(tile -> Buttons.gray(
                        target.factionButtonChecker()
                                + PLACE_SHIP_PREFIX
                                + agentOwner.getFaction()
                                + "~"
                                + ship.getAsyncId()
                                + "~"
                                + tile.getPosition(),
                        "Place " + ship.getName() + " in " + tile.getRepresentationForButtons(game, target)))
                .toList();
    }

    // Seraphane the Eternal
    public static void getUnplacedThronePlanetButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        List<Button> buttons = new ArrayList<>();

        for (String planet : THRONE_PLANETS) {
            if (game.getTileFromPlanet(planet) != null) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + CHOOSE_THRONE + planet,
                    "Place " + Mapper.getPlanet(planet).getName(),
                    FactionEmojis.thrones));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + DONE_CHOOSING_THRONE, "Done Choosing Thrones"));

        List<MessageEmbed> thronesEmbed = new ArrayList<>();
        for (String planet : THRONE_PLANETS) {
            if (game.getTileFromPlanet(planet) != null) {
                continue;
            }

            thronesEmbed.add(Mapper.getPlanet(planet).getRepresentationEmbed());
        }

        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose the 2 Throne planets to place in your home system.",
                thronesEmbed,
                buttons);
    }

    @ButtonHandler(CHOOSE_THRONE)
    public static void placeChosenThronePlanetInHs(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String planet = buttonID.replace(CHOOSE_THRONE, "");
        if (planet == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile homeSystem = player.getHomeSystemTile();
        String tokenID = Mapper.getTokenID(planet);
        if (!THRONE_PLANETS.contains(planet)
                || homeSystem == null
                || tokenID == null
                || game.getTileFromPlanet(planet) != null) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        homeSystem.addToken(tokenID, Constants.SPACE);
        Helper.addTokenPlanetToTile(game, homeSystem, planet);
        game.clearPlanetsCache();
        player.addPlanet(planet);

        String planetName = Mapper.getPlanet(planet).getName();
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " placed " + planetName
                        + " in their home system and gained control of it.");

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(DONE_CHOOSING_THRONE)
    public static void resolveThronesHeroFinalStep(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        Tile homeSystem = player.getHomeSystemTile();

        if (homeSystem != null) {
            for (Planet planet : homeSystem.getPlanetUnitHolders()) {
                player.refreshPlanet(planet.getName());
            }
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Readied all planets in your home system.");

        ButtonHelper.deleteMessage(event);
    }
}
