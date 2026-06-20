package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
class DefectorsAcd2ButtonHandler {

    private static final int DEFECTORS_MAX_SHIPS = 2;
    private static final String DEFECTORS_TARGET_KEY_PREFIX = "defectorsTarget_";
    private static final String DEFECTORS_SOURCE_KEY_PREFIX = "defectorsSource_";
    private static final String DEFECTORS_SELECTED_KEY_PREFIX = "defectorsSelected_";
    private static final String DEFECTORS_PENDING_KEY_PREFIX = "defectorsPending_";

    @ButtonHandler("resolveDefectors")
    public static void resolveDefectors(Player player, Game game, ButtonInteractionEvent event) {
        clearDefectorsState(game, player);
        List<Player> eligibleTargets = game.getRealPlayers().stream()
                .filter(target -> !player.is(target))
                .filter(target ->
                        !getDefectorsEligibleShipsBySystem(game, player, target).isEmpty())
                .toList();

        if (eligibleTargets.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", there are no players with eligible produced ships for _Defectors_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose which player to target with _Defectors_.",
                getDefectorsTargetButtons(player, game, eligibleTargets));
    }

    @ButtonHandler("defectorsTarget_")
    public static void resolveDefectorsTarget(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String faction = buttonID.replace("defectorsTarget_", "");
        Player target = game.getPlayerFromColorOrFaction(faction);
        if (target == null || player.is(target)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not resolve that _Defectors_ target.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Map<String, Map<String, Integer>> eligibleShipsBySystem =
                getDefectorsEligibleShipsBySystem(game, player, target);
        if (eligibleShipsBySystem.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", that player no longer has eligible produced ships for _Defectors_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(defectorsTargetKey(player), target.getFaction());
        game.removeStoredValue(defectorsSourceKey(player));
        game.removeStoredValue(defectorsSelectedKey(player));
        game.removeStoredValue(defectorsPendingKey(player));

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the system containing "
                        + target.getRepresentationUnfoggedNoPing() + "'s eligible produced ships.",
                getDefectorsSystemButtons(game, player, target, eligibleShipsBySystem));
    }

    @ButtonHandler("defectorsSystem_")
    public static void resolveDefectorsSystem(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.replace("defectorsSystem_", "");
        Player target = getDefectorsStoredTarget(game, player);
        if (target == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find the target player for _Defectors_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (getDefectorsRemainingShipsForSystem(game, player, target, pos, List.of())
                .isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", that system no longer has eligible ships for _Defectors_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(defectorsSourceKey(player), pos);
        game.removeStoredValue(defectorsSelectedKey(player));
        game.removeStoredValue(defectorsPendingKey(player));

        ButtonHelper.deleteMessage(event);
        sendDefectorsShipSelection(game, player, target, pos);
    }

    @ButtonHandler("defectorsPickShip_")
    public static void resolveDefectorsPickShip(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player target = getDefectorsStoredTarget(game, player);
        String sourcePos = game.getStoredValue(defectorsSourceKey(player));
        if (target == null || sourcePos.isBlank()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not continue resolving _Defectors_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String unitAlias = buttonID.replace("defectorsPickShip_", "");
        List<String> selectedShips = getStoredStringList(game, defectorsSelectedKey(player));
        Map<String, Integer> remainingShips =
                getDefectorsRemainingShipsForSystem(game, player, target, sourcePos, selectedShips);
        if (remainingShips.getOrDefault(unitAlias, 0) <= 0) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", that ship is no longer available to take with _Defectors_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        selectedShips.add(unitAlias);
        setStoredStringList(game, defectorsSelectedKey(player), selectedShips);
        ButtonHelper.deleteMessage(event);

        if (selectedShips.size() >= DEFECTORS_MAX_SHIPS) {
            startDefectorsPlacement(game, player);
            return;
        }

        sendDefectorsShipSelection(game, player, target, sourcePos);
    }

    @ButtonHandler("defectorsShipsDone")
    public static void resolveDefectorsShipsDone(Player player, Game game, ButtonInteractionEvent event) {
        if (getStoredStringList(game, defectorsSelectedKey(player)).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", choose at least 1 ship before finishing _Defectors_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        startDefectorsPlacement(game, player);
    }

    @ButtonHandler("defectorsPlaceRing_")
    public static void resolveDefectorsPlaceRing(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        List<String> pendingShips = getStoredStringList(game, defectorsPendingKey(player));
        if (pendingShips.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "There are no ships left to place for _Defectors_.");
            ButtonHelper.deleteMessage(event);
            clearDefectorsState(game, player);
            return;
        }

        String ringSelector = buttonID.replace("defectorsPlaceRing_", "");
        UnitModel shipToPlace = getDefectorsShipModelForPlacement(game, player, pendingShips.getFirst());
        List<Button> buttons = getDefectorsPlacementTileButtons(game, player, ringSelector);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", there are no eligible systems in that ring for _Defectors_.");
            ButtonHelper.deleteMessage(event);
            sendDefectorsPlacementRingButtons(game, player);
            return;
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose where to place "
                        + getDefectorsShipName(shipToPlace, pendingShips.getFirst()) + " with _Defectors_.",
                buttons);
    }

    @ButtonHandler("defectorsPlaceRingBack")
    public static void resolveDefectorsPlaceRingBack(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        sendDefectorsPlacementRingButtons(game, player);
    }

    @ButtonHandler("defectorsPlaceTile_")
    public static void resolveDefectorsPlaceTile(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        List<String> pendingShips = getStoredStringList(game, defectorsPendingKey(player));
        Player target = getDefectorsStoredTarget(game, player);
        String sourcePos = game.getStoredValue(defectorsSourceKey(player));
        if (pendingShips.isEmpty() || target == null || sourcePos.isBlank()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not complete _Defectors_.");
            ButtonHelper.deleteMessage(event);
            clearDefectorsState(game, player);
            return;
        }

        String destinationPos = buttonID.replace("defectorsPlaceTile_", "");
        Tile destination = game.getTileByPosition(destinationPos);
        if (!isDefectorsPlacementTileEligible(player, game, destination)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", that destination is no longer eligible for _Defectors_.");
            ButtonHelper.deleteMessage(event);
            sendDefectorsPlacementRingButtons(game, player);
            return;
        }

        String selectedShipAlias = pendingShips.removeFirst();
        UnitModel targetShip = getDefectorsShipModelForPlacement(game, target, selectedShipAlias);
        UnitModel matchingShip = getMatchingDefectorsUnit(player, targetShip);
        if (targetShip == null || matchingShip == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", could not determine the matching ship for _Defectors_.");
            ButtonHelper.deleteMessage(event);
            setStoredStringList(game, defectorsPendingKey(player), pendingShips);
            if (pendingShips.isEmpty()) {
                clearDefectorsState(game, player);
            } else {
                sendDefectorsPlacementRingButtons(game, player);
            }
            return;
        }

        Tile sourceTile = game.getTileByPosition(sourcePos);
        List<RemovedUnit> removedShips = sourceTile == null
                ? List.of()
                : RemoveUnitService.removeUnits(
                        event, sourceTile, game, target.getColor(), "1 " + selectedShipAlias, false);
        if (removedShips.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " could not remove "
                            + getDefectorsShipName(targetShip, selectedShipAlias) + " from "
                            + (sourceTile == null
                                    ? "that system"
                                    : sourceTile.getRepresentationForButtons(game, player))
                            + " for _Defectors_.");
            ButtonHelper.deleteMessage(event);
            setStoredStringList(game, defectorsPendingKey(player), pendingShips);
            if (pendingShips.isEmpty()) {
                clearDefectorsState(game, player);
            } else {
                sendDefectorsPlacementRingButtons(game, player);
            }
            return;
        }

