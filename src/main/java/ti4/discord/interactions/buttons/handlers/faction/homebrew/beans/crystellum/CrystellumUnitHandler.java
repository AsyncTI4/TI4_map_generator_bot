package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class CrystellumUnitHandler {
    private static final String USE_SHARD_SWARM = "useShardSwarm";
    private static final String SHARD_SWARM_SYSTEM = "shardSwarmSystem_";
    private static final String PLACE_SHARD_SWARM = "placeShardSwarm_";
    private static final String USE_FRACTAL_REBUILD = "useFractalRebuild_";
    private static final String FRACTAL_RETURN = "fractalReturn_";
    private static final String FRACTAL_SELECT_SHIP = "fractalSelectShip_";
    private static final String USE_REFRACTUM_DEPLOY = "useRefractumDeploy_";
    private static final String PLACE_REFRACTUM_DEPLOY = "placeRefractumDeploy_";

    public static void offerFractalRebuild(GenericInteractionCreateEvent event, Game game, Player player, Tile tile) {
        int capturedFighters = player.getNomboxTile().getUnitHolders().values().stream()
                .mapToInt(holder -> holder.getUnitCount(UnitType.Fighter, player))
                .sum();
        int capacity = player.getUnitModels().stream()
                .filter(unit -> "crystellum_flagship".equals(unit.getId()))
                .mapToInt(UnitModel::getCapacityValue)
                .findFirst()
                .orElse(0);
        if (capacity < 1
                || capturedFighters == 0
                || player.getUnitModels().stream()
                        .noneMatch(unit -> unit.getIsShip()
                                && !"flagship".equalsIgnoreCase(unit.getBaseType())
                                && unit.getCost() > 0
                                && unit.getCost() <= Math.min(capacity, capturedFighters))) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", you may return up to " + capacity
                        + " captured fighters with the Fractal (the Crystellum flagship) to place 1 ship in "
                        + tile.getRepresentationForButtons(game, player) + ".",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + USE_FRACTAL_REBUILD + tile.getPosition(),
                                "Use The Fractal"),
                        Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline")));
    }

    public static void addRefractumDeployButton(List<Button> buttons, Player player, Tile tile) {
        int capturedFighters = player.getNomboxTile().getUnitHolders().values().stream()
                .mapToInt(holder -> holder.getUnitCount(UnitType.Fighter, player))
                .sum();
        if (!player.hasUnit("crystellum_mech")
                || capturedFighters < 2
                || tile == null
                || tile.getPlanetUnitHolders().isEmpty()) return;
        buttons.add(Buttons.green(
                player.factionButtonChecker() + USE_REFRACTUM_DEPLOY + tile.getPosition(),
                "Use Refractum",
                UnitEmojis.mech));
    }

    @ButtonHandler(USE_REFRACTUM_DEPLOY)
    public static void useRefractumDeploy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(USE_REFRACTUM_DEPLOY.length()));
        UnitHolder capturedFighters = player.getNomboxTile().getSpaceUnitHolder();
        if (tile == null
                || !player.hasUnit("crystellum_mech")
                || capturedFighters.getUnitCount(UnitType.Fighter, player) < 2) return;

        List<Button> buttons = tile.getPlanetUnitHolders().stream()
                .map(Planet::getName)
                .map(planetName -> Buttons.green(
                        player.factionButtonChecker() + PLACE_REFRACTUM_DEPLOY + tile.getPosition() + "|" + planetName,
                        "Place On " + Helper.getPlanetRepresentation(planetName, game),
                        UnitEmojis.mech))
                .toList();
        if (buttons.isEmpty()) return;

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose the planet on which to place a Refractum (Crystellum mech) with its DEPLOY ability.",
                buttons);
    }

    @ButtonHandler(PLACE_REFRACTUM_DEPLOY)
    public static void placeRefractumDeploy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(PLACE_REFRACTUM_DEPLOY.length()).split("\\|", 2);
        if (payload.length != 2) return;
        Tile tile = game.getTileByPosition(payload[0]);
        UnitHolder planet = tile == null ? null : tile.getUnitHolders().get(payload[1]);
        UnitHolder capturedFighters = player.getNomboxTile().getSpaceUnitHolder();
        if (!(planet instanceof Planet)
                || !player.hasUnit("crystellum_mech")
                || capturedFighters.getUnitCount(UnitType.Fighter, player) < 2) return;

        capturedFighters.removeUnit(Units.getUnitKey(UnitType.Fighter, player.getColor()), 2);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + planet.getName());
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " returned 2 captured fighters and placed 1 mech on "
                        + Helper.getPlanetRepresentation(planet.getName(), game)
                        + " with a Refractum (Crystellum mech)'s DEPLOY ability.");
    }

    @ButtonHandler(USE_FRACTAL_REBUILD)
    public static void useFractalRebuild(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(USE_FRACTAL_REBUILD.length()));
        int capturedFighters = player.getNomboxTile().getUnitHolders().values().stream()
                .mapToInt(holder -> holder.getUnitCount(UnitType.Fighter, player))
                .sum();
        int capacity = player.getUnitModels().stream()
                .filter(unit -> "crystellum_flagship".equals(unit.getId()))
                .mapToInt(UnitModel::getCapacityValue)
                .findFirst()
                .orElse(0);
        if (tile == null
                || capacity < 1
                || capturedFighters == 0
                || player.getUnitModels().stream()
                        .noneMatch(unit -> unit.getIsShip()
                                && !"flagship".equalsIgnoreCase(unit.getBaseType())
                                && unit.getCost() > 0
                                && unit.getCost() <= Math.min(capacity, capturedFighters))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (int amount = 1; amount <= Math.min(capacity, capturedFighters); amount++) {
            int fightersReturned = amount;
            if (player.getUnitModels().stream()
                    .noneMatch(unit -> unit.getIsShip()
                            && !"flagship".equalsIgnoreCase(unit.getBaseType())
                            && unit.getCost() > 0
                            && unit.getCost() <= fightersReturned)) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + FRACTAL_RETURN + tile.getPosition() + "|" + amount,
                    "Return " + amount + " Fighter" + (amount == 1 ? "" : "s"),
                    UnitEmojis.fighter));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", choose up to " + capacity
                        + " captured fighters to return for the Fractal (the Crystellum flagship).",
                buttons);
    }

    @ButtonHandler(FRACTAL_RETURN)
    public static void chooseFractalReturn(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(FRACTAL_RETURN.length()).split("\\|", 2);
        if (payload.length != 2) return;
        Tile tile = game.getTileByPosition(payload[0]);
        int returned = NumberUtils.toInt(payload[1]);
        int capturedFighters = player.getNomboxTile().getUnitHolders().values().stream()
                .mapToInt(holder -> holder.getUnitCount(UnitType.Fighter, player))
                .sum();
        int capacity = player.getUnitModels().stream()
                .filter(unit -> "crystellum_flagship".equals(unit.getId()))
                .mapToInt(UnitModel::getCapacityValue)
                .findFirst()
                .orElse(0);
        if (tile == null || returned < 1 || returned > capacity || returned > capturedFighters) return;

        List<Button> buttons = new ArrayList<>();
        player.getUnitModels().stream()
                .map(UnitModel::getAsyncId)
                .filter(asyncId -> asyncId != null)
                .distinct()
                .map(asyncId -> player.getPriorityUnitByAsyncID(asyncId, tile.getSpaceUnitHolder()))
                .filter(unit -> unit != null
                        && unit.getIsShip()
                        && !"flagship".equalsIgnoreCase(unit.getBaseType())
                        && unit.getCost() > 0
                        && unit.getCost() <= returned)
                .sorted(Comparator.comparing(UnitModel::getName))
                .forEach(unit -> buttons.add(Buttons.green(
                        player.factionButtonChecker() + FRACTAL_SELECT_SHIP + tile.getPosition() + "|" + returned + "|"
                                + unit.getAsyncId(),
                        "Place " + unit.getName(),
                        unit.getUnitEmoji())));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", choose 1 ship to place with the Fractal (the Crystellum flagship).",
                buttons);
    }

    @ButtonHandler(FRACTAL_SELECT_SHIP)
    public static void chooseFractalShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(FRACTAL_SELECT_SHIP.length()).split("\\|", 3);
        if (payload.length != 3) return;
        Tile tile = game.getTileByPosition(payload[0]);
        int returned = NumberUtils.toInt(payload[1]);
        int capturedFighters = player.getNomboxTile().getUnitHolders().values().stream()
                .mapToInt(holder -> holder.getUnitCount(UnitType.Fighter, player))
                .sum();
        int capacity = player.getUnitModels().stream()
                .filter(unit -> "crystellum_flagship".equals(unit.getId()))
                .mapToInt(UnitModel::getCapacityValue)
                .findFirst()
                .orElse(0);
        UnitModel ship = tile == null ? null : player.getPriorityUnitByAsyncID(payload[2], tile.getSpaceUnitHolder());
        if (returned < 1
                || returned > capacity
                || returned > capturedFighters
                || ship == null
                || !ship.getIsShip()
                || "flagship".equalsIgnoreCase(ship.getBaseType())
                || ship.getCost() <= 0
                || ship.getCost() > returned) {
            return;
        }

        RemoveUnitService.removeUnits(event, player.getNomboxTile(), game, player.getColor(), returned + " fighter");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + ship.getAsyncId());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " returned " + returned + " captured fighter"
                        + (returned == 1 ? "" : "s")
                        + " and placed " + ship.getUnitEmoji() + " " + ship.getName() + " in "
                        + tile.getRepresentationForButtons(game, player)
                        + " with the Fractal (the Crystellum flagship).");
    }

    public static Button addShardSwarmStartTurnButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + USE_SHARD_SWARM, "Use Shard Swarm", UnitEmojis.fighter);
    }

    @ButtonHandler(USE_SHARD_SWARM)
    public static void useShardSwarm(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getShardSwarmSystemButtons(game, player);

        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You do not control a planet in any eligible system.");
            return;
        }

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                getShardSwarmSystemMessage(player),
                getShardSwarmSystemButtonsPage(buttons, player));
    }

    @ButtonHandler(SHARD_SWARM_SYSTEM)
    public static void chooseShardSwarmSystem(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> systemButtons = getShardSwarmSystemButtons(game, player);
        String pagePrefix = player.factionButtonChecker() + SHARD_SWARM_SYSTEM;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                player.getCorrectChannel(),
                systemButtons,
                getShardSwarmSystemMessage(player),
                pagePrefix,
                buttonID)) {
            return;
        }

        String position = buttonID.replace(SHARD_SWARM_SYSTEM, "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null) {
            return;
        }

        boolean controlsPlanetHere = tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> player.getPlanets().contains(planet.getName()));
        if (!controlsPlanetHere || !player.hasUnit("crystellum_fighter3")) {
            return;
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", place up to 2 fighters in "
                        + tile.getRepresentationForButtons(game, player)
                        + " with the Shard Swarm (the Crystellum fighter).",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + PLACE_SHARD_SWARM + position + "_1",
                                "Place 1 Fighter",
                                UnitEmojis.fighter),
                        Buttons.green(
                                player.factionButtonChecker() + PLACE_SHARD_SWARM + position + "_2",
                                "Place 2 Fighters",
                                UnitEmojis.fighter),
                        Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline")));
    }

    @ButtonHandler(PLACE_SHARD_SWARM)
    public static void placeShardSwarms(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.replace(PLACE_SHARD_SWARM, "");
        int separator = payload.lastIndexOf('_');
        if (separator < 1) {
            return;
        }

        Tile tile = game.getTileByPosition(payload.substring(0, separator));
        if (tile == null || !player.hasUnit("crystellum_fighter3")) {
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(payload.substring(separator + 1));
        } catch (NumberFormatException e) {
            return;
        }

        amount = Math.min(2, Math.max(1, amount));
        AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " fighter");

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " placed "
                        + amount
                        + " fighter"
                        + (amount == 1 ? "" : "s")
                        + " in "
                        + tile.getRepresentationForButtons(game, player)
                        + " with the Shard Swarm (the Crystellum fighter).");
    }

    private static List<Button> getShardSwarmSystemButtons(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> player.getPlanets().contains(planet.getName())))
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + SHARD_SWARM_SYSTEM + tile.getPosition(),
                        "Choose " + tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static String getShardSwarmSystemMessage(Player player) {
        return player.getRepresentationUnfogged()
                + ", choose a system containing a planet you control for the Shard Swarm (the Crystellum fighter).";
    }

    private static List<Button> getShardSwarmSystemButtonsPage(List<Button> systemButtons, Player player) {
        if (systemButtons.size() < 25) {
            List<Button> buttons = new ArrayList<>(systemButtons);
            buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
            return buttons;
        }

        return NewStuffHelper.buttonPagination(
                systemButtons, null, player.factionButtonChecker() + SHARD_SWARM_SYSTEM, 25, 0, true);
    }
}
