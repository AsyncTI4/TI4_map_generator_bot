package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.MoveUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class UnitReplacementHelper {

    private static final Set<UnitType> REPLACEABLE_SHIPS = Set.of(
            UnitType.Destroyer,
            UnitType.Cruiser,
            UnitType.Carrier,
            UnitType.Dreadnought,
            UnitType.Flagship,
            UnitType.Warsun);

    // --- Ship replacement ---

    public static List<Button> getUnitsToReplace(Player player, Tile tile, int costDifferential) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet) continue;

            for (Map.Entry<UnitKey, List<Integer>> entry :
                    unitHolder.getUnitsByStateForPlayer(player).entrySet()) {
                UnitKey unitKey = entry.getKey();
                if (!REPLACEABLE_SHIPS.contains(unitKey.getUnitType())) continue;

                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                String prettyName = unitModel == null ? unitKey.getUnitType().humanReadableName() : unitModel.getName();
                String unitName = unitKey.unitName();
                int totalUnits = unitHolder.getUnitCount(unitKey);
                int damagedUnits = unitHolder.getDamagedUnitCount(unitKey);

                for (int x = 1; x < damagedUnits + 1 && x < 2; x++) {
                    buttons.add(Buttons.red(
                            finChecker + "replaceShipPickUnit_" + tile.getPosition() + "_" + unitName + "damaged_"
                                    + costDifferential,
                            "Remove A Damaged " + prettyName,
                            unitKey.unitEmoji()));
                }
                totalUnits -= damagedUnits;
                for (int x = 1; x < totalUnits + 1 && x < 2; x++) {
                    buttons.add(Buttons.red(
                            finChecker + "replaceShipPickUnit_" + tile.getPosition() + "_" + unitName + "_"
                                    + costDifferential,
                            "Remove " + x + " " + prettyName,
                            unitKey.unitEmoji()));
                }
            }
        }
        buttons.add(Buttons.red(finChecker + "deleteButtons", "Decline"));
        return buttons;
    }

    @ButtonHandler("replaceShipPickUnit_")
    public static void handleReplaceShipPickUnit(
            ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String[] parts = buttonID.split("_");
        String pos = parts[1];
        String unit = parts[2];
        int costDifferential = Integer.parseInt(parts[3]);
        List<Button> buttons =
                getReplacementOptions(player, game, event, game.getTileByPosition(pos), unit, costDifferential);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentationUnfogged() + ", please choose which unit you wish to place down.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getReplacementOptions(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unit,
            int costDifferential) {
        String finChecker = "FFCC_" + player.getFaction() + "_";

        boolean damaged = unit.contains("damaged");
        if (damaged) unit = unit.replace("damaged", "");

        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
        RemoveUnitService.removeUnit(event, tile, game, new ParsedUnit(unitKey), damaged);

        String msg = (damaged ? "A damaged " : "") + unitKey.unitEmoji() + " was removed by "
                + player.getFactionEmoji() + ". A ship costing up to " + costDifferential
                + " more than it may now be placed.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);

        List<String> allowedUnits = Stream.of(
                        UnitType.Destroyer,
                        UnitType.Cruiser,
                        UnitType.Carrier,
                        UnitType.Dreadnought,
                        UnitType.Flagship,
                        UnitType.Warsun,
                        UnitType.Fighter)
                .map(UnitType::getValue)
                .toList();

        UnitModel removedUnit = player.getUnitsByAsyncID(unitKey.asyncID()).getFirst();
        List<Button> buttons = new ArrayList<>();
        for (String asyncID : allowedUnits) {
            UnitModel ownedUnit = player.getUnitFromAsyncID(asyncID);
            if (ownedUnit != null && ownedUnit.getCost() <= removedUnit.getCost() + costDifferential) {
                buttons.add(Buttons.green(
                        finChecker + "replaceShipPlace_" + ownedUnit.getBaseType() + "_" + tile.getPosition(),
                        "Place " + ownedUnit.getName(),
                        ownedUnit.getUnitEmoji()));
            }
        }
        return buttons;
    }

    @ButtonHandler("replaceShipPlace_")
    public static void handleReplaceShipPlace(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String[] parts = buttonID.replace("replaceShipPlace_", "").split("_");
        String unit = parts[0];
        String pos = parts[1];
        Tile tile = game.getTileByPosition(pos);
        AddUnitService.addUnits(event, tile, game, player.getColor(), unit);
        UnitModel unitModel = player.getUnitByBaseType(unit);
        String unitDisplay = unitModel != null ? unitModel.getUnitEmoji() + " " + unitModel.getName() : unit;
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmojiOrColor() + " replaced a ship with 1 " + unitDisplay + " in "
                        + tile.getRepresentationForButtons(game, player));
        ButtonHelper.deleteMessage(event);
    }

    // --- Mech replacement (infantry → mech) ---

    public static List<Button> getMechReplacementButtons(Player player, Game game, String reason) {
        List<Button> planetButtons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                String colorID = Mapper.getColorID(player.getColor());
                int numInf = unitHolder.getUnitCount(UnitType.Infantry, colorID);
                if (numInf > 0) {
                    String buttonID = player.getFinsFactionCheckerPrefix() + "mechReplacement_" + tile.getPosition()
                            + "_" + unitHolder.getName() + "_" + reason;
                    if ("space".equalsIgnoreCase(unitHolder.getName())) {
                        planetButtons.add(Buttons.green(
                                buttonID, "Space Area of " + tile.getRepresentationForButtons(game, player)));
                    } else {
                        planetButtons.add(
                                Buttons.green(buttonID, Helper.getPlanetRepresentation(unitHolder.getName(), game)));
                    }
                }
            }
        }
        return planetButtons;
    }

    @ButtonHandler("mechReplacement_")
    public static void handleMechPlacement(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String[] parts = buttonID.split("_", 4);
        Tile tile = game.getTileByPosition(parts[1]);
        String uH = parts[2];
        String reason = parts[3];
        String via = reason.isBlank() ? "." : " with " + reason + ".";
        String successMessage;
        if ("space".equalsIgnoreCase(uH)) {
            successMessage = player.getFactionEmojiOrColor() + " replaced 1 infantry with 1 mech in the space area of "
                    + tile.getRepresentationForButtons(game, player) + via;
        } else {
            successMessage = player.getFactionEmojiOrColor() + " replaced 1 infantry with 1 mech on "
                    + Helper.getPlanetRepresentation(uH, game) + via;
        }
        UnitHolder holder = tile.getUnitHolders().get(uH);
        MoveUnitService.replaceUnit(event, game, player, tile, holder, UnitType.Infantry, UnitType.Mech);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), successMessage);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