        AddUnitService.addUnits(event, destination, game, player.getColor(), "1 " + matchingShip.getAsyncId());
        setStoredStringList(game, defectorsPendingKey(player), pendingShips);
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " placed "
                        + getDefectorsShipName(matchingShip, matchingShip.getAsyncId()) + " in "
                        + destination.getRepresentationForButtons(game, player) + " via _Defectors_.");

        if (pendingShips.isEmpty()) {
            clearDefectorsState(game, player);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " finished resolving _Defectors_.");
            return;
        }

        sendDefectorsPlacementRingButtons(game, player);
    }

    private static List<Button> getDefectorsTargetButtons(Player player, Game game, List<Player> targets) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : targets) {
            String label = game.isFowMode() ? target.getColor() : StringUtils.capitalize(target.getFaction());
            Button button =
                    Buttons.blue(player.factionButtonChecker() + "defectorsTarget_" + target.getFaction(), label);
            if (!game.isFowMode()) {
                button = button.withEmoji(
                        Emoji.fromFormatted(target.getFactionEmoji()));
            }
            buttons.add(button);
        }
        return buttons;
    }

    private static List<Button> getDefectorsSystemButtons(
            Game game, Player resolvingPlayer, Player target, Map<String, Map<String, Integer>> eligibleShipsBySystem) {
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> entry : eligibleShipsBySystem.entrySet()) {
            Tile tile = game.getTileByPosition(entry.getKey());
            if (tile == null) {
                continue;
            }
            int shipCount = entry.getValue().values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
            buttons.add(Buttons.gray(
                    resolvingPlayer.factionButtonChecker() + "defectorsSystem_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, resolvingPlayer) + " (" + shipCount + " eligible)"));
        }
        return buttons;
    }

    private static void sendDefectorsShipSelection(Game game, Player resolvingPlayer, Player target, String sourcePos) {
        List<String> selectedShips = getStoredStringList(game, defectorsSelectedKey(resolvingPlayer));
        Map<String, Integer> remainingShips =
                getDefectorsRemainingShipsForSystem(game, resolvingPlayer, target, sourcePos, selectedShips);
        if (remainingShips.isEmpty()) {
            if (selectedShips.isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        resolvingPlayer.getCorrectChannel(),
                        resolvingPlayer.getRepresentationUnfogged()
                                + ", there are no eligible ships left in that system for _Defectors_.");
                clearDefectorsState(game, resolvingPlayer);
            } else {
                startDefectorsPlacement(game, resolvingPlayer);
            }
            return;
        }

        Tile sourceTile = game.getTileByPosition(sourcePos);
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : remainingShips.entrySet()) {
            UnitModel targetShip = getDefectorsShipModelForPlacement(game, target, entry.getKey());
            if (targetShip == null) {
                continue;
            }
            String label = "Take " + getDefectorsShipName(targetShip, entry.getKey());
            if (entry.getValue() > 1) {
                label += " (" + entry.getValue() + ")";
            }
            buttons.add(Buttons.gray(
                    resolvingPlayer.factionButtonChecker() + "defectorsPickShip_" + entry.getKey(),
                    label,
                    targetShip.getUnitEmoji()));
        }
        if (!selectedShips.isEmpty()) {
            buttons.add(Buttons.blue(resolvingPlayer.factionButtonChecker() + "defectorsShipsDone", "Done"));
        }

        StringBuilder message = new StringBuilder(resolvingPlayer.getRepresentationUnfogged())
                .append(", choose up to ")
                .append(DEFECTORS_MAX_SHIPS)
                .append(" ships without capacity from ")
                .append(target.getRepresentationUnfoggedNoPing())
                .append(" in ")
                .append(sourceTile == null ? sourcePos : sourceTile.getRepresentationForButtons(game, resolvingPlayer))
                .append(".");
        if (!selectedShips.isEmpty()) {
            message.append("\nSelected so far: ").append(selectedShips.size()).append(".");
        }

        MessageHelper.sendMessageToChannelWithButtons(resolvingPlayer.getCorrectChannel(), message.toString(), buttons);
    }

    private static void startDefectorsPlacement(Game game, Player player) {
        List<String> selectedShips = getStoredStringList(game, defectorsSelectedKey(player));
        if (selectedShips.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", there are no ships queued to place for _Defectors_.");
            clearDefectorsState(game, player);
            return;
        }

        setStoredStringList(game, defectorsPendingKey(player), selectedShips);
        game.removeStoredValue(defectorsSelectedKey(player));
        sendDefectorsPlacementRingButtons(game, player);
    }

    private static void sendDefectorsPlacementRingButtons(Game game, Player player) {
        List<String> pendingShips = getStoredStringList(game, defectorsPendingKey(player));
        if (pendingShips.isEmpty()) {
            clearDefectorsState(game, player);
            return;
        }

        UnitModel shipToPlace = getDefectorsShipModelForPlacement(game, player, pendingShips.getFirst());
        List<Button> buttons = getDefectorsPlacementRingButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", there are no eligible systems to place "
                            + getDefectorsShipName(shipToPlace, pendingShips.getFirst()) + " with _Defectors_.");
            clearDefectorsState(game, player);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose a ring for "
                        + getDefectorsShipName(shipToPlace, pendingShips.getFirst()) + ".",
                buttons);
    }

    private static List<Button> getDefectorsPlacementRingButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        Set<String> eligiblePositions = getDefectorsEligibleDestinationPositions(game, player);

        if (eligiblePositions.contains("000")) {
            Tile centerTile = game.getTileByPosition("000");
            if (centerTile != null) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "defectorsPlaceRing_0",
                        centerTile.getRepresentationForButtons(game, player),
                        centerTile.getTileEmoji(player)));
            }
        }

        for (int ring = 1; ring <= game.getRingCount(); ring++) {
            List<String> ringPositions = PositionMapper.getPositionsInRing(Integer.toString(ring), game);
            if (ringPositions.stream().anyMatch(eligiblePositions::contains)) {
                buttons.add(
                        Buttons.green(player.factionButtonChecker() + "defectorsPlaceRing_" + ring, "Ring #" + ring));
            }
        }

        if (getDefectorsCornerPositions(game).stream().anyMatch(eligiblePositions::contains)) {
            buttons.add(Buttons.green(player.factionButtonChecker() + "defectorsPlaceRing_corners", "Corners"));
        }

        return buttons;
    }

    private static List<Button> getDefectorsPlacementTileButtons(Game game, Player player, String ringSelector) {
        List<String> eligiblePositionsInRing = getDefectorsPositionsForRing(game, player, ringSelector);
        List<Button> buttons = new ArrayList<>();

        if (!ringSelector.contains("_") && eligiblePositionsInRing.size() > 20) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "defectorsPlaceRing_" + ringSelector + "_left", "Left Half"));
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "defectorsPlaceRing_" + ringSelector + "_right", "Right Half"));
        } else {
            for (String pos : eligiblePositionsInRing) {
                Tile tile = game.getTileByPosition(pos);
                if (tile != null) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + "defectorsPlaceTile_" + pos,
                            tile.getRepresentationForButtons(game, player),
                            tile.getTileEmoji(player)));
                }
            }
        }

        buttons.add(Buttons.red(player.factionButtonChecker() + "defectorsPlaceRingBack", "Choose Different Ring"));
        return buttons;
    }

    private static Set<String> getDefectorsEligibleDestinationPositions(Game game, Player player) {
        Set<String> positions = new HashSet<>();
        for (Tile tile : game.getTileMap().values()) {
            if (isDefectorsPlacementTileEligible(player, game, tile)) {
                positions.add(tile.getPosition());
            }
        }
        return positions;
    }

    private static boolean isDefectorsPlacementTileEligible(Player player, Game game, Tile tile) {
        return tile != null
                && (tile.getTileModel() == null || !tile.getTileModel().isHyperlane())
                && !FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game);
    }

    private static List<String> getDefectorsPositionsForRing(Game game, Player player, String ringSelector) {
        Set<String> eligiblePositions = getDefectorsEligibleDestinationPositions(game, player);
        List<String> positions;
        if ("0".equals(ringSelector)) {
            positions = game.getTileByPosition("000") == null ? List.of() : List.of("000");
        } else if ("corners".equalsIgnoreCase(ringSelector)) {
            positions = getDefectorsCornerPositions(game);
        } else if (ringSelector.endsWith("_left") || ringSelector.endsWith("_right")) {
            String baseRing = ringSelector.substring(0, ringSelector.indexOf('_'));
            List<String> ringPositions = PositionMapper.getPositionsInRing(baseRing, game);
            int midpoint = ringPositions.size() / 2;
            positions = ringSelector.endsWith("_left")
                    ? ringPositions.subList(midpoint, ringPositions.size())
                    : ringPositions.subList(0, midpoint);
        } else {
            positions = PositionMapper.getPositionsInRing(ringSelector, game);
        }

        return positions.stream().filter(eligiblePositions::contains).toList();
    }

    private static List<String> getDefectorsCornerPositions(Game game) {
        List<String> positions = new ArrayList<>(List.of("tl", "tr", "bl", "br"));
        positions.addAll(Stream.of("frac1", "frac2", "frac3", "frac4", "frac5", "frac6", "frac7")
                .filter(pos -> game.getTileByPosition(pos) != null)
                .toList());
        positions.removeIf(pos -> game.getTileByPosition(pos) == null);
        return positions;
    }

    private static Map<String, Map<String, Integer>> getDefectorsEligibleShipsBySystem(
            Game game, Player resolvingPlayer, Player target) {
        Map<String, Map<String, Integer>> eligibleShipsBySystem = new HashMap<>();
        for (Map.Entry<String, Integer> producedEntry :
                target.getCurrentProducedUnits().entrySet()) {
            ProducedUnitRecord producedUnit = parseProducedUnit(producedEntry.getKey());
            if (producedUnit == null || !"space".equalsIgnoreCase(producedUnit.location())) {
                continue;
            }

            Tile tile = game.getTileByPosition(producedUnit.tilePos());
            if (tile == null || !FoWHelper.playerHasShipsInSystem(target, tile)) {
                continue;
            }

            UnitModel targetShip = getDefectorsShipModelForPlacement(game, target, producedUnit.unitAlias());
            if (!isDefectorsShipEligible(resolvingPlayer, targetShip)) {
                continue;
            }

            eligibleShipsBySystem
                    .computeIfAbsent(producedUnit.tilePos(), ignored -> new HashMap<>())
                    .merge(producedUnit.unitAlias(), producedEntry.getValue(), Integer::sum);
        }
        return eligibleShipsBySystem;
    }

    private static Map<String, Integer> getDefectorsRemainingShipsForSystem(
            Game game, Player resolvingPlayer, Player target, String sourcePos, List<String> selectedShips) {
        Map<String, Integer> availableShips = new HashMap<>(
                getDefectorsEligibleShipsBySystem(game, resolvingPlayer, target).getOrDefault(sourcePos, Map.of()));
        for (String selectedShip : selectedShips) {
            int remaining = availableShips.getOrDefault(selectedShip, 0) - 1;
            if (remaining <= 0) {
                availableShips.remove(selectedShip);
            } else {
                availableShips.put(selectedShip, remaining);
            }
        }
        return availableShips;
    }

    private static boolean isDefectorsShipEligible(Player resolvingPlayer, UnitModel ship) {
        return ship != null
                && ship.getIsShip()
                && ship.getCapacityValue() <= 0
                && getMatchingDefectorsUnit(resolvingPlayer, ship) != null;
    }

    private static UnitModel getMatchingDefectorsUnit(Player player, UnitModel targetShip) {
        if (targetShip == null) {
            return null;
        }
        UnitModel matchingShip = player.getUnitByBaseType(targetShip.getBaseType());
        if (matchingShip != null && matchingShip.getIsShip()) {
            return matchingShip;
        }
        return targetShip.getUnitType() == null ? null : player.getUnitByType(targetShip.getUnitType());
    }

    private static UnitModel getDefectorsShipModelForPlacement(Game game, Player player, String unitAlias) {
        if (player == null || unitAlias == null || unitAlias.isBlank()) {
            return null;
        }
        UnitModel matchingByAsyncId = player.getPriorityUnitByAsyncID(unitAlias, null);
        if (matchingByAsyncId != null) {
            return matchingByAsyncId;
        }

        String resolvedUnit = ti4.helpers.AliasHandler.resolveUnit(unitAlias);
        UnitKey unitKey = Mapper.getUnitKey(resolvedUnit, player.getColor());
        if (unitKey != null) {
            UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
            if (unitModel != null) {
                return unitModel;
            }
        }
        return player.getUnitByBaseType(resolvedUnit);
    }

    private static String getDefectorsShipName(UnitModel ship, String fallbackAlias) {
        return ship == null ? StringUtils.capitalize(fallbackAlias.replace('_', ' ')) : ship.getName();
    }

    private static Player getDefectorsStoredTarget(Game game, Player player) {
        String faction = game.getStoredValue(defectorsTargetKey(player));
        return faction.isBlank() ? null : game.getPlayerFromColorOrFaction(faction);
    }

    private static void clearDefectorsState(Game game, Player player) {
        game.removeStoredValue(defectorsTargetKey(player));
        game.removeStoredValue(defectorsSourceKey(player));
        game.removeStoredValue(defectorsSelectedKey(player));
        game.removeStoredValue(defectorsPendingKey(player));
    }

    private static List<String> getStoredStringList(Game game, String key) {
        String storedValue = game.getStoredValue(key);
        if (storedValue == null || storedValue.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(storedValue.split(",")));
    }

    private static void setStoredStringList(Game game, String key, List<String> values) {
        if (values.isEmpty()) {
            game.removeStoredValue(key);
            return;
        }
        game.setStoredValue(key, String.join(",", values));
    }

    private static String defectorsTargetKey(Player player) {
        return DEFECTORS_TARGET_KEY_PREFIX + player.getFaction();
    }

    private static String defectorsSourceKey(Player player) {
        return DEFECTORS_SOURCE_KEY_PREFIX + player.getFaction();
    }

    private static String defectorsSelectedKey(Player player) {
        return DEFECTORS_SELECTED_KEY_PREFIX + player.getFaction();
    }

    private static String defectorsPendingKey(Player player) {
        return DEFECTORS_PENDING_KEY_PREFIX + player.getFaction();
    }

    private record ProducedUnitRecord(String unitAlias, String tilePos, String location) {}

    private static ProducedUnitRecord parseProducedUnit(String producedUnitKey) {
        int lastSeparator = producedUnitKey.lastIndexOf('_');
        if (lastSeparator < 0 || lastSeparator == producedUnitKey.length() - 1) {
            return null;
        }
        int middleSeparator = producedUnitKey.lastIndexOf('_', lastSeparator - 1);
        if (middleSeparator < 0) {
            return null;
        }
        return new ProducedUnitRecord(
                producedUnitKey.substring(0, middleSeparator),
                producedUnitKey.substring(middleSeparator + 1, lastSeparator),
                producedUnitKey.substring(lastSeparator + 1));
    }
}
