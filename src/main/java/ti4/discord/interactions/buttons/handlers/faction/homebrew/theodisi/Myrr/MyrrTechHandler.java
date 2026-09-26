package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Myrr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class MyrrTechHandler {
    private static final String SEGMENTED_STRUCTURING = "thmyrry";
    private static final String SEGMENTED_USE = "useSegmentedStructuring_";
    private static final String SEGMENTED_REMOVE = "segmentedStructuringRemove_";
    private static final String SEGMENTED_DONE_REMOVING = "segmentedStructuringDoneRemoving";
    private static final String SEGMENTED_PLACE = "segmentedStructuringPlace_";
    private static final String SEGMENTED_DONE_PLACING = "segmentedStructuringDonePlacing";

    public static void addSegmentedStructuringButton(List<Button> buttons, Game game, Player player, Tile tile) {
        if (!player.hasTech(SEGMENTED_STRUCTURING)
                || !game.getActiveSystem().equals(tile.getPosition())
                || !game.getStoredValue(getSegmentedUsedKey(player)).isEmpty()
                || getSegmentedShips(player, tile).isEmpty()) {
            return;
        }
        buttons.add(Buttons.blue(
                player.factionButtonChecker() + SEGMENTED_USE + tile.getPosition(), "Use Segmented Structuring"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
    }

    public static void offerSegmentedStructuring(ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        addSegmentedStructuringButton(buttons, game, player, tile);
        if (!buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged() + ", you may use **Segmented Structuring**.",
                    buttons);
        }
    }

    @ButtonHandler(SEGMENTED_USE)
    public static void useSegmentedStructuring(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(SEGMENTED_USE.length()));
        if (tile == null
                || !player.hasTech(SEGMENTED_STRUCTURING)
                || !game.getActiveSystem().equals(tile.getPosition())
                || !game.getStoredValue(getSegmentedUsedKey(player)).isEmpty()
                || getSegmentedShips(player, tile).isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(getSegmentedUsedKey(player), "true");
        game.setStoredValue(getSegmentedCostKey(player), "0");
        game.setStoredValue(getSegmentedFighterCountKey(player), "0");
        game.setStoredValue(getSegmentedPositionKey(player), tile.getPosition());
        sendSegmentedRemovalButtons(event, game, player, tile);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SEGMENTED_REMOVE)
    public static void removeSegmentedShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SEGMENTED_REMOVE.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        UnitKey unitKey = tile == null
                ? null
                : getSegmentedShips(player, tile).stream()
                        .filter(key -> key.asyncID().equals(payload[1]))
                        .findFirst()
                        .orElse(null);
        if (!isResolvingSegmentedStructuring(game, player, tile) || unitKey == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), tile.getSpaceUnitHolder());
        int removedCost = getHalfCost(unit);
        if (unitKey.unitType() == UnitType.Fighter) {
            int removedFighters = getSegmentedFighterCount(game, player);
            removedCost = removedFighters % 2 == 0 ? 2 : 0;
            game.setStoredValue(getSegmentedFighterCountKey(player), Integer.toString(removedFighters + 1));
        }
        RemoveUnitService.removeUnit(event, tile, game, player, tile.getSpaceUnitHolder(), unitKey.unitType(), 1);
        game.setStoredValue(
                getSegmentedCostKey(player), Integer.toString(getSegmentedCost(game, player) + removedCost));

        sendSegmentedRemovalButtons(event, game, player, tile);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SEGMENTED_DONE_REMOVING)
    public static void finishSegmentedRemovals(ButtonInteractionEvent event, Game game, Player player) {
        Tile tile = game.getTileByPosition(game.getStoredValue(getSegmentedPositionKey(player)));
        int cost = getSegmentedCost(game, player);
        if (!isResolvingSegmentedStructuring(game, player, tile) || cost == 0) {
            clearSegmentedStructuringState(game, player);
            ButtonHelper.deleteMessage(event);
            return;
        }

        int production = tile.getUnitHolders().values().stream()
                .mapToInt(holder -> Helper.getProductionValueOfUnitHolder(player, game, tile, holder, true))
                .sum();
        if (production < 1) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged()
                            + " removed ships using **Segmented Structuring**, but has no PRODUCTION in the active system to place units.");
            clearSegmentedStructuringState(game, player);
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(getSegmentedProductionKey(player), Integer.toString(production));
        sendSegmentedPlacementButtons(event, game, player, tile);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SEGMENTED_PLACE)
    public static void placeWithSegmentedStructuring(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String payload = buttonID.substring(SEGMENTED_PLACE.length());
        String[] parts = payload.split("\\|", 3);
        if (parts.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(game.getStoredValue(getSegmentedPositionKey(player)));
        SegmentedPlacement placement = getSegmentedPlacement(player, parts[2]);
        int cost = getSegmentedCost(game, player);
        int production = getSegmentedProduction(game, player);
        if (!isResolvingSegmentedStructuring(game, player, tile)
                || placement == null
                || placement.halfCost() > cost
                || placement.count() > production) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(getSegmentedCostKey(player), Integer.toString(cost - placement.halfCost()));
        game.setStoredValue(getSegmentedProductionKey(player), Integer.toString(production - placement.count()));
        ButtonHelperModifyUnits.placeUnitAndDeleteButton(
                "placeOneNDone_skipbuild_" + placement.unitAndHolder(), event, game, player);
        sendSegmentedPlacementButtons(event, game, player, tile);
    }

    @ButtonHandler(SEGMENTED_DONE_PLACING)
    public static void finishSegmentedPlacements(ButtonInteractionEvent event, Game game, Player player) {
        clearSegmentedStructuringState(game, player);
        ButtonHelper.deleteMessage(event);
    }

    public static void clearSegmentedStructuring(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(getSegmentedUsedKey(player));
            clearSegmentedStructuringState(game, player);
        }
    }

    private static void sendSegmentedRemovalButtons(ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey : getSegmentedShips(player, tile)) {
            UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), tile.getSpaceUnitHolder());
            int removalCost = unitKey.unitType() == UnitType.Fighter
                    ? (getSegmentedFighterCount(game, player) % 2 == 0 ? 2 : 0)
                    : getHalfCost(unit);
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + SEGMENTED_REMOVE + tile.getPosition() + "|" + unitKey.asyncID(),
                    "Remove 1 " + unit.getName() + " (Cost " + formatHalfCost(removalCost) + ")",
                    unitKey.unitEmoji()));
        }
        buttons.add(Buttons.green(player.factionButtonChecker() + SEGMENTED_DONE_REMOVING, "Done Removing Ships"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", remove any number of non-flagship ships using **Segmented Structuring**."
                        + " Removed cost: "
                        + formatHalfCost(getSegmentedCost(game, player))
                        + ".",
                buttons);
    }

    private static void sendSegmentedPlacementButtons(
            ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        int cost = getSegmentedCost(game, player);
        int production = getSegmentedProduction(game, player);
        Map<String, Integer> producedUnits = new HashMap<>(player.getCurrentProducedUnits());
        List<Button> buttons = Helper.getPlaceUnitButtons(
                event, player, game, tile, "segmentedstructuring", SEGMENTED_PLACE + cost + "|" + production + "|");
        player.resetProducedUnits();
        producedUnits.forEach(player::setProducedUnit);

        buttons.removeIf(button -> {
            String customId = button.getCustomId();
            int index = customId.indexOf(SEGMENTED_PLACE);
            String[] payload = index < 0
                    ? new String[0]
                    : customId.substring(index + SEGMENTED_PLACE.length()).split("\\|", 3);
            SegmentedPlacement placement = payload.length < 3 ? null : getSegmentedPlacement(player, payload[2]);
            return placement == null || placement.halfCost() > cost || placement.count() > production;
        });
        buttons.add(Buttons.red(player.factionButtonChecker() + SEGMENTED_DONE_PLACING, "Done Placing Units"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", place units using **Segmented Structuring**. Remaining: "
                        + production
                        + " PRODUCTION and "
                        + formatHalfCost(cost)
                        + " cost.",
                buttons);
    }

    private static List<UnitKey> getSegmentedShips(Player player, Tile tile) {
        UnitHolder space = tile.getSpaceUnitHolder();
        return space.getUnits().keySet().stream()
                .filter(unitKey -> unitKey.getColor().equalsIgnoreCase(player.getColor()))
                .filter(unitKey -> unitKey.unitType() != UnitType.Flagship)
                .filter(unitKey -> {
                    UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), space);
                    return unit != null && unit.getIsShip() && space.getUnitCount(unitKey) > 0;
                })
                .toList();
    }

    private static boolean isResolvingSegmentedStructuring(Game game, Player player, Tile tile) {
        return tile != null
                && player.hasTech(SEGMENTED_STRUCTURING)
                && "true".equals(game.getStoredValue(getSegmentedUsedKey(player)))
                && tile.getPosition().equals(game.getStoredValue(getSegmentedPositionKey(player)));
    }

    private static SegmentedPlacement getSegmentedPlacement(Player player, String unitAndHolder) {
        String[] parts = unitAndHolder.replaceFirst("^_", "").split("_", 2);
        if (parts.length != 2) {
            return null;
        }
        String unit = parts[0];
        int count = 1;
        if ("2ff".equals(unit) || "2gf".equals(unit)) {
            count = 2;
            unit = unit.substring(1);
        }
        String unitAlias =
                switch (unit) {
                    case "ff" -> "fighter";
                    case "gf" -> "infantry";
                    case "mf" -> "mech";
                    default -> unit;
                };
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitAlias), player.getColorID());
        if (unitKey == null) {
            return null;
        }
        UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
        if (unitModel == null) {
            return null;
        }
        return new SegmentedPlacement(unitAndHolder.replaceFirst("^_", ""), count, getHalfCost(unitModel) * count);
    }

    private static int getHalfCost(UnitModel unit) {
        return Math.round(unit.getCost() * 2);
    }

    private static String formatHalfCost(int halfCost) {
        return halfCost % 2 == 0 ? Integer.toString(halfCost / 2) : halfCost / 2 + ".5";
    }

    private static int getSegmentedCost(Game game, Player player) {
        String cost = game.getStoredValue(getSegmentedCostKey(player));
        return cost.isEmpty() ? 0 : Integer.parseInt(cost);
    }

    private static int getSegmentedProduction(Game game, Player player) {
        String production = game.getStoredValue(getSegmentedProductionKey(player));
        return production.isEmpty() ? 0 : Integer.parseInt(production);
    }

    private static int getSegmentedFighterCount(Game game, Player player) {
        String fighters = game.getStoredValue(getSegmentedFighterCountKey(player));
        return fighters.isEmpty() ? 0 : Integer.parseInt(fighters);
    }

    private static void clearSegmentedStructuringState(Game game, Player player) {
        game.removeStoredValue(getSegmentedCostKey(player));
        game.removeStoredValue(getSegmentedProductionKey(player));
        game.removeStoredValue(getSegmentedFighterCountKey(player));
        game.removeStoredValue(getSegmentedPositionKey(player));
    }

    private static String getSegmentedUsedKey(Player player) {
        return "myrrSegmentedStructuringUsed_" + player.getFaction();
    }

    private static String getSegmentedCostKey(Player player) {
        return "myrrSegmentedStructuringCost_" + player.getFaction();
    }

    private static String getSegmentedProductionKey(Player player) {
        return "myrrSegmentedStructuringProduction_" + player.getFaction();
    }

    private static String getSegmentedFighterCountKey(Player player) {
        return "myrrSegmentedStructuringFighters_" + player.getFaction();
    }

    private static String getSegmentedPositionKey(Player player) {
        return "myrrSegmentedStructuringPosition_" + player.getFaction();
    }

    private record SegmentedPlacement(String unitAndHolder, int count, int halfCost) {}
}
