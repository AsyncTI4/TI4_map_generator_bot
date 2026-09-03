package ti4.discord.interactions.buttons.handlers.actioncards.monuments;

import java.util.ArrayList;
import java.util.Comparator;
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
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParseUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class MonumentsActionCardHandler {
    private static final String RESOLVE = "resolveMonumentsActionCard_";
    private static final String FESTIVAL_PLANET = "monumentsFestivalPlanet_";
    private static final String FESTIVAL_PLACE = "monumentsFestivalPlace_";
    private static final String REBEL_BOMBING = "monumentsRebelBombing_";
    private static final String RENOVATION_STRUCTURE = "monumentsRenovationStructure_";
    private static final String RENOVATION_PLACE = "monumentsRenovationPlace_";

    @ButtonHandler(RESOLVE)
    public static void resolveMonumentsActionCard(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.isMonumentsMode()) {
            return;
        }
        String automationId = buttonID.replace(RESOLVE, "");
        switch (automationId) {
            case "monuments_festival" -> {
                List<Button> buttons = new ArrayList<>();
                for (String planetName : player.getPlanets()) {
                    Tile tile = game.getTileFromPlanet(planetName);
                    Planet planet = game.getUnitHolderFromPlanet(planetName);
                    if (tile == null || planet == null || tile.isHomeSystem(game)) {
                        continue;
                    }
                    boolean hasStructure = planet.getUnitKeysForPlayer(player).stream()
                            .map(player::getUnitFromUnitKey)
                            .anyMatch(unit -> unit != null && unit.getIsStructure());
                    if (hasStructure) {
                        buttons.add(Buttons.green(
                                player.factionButtonChecker() + FESTIVAL_PLANET + planetName,
                                "Ready " + Helper.getPlanetRepresentation(planetName, game)));
                    }
                }
                if (buttons.isEmpty()) {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            player.getRepresentationNoPing() + " has no eligible planet for _Festival_.");
                    ButtonHelper.deleteMessage(event);
                    return;
                }
                MessageHelper.editMessageWithButtons(
                        event,
                        player.getRepresentationNoPing() + ", choose a non-home planet to ready for _Festival_.",
                        buttons);
            }
            case "monuments_rebel_bombing" -> {
                List<Button> buttons = new ArrayList<>();
                for (Player neighbor : player.getNeighbouringPlayers(true)) {
                    for (Tile tile : game.getTileMap().values()) {
                        if (tile.isHomeSystem(game)) {
                            continue;
                        }
                        for (UnitHolder holder : tile.getUnitHolders().values()) {
                            for (UnitKey key : holder.getUnitKeysForPlayer(neighbor)) {
                                UnitModel unit = neighbor.getUnitFromUnitKey(key);
                                if (unit == null || !unit.getIsStructure()) {
                                    continue;
                                }
                                for (UnitState state : UnitState.values()) {
                                    if (holder.getUnitCountForState(key, state) < 1) {
                                        continue;
                                    }
                                    String location = holder instanceof Planet
                                            ? " on " + Helper.getPlanetRepresentation(holder.getName(), game)
                                            : " in " + tile.getRepresentationForButtons(game, player);
                                    buttons.add(Buttons.red(
                                            player.factionButtonChecker() + REBEL_BOMBING + neighbor.getFaction()
                                                    + "|" + tile.getPosition() + "|" + holder.getName() + "|"
                                                    + key.unitType().getValue() + "|" + state.name(),
                                            "Destroy " + key.humanReadableName() + location,
                                            key.unitEmoji()));
                                }
                            }
                        }
                    }
                }
                if (buttons.isEmpty()) {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            player.getRepresentationNoPing()
                                    + " has no neighboring structure to target with _Rebel Bombing_.");
                    ButtonHelper.deleteMessage(event);
                    return;
                }
                MessageHelper.editMessageWithButtons(
                        event,
                        player.getRepresentationNoPing()
                                + ", spend 2 trade goods to choose a neighboring structure to destroy with _Rebel Bombing_.",
                        buttons);
            }
            case "monuments_renovation" -> {
                List<Button> buttons = new ArrayList<>();
                for (Tile tile : game.getTileMap().values()) {
                    for (UnitHolder holder : tile.getUnitHolders().values()) {
                        for (UnitKey key : holder.getUnitKeysForPlayer(player)) {
                            UnitModel unit = player.getUnitFromUnitKey(key);
                            if (unit == null || !unit.getIsStructure()) {
                                continue;
                            }
                            for (UnitState state : UnitState.values()) {
                                if (holder.getUnitCountForState(key, state) < 1) {
                                    continue;
                                }
                                String location = holder instanceof Planet
                                        ? " on " + Helper.getPlanetRepresentation(holder.getName(), game)
                                        : " in " + tile.getRepresentationForButtons(game, player);
                                buttons.add(Buttons.green(
                                        player.factionButtonChecker() + RENOVATION_STRUCTURE + tile.getPosition() + "|"
                                                + holder.getName() + "|"
                                                + key.unitType().getValue() + "|" + state.name(),
                                        "Replace " + key.humanReadableName() + location,
                                        key.unitEmoji()));
                            }
                        }
                    }
                }
                if (buttons.isEmpty()) {
                    MessageHelper.sendMessageToChannel(
                            event.getMessageChannel(),
                            player.getRepresentationNoPing() + " has no structure to replace with _Renovation_.");
                    ButtonHelper.deleteMessage(event);
                    return;
                }
                MessageHelper.editMessageWithButtons(
                        event,
                        player.getRepresentationNoPing() + ", choose the structure to replace with _Renovation_.",
                        buttons);
            }
            default -> MessageHelper.sendEphemeralMessageToEventChannel(event, "Unknown Monuments action card.");
        }
    }

    @ButtonHandler(FESTIVAL_PLANET)
    public static void resolveFestivalPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.isMonumentsMode()) {
            return;
        }
        String planetName = buttonID.replace(FESTIVAL_PLANET, "");
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        boolean hasStructure = planet != null
                && planet.getUnitKeysForPlayer(player).stream()
                        .map(player::getUnitFromUnitKey)
                        .anyMatch(unit -> unit != null && unit.getIsStructure());
        if (tile == null
                || planet == null
                || tile.isHomeSystem(game)
                || !player.getPlanets().contains(planetName)
                || !hasStructure) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That planet is no longer eligible for _Festival_.");
            return;
        }

        player.refreshPlanet(planetName);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " readied " + Helper.getPlanetRepresentation(planetName, game)
                        + " with _Festival_.");

        if (planet.getUnitCount(UnitType.Monument, player) < 1) {
            return;
        }

        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + FESTIVAL_PLACE + "infantry|" + planetName,
                        "Place 4 Infantry",
                        UnitEmojis.infantry),
                Buttons.green(
                        player.factionButtonChecker() + FESTIVAL_PLACE + "mech|" + planetName,
                        "Place 1 Mech",
                        UnitEmojis.mech));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", your Monument is on that planet. Choose the additional units to place for _Festival_.",
                buttons);
    }

    @ButtonHandler(FESTIVAL_PLACE)
    public static void resolveFestivalPlacement(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.isMonumentsMode()) {
            return;
        }
        String[] payload = buttonID.replace(FESTIVAL_PLACE, "").split("\\|", 2);
        if (payload.length != 2) {
            return;
        }
        String unit = payload[0];
        String planetName = payload[1];
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (tile == null
                || planet == null
                || !player.getPlanets().contains(planetName)
                || planet.getUnitCount(UnitType.Monument, player) < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That Monument is no longer on the selected planet.");
            return;
        }

        if ("infantry".equals(unit)) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "4 infantry " + planetName);
        } else if ("mech".equals(unit)) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + planetName);
        } else {
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed " + ("infantry".equals(unit) ? "4 infantry" : "1 mech")
                        + " on " + Helper.getPlanetRepresentation(planetName, game) + " with _Festival_.");
    }

    @ButtonHandler(REBEL_BOMBING)
    public static void resolveRebelBombing(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.isMonumentsMode()) {
            return;
        }
        String[] payload = buttonID.replace(REBEL_BOMBING, "").split("\\|", 5);
        if (payload.length != 5) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(payload[0]);
        Tile tile = game.getTileByPosition(payload[1]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(payload[2]);
        UnitType type = Units.findUnitType(payload[3]);
        UnitState state = Units.findUnitState(payload[4]);
        UnitKey key = target == null || type == null ? null : new UnitKey(type, target.getColorID());
        UnitModel unit = key == null ? null : target.getUnitFromUnitKey(key);
        if (target == null
                || tile == null
                || holder == null
                || type == null
                || state == null
                || key == null
                || unit == null
                || tile.isHomeSystem(game)
                || !player.getNeighbouringPlayers(true).contains(target)
                || !unit.getIsStructure()
                || holder.getUnitCountForState(key, state) < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That structure is no longer eligible for _Rebel Bombing_.");
            return;
        }
        if (player.getTg() < 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You need 2 trade goods to resolve _Rebel Bombing_.");
            return;
        }

        player.setTg(player.getTg() - 2);
        DestroyUnitService.destroyUnit(
                event, tile, game, ParseUnitService.simpleParsedUnit(target, type, holder, 1), false, state);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " spent 2 trade goods and destroyed "
                        + target.getRepresentationNoPing() + "'s " + key.humanReadableName()
                        + " with _Rebel Bombing_.");
    }

    @ButtonHandler(RENOVATION_STRUCTURE)
    public static void resolveRenovationStructure(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.isMonumentsMode()) {
            return;
        }
        String[] payload = buttonID.replace(RENOVATION_STRUCTURE, "").split("\\|", 4);
        if (payload.length != 4) {
            return;
        }
        Tile tile = game.getTileByPosition(payload[0]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(payload[1]);
        UnitType type = Units.findUnitType(payload[2]);
        UnitState state = Units.findUnitState(payload[3]);
        UnitKey key = type == null ? null : new UnitKey(type, player.getColorID());
        UnitModel oldUnit = key == null ? null : player.getUnitFromUnitKey(key);
        if (tile == null
                || holder == null
                || type == null
                || state == null
                || key == null
                || oldUnit == null
                || !oldUnit.getIsStructure()
                || holder.getUnitCountForState(key, state) < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That structure is no longer eligible for _Renovation_.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        List<UnitModel> replacementStructures = new ArrayList<>();
        for (UnitModel unit : player.getUnitModels().stream()
                .filter(UnitModel::getIsStructure)
                .sorted(Comparator.comparing(UnitModel::getName))
                .toList()) {
            boolean canBePlacedHere = holder instanceof Planet ? unit.getIsPlanetOnly() : unit.getIsSpaceOnly();
            if (canBePlacedHere
                    && replacementStructures.stream()
                            .noneMatch(existing -> existing.getBaseType().equalsIgnoreCase(unit.getBaseType()))) {
                replacementStructures.add(unit);
            }
        }
        for (UnitModel unit : replacementStructures) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + RENOVATION_PLACE + tile.getPosition() + "|" + holder.getName() + "|"
                            + type.getValue() + "|" + state.name() + "|" + unit.getBaseType(),
                    "Place " + unit.getName(),
                    unit.getUnitEmoji()));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You have no structure available to place.");
            return;
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose the replacement structure for _Renovation_.",
                buttons);
    }

    @ButtonHandler(RENOVATION_PLACE)
    public static void resolveRenovationPlacement(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.isMonumentsMode()) {
            return;
        }
        String[] payload = buttonID.replace(RENOVATION_PLACE, "").split("\\|", 5);
        if (payload.length != 5) {
            return;
        }
        Tile tile = game.getTileByPosition(payload[0]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(payload[1]);
        UnitType oldType = Units.findUnitType(payload[2]);
        UnitState state = Units.findUnitState(payload[3]);
        UnitKey oldKey = oldType == null ? null : new UnitKey(oldType, player.getColorID());
        UnitModel replacement = player.getUnitByBaseType(payload[4]);
        UnitModel oldUnit = oldKey == null ? null : player.getUnitFromUnitKey(oldKey);
        boolean correctPlacement = holder instanceof Planet
                ? replacement != null && replacement.getIsPlanetOnly()
                : replacement != null && replacement.getIsSpaceOnly();
        if (tile == null
                || holder == null
                || oldType == null
                || state == null
                || oldKey == null
                || oldUnit == null
                || replacement == null
                || !oldUnit.getIsStructure()
                || !replacement.getIsStructure()
                || !correctPlacement
                || holder.getUnitCountForState(oldKey, state) < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That replacement is no longer eligible for _Renovation_.");
            return;
        }

        RemoveUnitService.removeUnit(event, tile, game, player, holder, oldType, 1, state);
        AddUnitService.addUnits(
                event, tile, game, player.getColor(), "1 " + replacement.getBaseType() + " " + holder.getName());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " replaced " + oldKey.humanReadableName() + " with "
                        + replacement.getName() + " using _Renovation_.");
    }
}
