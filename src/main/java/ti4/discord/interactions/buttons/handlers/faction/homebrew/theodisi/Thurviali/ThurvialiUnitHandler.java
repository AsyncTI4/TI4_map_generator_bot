package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Thurviali;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class ThurvialiUnitHandler {
    private static final String USE_DOUBLE_DRAGONS_DEPLOY = "useDoubleDragonsDeploy";
    private static final String SELECT_DOUBLE_DRAGONS_PLANET = "selectDoubleDragonsPlanet_";
    private static final String REMOVE_DOUBLE_DRAGONS_STRUCTURE = "removeDoubleDragonsStructure_";
    private static final String DOUBLE_DRAGONS_PLANET = "doubleDragonsPlanet_";
    private static final String DOUBLE_DRAGONS_REMOVED = "doubleDragonsRemoved_";

    public static Button getDoubleDragonsDeployButton(Game game, Player player) {
        if (getDoubleDragonsPlanetButtons(game, player).isEmpty()) {
            return null;
        }

        return Buttons.green(
                player.factionButtonChecker() + USE_DOUBLE_DRAGONS_DEPLOY,
                "DEPLOY Double Dragons",
                FactionEmojis.thurviali);
    }

    @ButtonHandler(USE_DOUBLE_DRAGONS_DEPLOY)
    public static void useDoubleDragonsDeploy(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getDoubleDragonsPlanetButtons(game, player);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", choose a planet from which to remove 2 structures to place _Double Dragons_.",
                buttons);

        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_DOUBLE_DRAGONS_PLANET)
    public static void selectDoubleDragonsPlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring(SELECT_DOUBLE_DRAGONS_PLANET.length());
        Planet planet = game.getUnitHolderFromPlanet(planetName);

        if (planet == null || !canDeployDoubleDragons(game, player, planet)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        game.setStoredValue(DOUBLE_DRAGONS_PLANET + player.getFaction(), planetName);
        game.setStoredValue(DOUBLE_DRAGONS_REMOVED + player.getFaction(), "0");

        sendDoubleDragonsStructureButtons(event, game, player, planet);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(REMOVE_DOUBLE_DRAGONS_STRUCTURE)
    public static void removeDoubleDragonsStructure(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = game.getStoredValue(DOUBLE_DRAGONS_PLANET + player.getFaction());
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = planet == null ? null : game.getTileFromPlanet(planetName);
        String asyncId = buttonID.substring(REMOVE_DOUBLE_DRAGONS_STRUCTURE.length());

        UnitKey unitKey = planet == null
                ? null
                : planet.getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.asyncID().equals(asyncId))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), planet);

        if (planet == null
                || tile == null
                || unitKey == null
                || unit == null
                || !unit.getIsStructure()
                || !player.hasUnit("thurviali_flagship")
                || ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "fs") > 0
                || !player.getPlanetsAllianceMode().contains(planetName)
                || !FoWHelper.playerHasShipsInSystem(player, tile)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        RemoveUnitService.removeUnit(event, tile, game, player, planet, unitKey.unitType(), 1);

        int removed;
        try {
            removed = Integer.parseInt(game.getStoredValue(DOUBLE_DRAGONS_REMOVED + player.getFaction()));
        } catch (NumberFormatException e) {
            removed = 0;
        }
        removed++;

        if (removed < 2) {
            game.setStoredValue(DOUBLE_DRAGONS_REMOVED + player.getFaction(), Integer.toString(removed));
            sendDoubleDragonsStructureButtons(event, game, player, planet);
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "fs");

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " removed 2 structures from "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " and placed _Double Dragons_ in "
                        + tile.getRepresentationForButtons(game, player)
                        + ".");

        game.removeStoredValue(DOUBLE_DRAGONS_PLANET + player.getFaction());
        game.removeStoredValue(DOUBLE_DRAGONS_REMOVED + player.getFaction());
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getDoubleDragonsPlanetButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();

        for (String planetName : player.getPlanetsAllianceMode()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet == null || !canDeployDoubleDragons(game, player, planet)) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_DOUBLE_DRAGONS_PLANET + planetName,
                    "Remove structures from " + planet.getRepresentation(game),
                    FactionEmojis.thurviali));
        }

        return buttons;
    }

    private static void sendDoubleDragonsStructureButtons(
            ButtonInteractionEvent event, Game game, Player player, Planet planet) {
        List<Button> buttons = new ArrayList<>();

        for (UnitKey unitKey : planet.getUnitKeysForPlayer(player)) {
            UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), planet);
            if (unit == null || !unit.getIsStructure()) {
                continue;
            }

            buttons.add(Buttons.red(
                    player.factionButtonChecker() + REMOVE_DOUBLE_DRAGONS_STRUCTURE + unitKey.asyncID(),
                    "Remove " + unit.getName(),
                    unit.getUnitEmoji()));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", choose a structure to remove from "
                        + planet.getRepresentation(game)
                        + ".",
                buttons);
    }

    private static boolean canDeployDoubleDragons(Game game, Player player, Planet planet) {
        Tile tile = game.getTileFromPlanet(planet.getName());

        return player.hasUnit("thurviali_flagship")
                && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "fs") < 1
                && tile != null
                && player.getPlanetsAllianceMode().contains(planet.getName())
                && FoWHelper.playerHasShipsInSystem(player, tile)
                && planet.countPlayersUnitsWithModelCondition(player, UnitModel::getIsStructure) >= 2;
    }

    public static boolean hasCopiedMechAbility(Game game, Player player, String mechId) {
        return getCoexistingMechOwners(game, player).stream()
                .anyMatch(owner -> owner.getUnitsOwned().contains(mechId));
    }

    public static List<Player> getCoexistingMechOwners(Game game, Player player) {
        if (game == null || player == null || !player.getUnitsOwned().contains("thurviali_mech")) {
            return List.of();
        }
        return game.getRealPlayersNNeutral().stream()
                .filter(other -> other != player)
                .filter(other -> !player.getAllianceMembers().contains(other.getFaction()))
                .filter(other -> other.getUnitsOwned().stream()
                        .map(Mapper::getUnit)
                        .anyMatch(unit -> unit != null && unit.getUnitType() == UnitType.Mech))
                .filter(other -> other.getPlanets().stream().anyMatch(planetName -> {
                    UnitHolder planet = game.getUnitHolderFromPlanet(planetName);
                    return planet != null && FoWHelper.playerHasUnitsOnPlanet(player, planet);
                }))
                .toList();
    }

    public static boolean isStructureUnitAbilitySuppressed(Player player, UnitHolder holder, UnitModel unit) {
        return player != null
                && holder != null
                && unit != null
                && player.hasAbility("radiant_grafting_flight")
                && unit.getIsStructure()
                && "space".equalsIgnoreCase(holder.getName());
    }

    public static void destroyBlockadedFlightStructures(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!player.hasAbility("radiant_grafting_flight")) {
            return;
        }
        for (Tile tile : game.getTileMap().values()) {
            UnitHolder space = tile.getSpaceUnitHolder();
            boolean blockaded = game.getRealPlayersNDummies().stream()
                    .anyMatch(other -> other != player && FoWHelper.playerHasActualShipsInSystem(other, tile));
            if (!blockaded) {
                continue;
            }
            List<UnitKey> structures = space.getUnitKeysForPlayer(player).stream()
                    .filter(key -> {
                        UnitModel unit = player.getUnitFromUnitKey(key);
                        return unit != null && unit.getIsStructure();
                    })
                    .toList();
            if (structures.isEmpty()) {
                continue;
            }
            for (UnitKey structure : structures) {
                DestroyUnitService.destroyUnit(
                        event, tile, game, structure, space.getUnitCount(structure), space, false);
            }
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " destroyed their blockaded floating structures in "
                            + tile.getRepresentation() + ".");
        }
    }
}
