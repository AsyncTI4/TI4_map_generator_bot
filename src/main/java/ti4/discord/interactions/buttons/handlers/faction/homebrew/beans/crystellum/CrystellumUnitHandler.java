package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
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
    private static final String FRACTAL_DONE = "fractalDone_";
    private static final String USE_REFRACTUM_DEPLOY = "useRefractumDeploy_";

    public static void offerFractalRebuild(Game game, Player player, Tile tile) {
        int capturedFighters = player.getNomboxTile().getUnitHolders().values().stream()
                .mapToInt(holder -> holder.getUnitCount(UnitType.Fighter, player))
                .sum();
        if (!player.hasUnit("crystellum_flagship") || capturedFighters == 0) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", you may return captured fighters with _The Fractal_ (the Crystellum flagship) to place 2 ships in "
                        + tile.getRepresentationForButtons(game, player) + ".",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + USE_FRACTAL_REBUILD + tile.getPosition(),
                                "Use The Fractal"),
                        Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline")));
    }

    public static void addRefractumDeployButton(List<Button> buttons, Player player) {
        int capturedFighters = player.getNomboxTile().getUnitHolders().values().stream()
                .mapToInt(holder -> holder.getUnitCount(UnitType.Fighter, player))
                .sum();
        if (!player.hasUnit("crystellum_mech") || capturedFighters < 2) return;
        buttons.add(
                Buttons.green(player.factionButtonChecker() + USE_REFRACTUM_DEPLOY, "Use Refractum", UnitEmojis.mech));
    }

    @ButtonHandler(USE_REFRACTUM_DEPLOY)
    public static void useRefractumDeploy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        Tile tile = combat == null ? null : game.getTileByPosition(combat.tilePosition());
        UnitHolder planet = tile == null || combat.unitHolderName() == null
                ? null
                : tile.getUnitHolders().get(combat.unitHolderName());
        if (planet == null || !player.hasUnit("crystellum_mech")) return;
        int capturedFighters = player.getNomboxTile().getUnitHolders().values().stream()
                .mapToInt(holder -> holder.getUnitCount(UnitType.Fighter, player))
                .sum();
        if (planet == null || capturedFighters < 2) return;

        RemoveUnitService.removeUnits(event, player.getNomboxTile(), game, player.getColor(), "2 fighter");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + planet.getName());
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " returned 2 captured fighters and placed 1 mech on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + " with Refractum's DEPLOY ability.");
    }

    @ButtonHandler(USE_FRACTAL_REBUILD)
    public static void useFractalRebuild(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(USE_FRACTAL_REBUILD.length()));
        int capturedFighters = player.getNomboxTile().getUnitHolders().values().stream()
                .mapToInt(holder -> holder.getUnitCount(UnitType.Fighter, player))
                .sum();
        if (tile == null || capturedFighters == 0 || !player.hasUnit("crystellum_flagship")) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (int amount = 1; amount <= Math.min(8, capturedFighters); amount++) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + FRACTAL_RETURN + tile.getPosition() + "|" + amount,
                    "Return " + amount + " Fighter" + (amount == 1 ? "" : "s"),
                    UnitEmojis.fighter));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose how many captured fighters to return for _The Fractal_.",
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
        if (tile == null || returned < 1 || returned > 8 || returned > capturedFighters) return;

        List<Button> buttons = new ArrayList<>();
        player.getUnitModels().stream()
                .map(UnitModel::getAsyncId)
                .filter(asyncId -> asyncId != null)
                .distinct()
                .map(asyncId -> player.getPriorityUnitByAsyncID(asyncId, tile.getSpaceUnitHolder()))
                .filter(unit -> unit != null && unit.getIsShip() && unit.getCost() > 0 && unit.getCost() <= returned)
                .sorted(Comparator.comparing(UnitModel::getName))
                .forEach(unit -> buttons.add(Buttons.green(
                        player.factionButtonChecker() + FRACTAL_SELECT_SHIP + tile.getPosition() + "|" + returned + "||"
                                + unit.getAsyncId(),
                        "Place " + unit.getName(),
                        unit.getUnitEmoji())));
        buttons.add(Buttons.green(
                player.factionButtonChecker() + FRACTAL_DONE + tile.getPosition() + "|" + returned + "|",
                "Done Placing Ships"));
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Cancel"));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose up to 2 ships to place with _The Fractal_.\nSelected: none.",
                buttons);
    }

    @ButtonHandler(FRACTAL_SELECT_SHIP)
    public static void chooseFractalShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(FRACTAL_SELECT_SHIP.length()).split("\\|", 4);
        if (payload.length != 4) return;
        Tile tile = game.getTileByPosition(payload[0]);
        int returned = NumberUtils.toInt(payload[1]);
        List<String> selectedShips =
                payload[2].isBlank() ? new ArrayList<>() : new ArrayList<>(List.of(payload[2].split(",")));
        List<UnitModel> selectedUnits = selectedShips.stream()
                .map(asyncId ->
                        tile == null ? null : player.getPriorityUnitByAsyncID(asyncId, tile.getSpaceUnitHolder()))
                .filter(unit -> unit != null && unit.getIsShip() && unit.getCost() > 0)
                .toList();
        float selectedCost = selectedUnits.stream().map(UnitModel::getCost).reduce(0F, Float::sum);
        int capturedFighters = player.getNomboxTile().getUnitHolders().values().stream()
                .mapToInt(holder -> holder.getUnitCount(UnitType.Fighter, player))
                .sum();
        UnitModel ship = tile == null ? null : player.getPriorityUnitByAsyncID(payload[3], tile.getSpaceUnitHolder());
        if (returned < 1
                || returned > 8
                || returned > capturedFighters
                || selectedShips.size() >= 2
                || selectedUnits.size() != selectedShips.size()
                || ship == null
                || !ship.getIsShip()
                || ship.getCost() <= 0
                || ship.getCost() > returned - selectedCost) {
            return;
        }
        selectedShips.add(ship.getAsyncId());
        selectedUnits = selectedShips.stream()
                .map(asyncId -> player.getPriorityUnitByAsyncID(asyncId, tile.getSpaceUnitHolder()))
                .filter(unit -> unit != null && unit.getIsShip() && unit.getCost() > 0)
                .toList();
        selectedCost = selectedUnits.stream().map(UnitModel::getCost).reduce(0F, Float::sum);
        String encodedSelection = String.join(",", selectedShips);
        List<Button> buttons = new ArrayList<>();
        if (selectedShips.size() < 2) {
            float remainingCost = returned - selectedCost;
            player.getUnitModels().stream()
                    .map(UnitModel::getAsyncId)
                    .filter(asyncId -> asyncId != null)
                    .distinct()
                    .map(asyncId -> player.getPriorityUnitByAsyncID(asyncId, tile.getSpaceUnitHolder()))
                    .filter(unit ->
                            unit != null && unit.getIsShip() && unit.getCost() > 0 && unit.getCost() <= remainingCost)
                    .sorted(Comparator.comparing(UnitModel::getName))
                    .forEach(unit -> buttons.add(Buttons.green(
                            player.factionButtonChecker() + FRACTAL_SELECT_SHIP + tile.getPosition() + "|" + returned
                                    + "|" + encodedSelection + "|" + unit.getAsyncId(),
                            "Place " + unit.getName(),
                            unit.getUnitEmoji())));
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker() + FRACTAL_DONE + tile.getPosition() + "|" + returned + "|"
                        + encodedSelection,
                "Done Placing Ships"));
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Cancel"));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose up to 2 ships to place with _The Fractal_.\nSelected: "
                        + selectedUnits.stream()
                                .map(unit -> unit.getUnitEmoji() + " " + unit.getName())
                                .collect(java.util.stream.Collectors.joining(" and "))
                        + ".",
                buttons);
    }

    @ButtonHandler(FRACTAL_DONE)
    public static void finishFractalRebuild(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(FRACTAL_DONE.length()).split("\\|", 3);
        if (payload.length != 3) return;
        Tile tile = game.getTileByPosition(payload[0]);
        int returned = NumberUtils.toInt(payload[1]);
        int capturedFighters = player.getNomboxTile().getUnitHolders().values().stream()
                .mapToInt(holder -> holder.getUnitCount(UnitType.Fighter, player))
                .sum();
        if (tile == null || returned < 1 || returned > 8 || returned > capturedFighters) return;
        List<String> selectedShips =
                payload[2].isBlank() ? new ArrayList<>() : new ArrayList<>(List.of(payload[2].split(",")));
        List<UnitModel> ships = selectedShips.stream()
                .map(asyncId -> player.getPriorityUnitByAsyncID(asyncId, tile.getSpaceUnitHolder()))
                .filter(unit -> unit != null && unit.getIsShip() && unit.getCost() > 0)
                .toList();
        float totalCost = ships.stream().map(UnitModel::getCost).reduce(0F, Float::sum);
        if (ships.size() != selectedShips.size() || ships.size() > 2 || totalCost > returned) return;

        RemoveUnitService.removeUnits(event, player.getNomboxTile(), game, player.getColor(), returned + " fighter");
        ships.forEach(ship -> AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + ship.getAsyncId()));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " returned " + returned + " captured fighter"
                        + (returned == 1 ? "" : "s")
                        + (ships.isEmpty()
                                ? " with _The Fractal_."
                                : " and placed "
                                        + ships.stream()
                                                .map(ship -> ship.getUnitEmoji() + " " + ship.getName())
                                                .collect(java.util.stream.Collectors.joining(" and "))
                                        + " in "
                                        + tile.getRepresentationForButtons(game, player) + " with _The Fractal_."));
    }

    public static Button addShardSwarmStartTurnButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + USE_SHARD_SWARM, "Use SHARD SWARM", UnitEmojis.fighter);
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
                        + " with SHARD SWARM.",
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
                        + " with SHARD SWARM.");
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
                + ", choose a system containing a planet you control for SHARD SWARM.";
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
