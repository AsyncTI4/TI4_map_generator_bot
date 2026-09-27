package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Thurviali;

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
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.turn.StartTurnService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class ThurvialiTechHandler {
    private static final String RESTRUCTURING = "ththurvialig";
    private static final String MUTUALISM = "ththurvialib";
    private static final String SELECT_RESTRUCTURING_STRUCTURE = "selectRestructuringStructure_";
    private static final String PLACE_RESTRUCTURING_STRUCTURE = "placeRestructuringStructure_";
    private static final String FINISH_RESTRUCTURING = "finishRestructuring";
    private static final String RESTRUCTURING_SELECTED = "restructuringSelected_";
    private static final String SELECT_MUTUALISM_PLAYER = "selectMutualismPlayer_";
    private static final String SELECT_MUTUALISM_STRUCTURE = "selectMutualismStructure_";
    private static final String PLACE_MUTUALISM_STRUCTURE = "placeMutualismStructure_";

    public static void resolveRestructuring(Game game, Player player) {
        if (game == null || player == null || !player.hasTech(RESTRUCTURING)) {
            return;
        }
        game.removeStoredValue(RESTRUCTURING_SELECTED + player.getFaction());
        sendRestructuringStructureButtons(game, player);
    }

    public static boolean canUseCoexistingStructure(Game game, Player player, UnitHolder holder, UnitKey unitKey) {
        if (game == null
                || player == null
                || holder == null
                || unitKey == null
                || !player.hasTech(MUTUALISM)
                || !(holder instanceof Planet)
                || !game.getPlanetsPlayerIsCoexistingOn(player).contains(holder.getName())) {
            return false;
        }
        Player owner = game.getPlayerByColorID(unitKey.colorID()).orElse(null);
        UnitModel unit = owner == null ? null : owner.getUnitFromUnitKey(unitKey);
        return owner != null && owner != player && unit != null && unit.getIsStructure();
    }

    public static boolean hasCoexistingSpaceCannonCoverage(Game game, Player player, Tile targetTile) {
        if (game == null || player == null || targetTile == null || !player.hasTech(MUTUALISM)) {
            return false;
        }
        for (String position :
                ti4.helpers.FoWHelper.getAdjacentTiles(game, targetTile.getPosition(), player, false, true)) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null || tile.isScar(game)) {
                continue;
            }
            boolean sameTile = targetTile.getPosition().equals(position);
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (var entry : holder.getUnits().entrySet()) {
                    if (entry.getValue() < 1) {
                        continue;
                    }
                    UnitKey unitKey = entry.getKey();
                    Player owner = game.getPlayerByColorID(unitKey.colorID()).orElse(null);
                    UnitModel unit = owner == null ? null : owner.getUnitFromUnitKey(unitKey);
                    if (canUseCoexistingStructure(game, player, holder, unitKey)
                            && unit != null
                            && unit.getSpaceCannonDieCount(player) > 0
                            && (sameTile
                                    || unit.getDeepSpaceCannon(player)
                                    || game.playerHasLeaderUnlockedOrAlliance(player, "mirvedacommander"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void resolveMutualism(Game game, Player player) {
        if (game == null || player == null || !player.hasTech(MUTUALISM)) {
            return;
        }
        List<Button> buttons = game.getRealPlayers().stream()
                .filter(target -> target != player)
                .filter(target -> target.getUnitsOwned().stream()
                        .map(Mapper::getUnit)
                        .anyMatch(unit -> unit != null
                                && unit.getIsStructure()
                                && (unit.getUnitType() != UnitType.Monument || game.isMonumentsMode())
                                && ButtonHelper.getNumberOfUnitsOnTheBoard(game, target, unit.getAsyncId())
                                        < target.getUnitCap(unit.getAsyncId())))
                .map(target -> Buttons.green(
                        player.factionButtonChecker() + SELECT_MUTUALISM_PLAYER + target.getFaction(),
                        "Select " + target.getColor(),
                        target.getFactionEmojiOrColor()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "No player has an available structure in their reinforcements.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a player for _Mutualism_.",
                buttons);
    }

    @ButtonHandler(SELECT_MUTUALISM_PLAYER)
    public static void selectMutualismPlayer(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_MUTUALISM_PLAYER.length()));
        if (target == null || target == player || !player.hasTech(MUTUALISM)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = target.getUnitsOwned().stream()
                .map(Mapper::getUnit)
                .filter(java.util.Objects::nonNull)
                .filter(UnitModel::getIsStructure)
                .filter(unit -> unit.getUnitType() != UnitType.Monument || game.isMonumentsMode())
                .filter(unit -> ButtonHelper.getNumberOfUnitsOnTheBoard(game, target, unit.getAsyncId())
                        < target.getUnitCap(unit.getAsyncId()))
                .map(unit -> Buttons.green(
                        target.factionButtonChecker() + SELECT_MUTUALISM_STRUCTURE + player.getFaction() + "|"
                                + unit.getAsyncId(),
                        "Place " + unit.getName(),
                        unit.getUnitEmoji()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        buttons.add(Buttons.red(target.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationNoPing() + ", " + player.getRepresentationNoPing()
                        + " selected you for _Mutualism_. You may place a structure into coexistence on one of their planets.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_MUTUALISM_STRUCTURE)
    public static void selectMutualismStructure(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(SELECT_MUTUALISM_STRUCTURE.length()).split("\\|", 2);
        Player owner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        UnitModel unit = payload.length == 2
                ? player.getUnitsByAsyncID(payload[1]).stream().findFirst().orElse(null)
                : null;
        if (owner == null
                || owner == player
                || unit == null
                || !owner.hasTech(MUTUALISM)
                || !unit.getIsStructure()
                || (unit.getUnitType() == UnitType.Monument && !game.isMonumentsMode())
                || ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, unit.getAsyncId())
                        >= player.getUnitCap(unit.getAsyncId())) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String planetName : owner.getPlanets()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet == null || !unit.canBePlacedOnPlanetTypes(planet.getPlanetTypes())) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE_MUTUALISM_STRUCTURE + owner.getFaction() + "|"
                            + unit.getAsyncId() + "|" + planet.getName(),
                    "Place on " + Helper.getPlanetRepresentation(planet.getName(), game)));
        }
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose one of " + owner.getRepresentationNoPing()
                        + "'s planets to place " + unit.getName() + " into coexistence.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_MUTUALISM_STRUCTURE)
    public static void placeMutualismStructure(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(PLACE_MUTUALISM_STRUCTURE.length()).split("\\|", 3);
        Player owner = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        UnitModel unit = payload.length == 3 && owner != null
                ? player.getUnitsByAsyncID(payload[1]).stream().findFirst().orElse(null)
                : null;
        Tile tile = payload.length == 3 ? game.getTileFromPlanet(payload[2]) : null;
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(payload[2]);
        if (owner == null
                || owner == player
                || unit == null
                || tile == null
                || planet == null
                || !owner.hasTech(MUTUALISM)
                || !owner.getPlanets().contains(payload[2])
                || !unit.getIsStructure()
                || (unit.getUnitType() == UnitType.Monument && !game.isMonumentsMode())
                || !unit.canBePlacedOnPlanetTypes(planet.getPlanetTypes())
                || ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, unit.getAsyncId())
                        >= player.getUnitCap(unit.getAsyncId())) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        UnitKey placedUnitKey = new UnitKey(unit.getUnitType(), player.getColorID());
        int existingUnits = planet.getUnitCount(placedUnitKey);
        String coexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            AddUnitService.addUnits(event, tile, game, player.getColor(), unit.getAsyncId() + " " + planet.getName());
        } finally {
            if (coexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", coexistenceFlag);
            }
        }
        if (planet.getUnitCount(placedUnitKey) <= existingUnits) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed " + unit.getName() + " into coexistence on "
                        + Helper.getPlanetRepresentation(planet.getName(), game)
                        + " with _Mutualism_ and gains 1 command token.",
                ButtonHelper.getGainCCButtons(player));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_RESTRUCTURING_STRUCTURE)
    public static void selectRestructuringStructure(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> structureButtons = getRestructuringStructureButtons(game, player);
        String message = getRestructuringMessage(game, player);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                player.getCorrectChannel(),
                structureButtons,
                List.of(Buttons.red(FINISH_RESTRUCTURING, "Done")),
                message,
                player.factionButtonChecker() + SELECT_RESTRUCTURING_STRUCTURE,
                buttonID)) {
            return;
        }
        String[] payload =
                buttonID.substring(SELECT_RESTRUCTURING_STRUCTURE.length()).split("\\|", 5);
        Tile source = payload.length == 5 ? game.getTileByPosition(payload[0]) : null;
        UnitHolder holder = source == null || payload.length != 5
                ? null
                : source.getUnitHolders().get(payload[1]);
        UnitKey unitKey = holder == null || payload.length != 5
                ? null
                : holder.getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.asyncID().equals(payload[2]))
                        .findFirst()
                        .orElse(null);
        UnitState state = payload.length != 5 ? null : getUnitState(payload[3]);
        UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
        if (source == null
                || holder == null
                || unitKey == null
                || state == null
                || unit == null
                || !unit.getIsStructure()
                || (unit.getUnitType() == UnitType.Monument && !game.isMonumentsMode())
                || holder.getUnitCountForState(unitKey, state) < 1
                || getRestructuringSelectionCount(game, player) >= 3
                || getRestructuringSelections(game, player).contains(String.join("|", payload))) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        game.setStoredValue(
                RESTRUCTURING_SELECTED + player.getFaction(),
                game.getStoredValue(RESTRUCTURING_SELECTED + player.getFaction()) + String.join("|", payload) + ";");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(PLACE_RESTRUCTURING_STRUCTURE)
    public static void placeRestructuringStructure(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(PLACE_RESTRUCTURING_STRUCTURE.length()).split("\\|", 6);
        Tile source = payload.length == 6 ? game.getTileByPosition(payload[0]) : null;
        UnitHolder sourceHolder = source == null || payload.length != 6
                ? null
                : source.getUnitHolders().get(payload[1]);
        UnitKey unitKey = sourceHolder == null || payload.length != 6
                ? null
                : sourceHolder.getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.asyncID().equals(payload[2]))
                        .findFirst()
                        .orElse(null);
        UnitState state = payload.length != 6 ? null : getUnitState(payload[3]);
        Tile destination = payload.length != 6 ? null : game.getTileFromPlanet(payload[5]);
        Planet destinationPlanet = destination == null ? null : destination.getUnitHolderFromPlanet(payload[5]);
        UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), sourceHolder);
        if (source == null
                || sourceHolder == null
                || unitKey == null
                || state == null
                || destination == null
                || destinationPlanet == null
                || !player.getPlanets().contains(payload[5])
                || unit == null
                || !unit.getIsStructure()
                || (unit.getUnitType() == UnitType.Monument && !game.isMonumentsMode())
                || !unit.canBePlacedOnPlanetTypes(destinationPlanet.getPlanetTypes())
                || sourceHolder.getUnitCountForState(unitKey, state) < 1
                || !game.getStoredValue(RESTRUCTURING_SELECTED + player.getFaction())
                        .startsWith(
                                String.join("|", payload[0], payload[1], payload[2], payload[3], payload[4]) + ";")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Integer> removedStates = sourceHolder.removeUnit(unitKey, 1, state);
        if (removedStates.stream().mapToInt(Integer::intValue).sum() < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        destinationPlanet.addUnitsWithStates(unitKey, removedStates);
        String currentSelection = String.join("|", payload[0], payload[1], payload[2], payload[3], payload[4]) + ";";
        game.setStoredValue(
                RESTRUCTURING_SELECTED + player.getFaction(),
                game.getStoredValue(RESTRUCTURING_SELECTED + player.getFaction())
                        .substring(currentSelection.length()));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " moved " + unit.getName() + " to "
                        + Helper.getPlanetRepresentation(destinationPlanet.getName(), game) + " with _Restructuring_.");
        ButtonHelper.deleteMessage(event);
        if (getRestructuringSelections(game, player).isEmpty()) {
            finishRestructuring(game, player, event);
        } else {
            sendNextRestructuringPlacementButtons(game, player);
        }
    }

    @ButtonHandler(FINISH_RESTRUCTURING)
    public static void finishRestructuring(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
        finishRestructuring(game, player, event);
    }

    private static void finishRestructuring(Game game, Player player, ButtonInteractionEvent event) {
        if (getRestructuringSelections(game, player).isEmpty()) {
            game.removeStoredValue(RESTRUCTURING_SELECTED + player.getFaction());
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    "Please choose whether to end your turn or take another action.",
                    StartTurnService.getStartOfTurnButtons(player, game, true, event));
            return;
        }
        sendNextRestructuringPlacementButtons(game, player);
    }

    private static void sendRestructuringStructureButtons(Game game, Player player) {
        List<Button> buttons = getRestructuringStructureButtons(game, player);
        if (buttons.isEmpty()) {
            game.removeStoredValue(RESTRUCTURING_SELECTED + player.getFaction());
            return;
        }
        String message = getRestructuringMessage(game, player);
        List<Button> displayedButtons = buttons.size() <= 24
                ? new ArrayList<>(buttons)
                : NewStuffHelper.buttonPagination(
                        buttons,
                        List.of(Buttons.red(FINISH_RESTRUCTURING, "Done")),
                        player.factionButtonChecker() + SELECT_RESTRUCTURING_STRUCTURE,
                        25,
                        0,
                        false);
        if (buttons.size() <= 24) {
            displayedButtons.add(Buttons.red(FINISH_RESTRUCTURING, "Done"));
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, displayedButtons);
    }

    private static List<Button> getRestructuringStructureButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (UnitKey unitKey : holder.getUnitKeysForPlayer(player)) {
                    UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
                    if (unit == null
                            || !unit.getIsStructure()
                            || (unit.getUnitType() == UnitType.Monument && !game.isMonumentsMode())) {
                        continue;
                    }
                    for (UnitState state : holder.getNonZeroUnitStates(unitKey)) {
                        int count = holder.getUnitCountForState(unitKey, state);
                        for (int index = 1; index <= count; index++) {
                            String selection = tile.getPosition() + "|" + holder.getName() + "|" + unitKey.asyncID()
                                    + "|" + state.name() + "|" + index;
                            if (getRestructuringSelections(game, player).contains(selection)) {
                                continue;
                            }
                            buttons.add(Buttons.green(
                                    player.factionButtonChecker() + SELECT_RESTRUCTURING_STRUCTURE + selection,
                                    "Remove " + unit.getName() + " from "
                                            + (holder instanceof Planet planet
                                                    ? Helper.getPlanetRepresentation(planet.getName(), game)
                                                    : tile.getRepresentationForButtons(game, player)),
                                    unitKey.unitEmoji()));
                        }
                    }
                }
            }
        }
        return buttons;
    }

    private static String getRestructuringMessage(Game game, Player player) {
        return player.getRepresentationNoPing() + ", choose up to 3 structures to remove with _Restructuring_ ("
                + getRestructuringSelectionCount(game, player) + "/3 selected), then press **Done**.";
    }

    private static List<String> getRestructuringSelections(Game game, Player player) {
        return java.util.Arrays.stream(game.getStoredValue(RESTRUCTURING_SELECTED + player.getFaction())
                        .split(";"))
                .filter(selection -> !selection.isBlank())
                .toList();
    }

    private static int getRestructuringSelectionCount(Game game, Player player) {
        return getRestructuringSelections(game, player).size();
    }

    private static void sendNextRestructuringPlacementButtons(Game game, Player player) {
        List<String> selections = getRestructuringSelections(game, player);
        if (selections.isEmpty()) {
            game.removeStoredValue(RESTRUCTURING_SELECTED + player.getFaction());
            return;
        }
        String[] selection = selections.getFirst().split("\\|", 5);
        Tile source = selection.length == 5 ? game.getTileByPosition(selection[0]) : null;
        UnitHolder holder = source == null ? null : source.getUnitHolders().get(selection[1]);
        UnitKey unitKey = holder == null
                ? null
                : holder.getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.asyncID().equals(selection[2]))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
        if (source == null || holder == null || unitKey == null || unit == null) {
            game.setStoredValue(
                    RESTRUCTURING_SELECTED + player.getFaction(),
                    game.getStoredValue(RESTRUCTURING_SELECTED + player.getFaction())
                            .replaceFirst(java.util.regex.Pattern.quote(selections.getFirst() + ";"), ""));
            sendNextRestructuringPlacementButtons(game, player);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Tile destination = game.getTileFromPlanet(planetName);
            Planet planet = destination == null ? null : destination.getUnitHolderFromPlanet(planetName);
            if (destination == null || planet == null || !unit.canBePlacedOnPlanetTypes(planet.getPlanetTypes())) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE_RESTRUCTURING_STRUCTURE + selections.getFirst() + "|"
                            + planetName,
                    "Place " + unit.getName() + " on " + Helper.getPlanetRepresentation(planetName, game),
                    unit.getUnitEmoji()));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " has no eligible planet on which to place " + unit.getName()
                            + " with _Restructuring_.");
            game.setStoredValue(
                    RESTRUCTURING_SELECTED + player.getFaction(),
                    game.getStoredValue(RESTRUCTURING_SELECTED + player.getFaction())
                            .replaceFirst(java.util.regex.Pattern.quote(selections.getFirst() + ";"), ""));
            sendNextRestructuringPlacementButtons(game, player);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose where to place " + unit.getName()
                        + " with _Restructuring_.",
                buttons);
    }

    private static UnitState getUnitState(String state) {
        try {
            return UnitState.valueOf(state);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
