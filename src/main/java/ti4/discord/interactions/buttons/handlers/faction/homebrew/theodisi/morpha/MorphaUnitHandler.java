package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.morpha;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.MoveUnitService;

@UtilityClass
public class MorphaUnitHandler {
    private static final String CYTOPLASMIC_STATE = "morphaCytoplasmicState_";
    private static final String CYTOPLASMIC_CARGO = "morphaCytoplasmicCargo_";

    @ButtonHandler("deployOoze_")
    public static void deployOoze(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String tilePos = buttonID.replace("deployOoze_", "");
        Tile tile = game.getTileByPosition(tilePos);
        if (tile == null
                || !tilePos.equals(game.getActiveSystem())
                || !player.hasUnit("morpha_flagship")
                || !FoWHelper.playerHasShipsInSystem(player, tile)
                || ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "fs", true) >= player.getUnitCap("fs")
                || player.getActionCards().isEmpty()
                || !game.getStoredValue("oozeDeployed_" + player.getFaction()).isBlank()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue("oozeDeployed_" + player.getFaction(), tilePos);

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 fs");
        ActionCardHelper.sendACDiscardButtons(player);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " placed _The Ooze_ in " + tile.getRepresentation()
                        + ". Buttons to discard an action card have been sent to your `#cards-info` thread.");

        ButtonHelper.deleteMessage(event);
    }

    public static void clearOozeDeploy(Game game) {
        for (Player player : game.getRealPlayers()) {
            if (game.getStoredValue("oozeDeployed_" + player.getFaction()).isBlank()) {
                continue;
            }
            for (Tile tile : game.getTileMap().values()) {
                tile.getSpaceUnitHolder().removeUnit(Units.getUnitKey(UnitType.Flagship, player.getColor()), 1);
            }
            game.removeStoredValue("oozeDeployed_" + player.getFaction());
        }
    }

    public static void recordCytoplasmicTransportDiscards(Game game, Player player, int discardedActionCards) {
        int requiredDiscards = getCytoplasmicDiscardRequirement(player);
        if (requiredDiscards == 0
                || discardedActionCards < requiredDiscards
                || !game.getStoredValue(CYTOPLASMIC_STATE + player.getFaction()).isBlank()) {
            return;
        }

        game.setStoredValue(CYTOPLASMIC_STATE + player.getFaction(), "available");
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may use _Cytoplasmic Transports_ to produce 1 carrier or move 1 carrier to a system containing your space dock.",
                Buttons.green(
                        player.factionButtonChecker() + "useCytoplasmicTransports", "Use Cytoplasmic Transports"));
    }

    public static void clearCytoplasmicTransports(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(CYTOPLASMIC_STATE + player.getFaction());
            game.removeStoredValue(CYTOPLASMIC_CARGO + player.getFaction());
        }
    }

    @ButtonHandler("useCytoplasmicTransports")
    public static void useCytoplasmicTransports(ButtonInteractionEvent event, Game game, Player player) {
        if (!isCytoplasmicTransportAvailable(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        if (hasCarrierReinforcement(game, player) && !getShipTiles(game, player).isEmpty()) {
            buttons.add(
                    Buttons.green(player.factionButtonChecker() + "chooseCytoplasmicProduction", "Produce 1 Carrier"));
        }
        if (!getCarrierTiles(game, player).isEmpty()
                && !getSpaceDockTiles(game, player).isEmpty()) {
            buttons.add(Buttons.blue(player.factionButtonChecker() + "chooseCytoplasmicMovement", "Move 1 Carrier"));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + "declineCytoplasmicTransports", "Decline"));
        MessageHelper.editMessageWithButtons(
                event, player.getRepresentation() + ", choose how to resolve _Cytoplasmic Transports_.", buttons);
    }

    @ButtonHandler("declineCytoplasmicTransports")
    public static void declineCytoplasmicTransports(ButtonInteractionEvent event, Game game, Player player) {
        if (isCytoplasmicTransportAvailable(game, player)) {
            game.setStoredValue(CYTOPLASMIC_STATE + player.getFaction(), "declined");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("chooseCytoplasmicProduction")
    public static void chooseCytoplasmicProductionSystem(ButtonInteractionEvent event, Game game, Player player) {
        if (!isCytoplasmicTransportAvailable(game, player) || !hasCarrierReinforcement(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getShipTiles(game, player).stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + "cytoplasmicProduceIn_" + tile.getPosition(),
                        "Produce in " + tile.getRepresentationForButtons(game, player)))
                .toList();
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentation() + ", choose a system containing your ships to produce 1 carrier.",
                buttons);
    }

    @ButtonHandler("cytoplasmicProduceIn_")
    public static void produceCytoplasmicCarrier(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring("cytoplasmicProduceIn_".length()));
        if (!isCytoplasmicTransportAvailable(game, player)
                || !hasCarrierReinforcement(game, player)
                || tile == null
                || !getShipTiles(game, player).contains(tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons =
                Helper.getPlaceUnitButtons(event, player, game, tile, "sling", "placeOneNDone_dontskip").stream()
                        .filter(button -> button.getCustomId().endsWith("_carrier_" + tile.getPosition()))
                        .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(CYTOPLASMIC_STATE + player.getFaction(), "used");
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + " used _Cytoplasmic Transports_ to produce 1 carrier in "
                        + tile.getRepresentationForButtons(game, player)
                        + ". You must pay its cost.\n> "
                        + ButtonHelper.getListOfStuffAvailableToSpend(player, game),
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("chooseCytoplasmicMovement")
    public static void chooseCytoplasmicCarrier(ButtonInteractionEvent event, Game game, Player player) {
        if (!isCytoplasmicTransportAvailable(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getCarrierTiles(game, player).stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + "cytoplasmicMoveFrom_" + tile.getPosition(),
                        "Move from " + tile.getRepresentationForButtons(game, player)))
                .toList();
        MessageHelper.editMessageWithButtons(
                event, player.getRepresentation() + ", choose the system containing the carrier to move.", buttons);
    }

    @ButtonHandler("cytoplasmicMoveFrom_")
    public static void chooseCytoplasmicDestination(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile source = game.getTileByPosition(buttonID.substring("cytoplasmicMoveFrom_".length()));
        if (!isCytoplasmicTransportAvailable(game, player)
                || source == null
                || !getCarrierTiles(game, player).contains(source)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getSpaceDockTiles(game, player).stream()
                .filter(tile -> tile != source)
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + "cytoplasmicMoveTo_" + source.getPosition() + "|"
                                + tile.getPosition(),
                        "Move to " + tile.getRepresentationForButtons(game, player)))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.editMessageWithButtons(
                    event,
                    player.getRepresentation() + ", you do not have another system containing your space dock.",
                    List.of(Buttons.red(player.factionButtonChecker() + "declineCytoplasmicTransports", "Decline")));
            return;
        }
        MessageHelper.editMessageWithButtons(
                event, player.getRepresentation() + ", choose a system containing one of your space docks.", buttons);
    }

    @ButtonHandler("cytoplasmicMoveTo_")
    public static void chooseCytoplasmicCargo(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] positions = buttonID.substring("cytoplasmicMoveTo_".length()).split("\\|", 2);
        if (positions.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile source = game.getTileByPosition(positions[0]);
        Tile destination = game.getTileByPosition(positions[1]);
        if (!isCytoplasmicTransportAvailable(game, player)
                || source == null
                || destination == null
                || !getCarrierTiles(game, player).contains(source)
                || !getSpaceDockTiles(game, player).contains(destination)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.removeStoredValue(CYTOPLASMIC_CARGO + player.getFaction());
        sendCytoplasmicCargoButtons(event, game, player, source, destination);
    }

    @ButtonHandler("cytoplasmicCargo_")
    public static void selectCytoplasmicCargo(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring("cytoplasmicCargo_".length()).split("\\|", 4);
        if (payload.length != 4) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile source = game.getTileByPosition(payload[0]);
        Tile destination = game.getTileByPosition(payload[1]);
        UnitHolder holder = source == null ? null : source.getUnitHolders().get(payload[3]);
        UnitKey unitKey = getUnitKey(player, holder, payload[2]);
        if (!isCytoplasmicTransportAvailable(game, player)
                || source == null
                || destination == null
                || holder == null
                || unitKey == null
                || !isCytoplasmicCargo(player, unitKey)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<String> cargo = getCytoplasmicCargo(game, player);
        String selection = payload[2] + "|" + payload[3];
        long selectedCount = cargo.stream().filter(selection::equals).count();
        if (selectedCount >= holder.getUnitCount(unitKey)) {
            return;
        }
        cargo.add(selection);
        game.setStoredValue(CYTOPLASMIC_CARGO + player.getFaction(), String.join(",", cargo));
        sendCytoplasmicCargoButtons(event, game, player, source, destination);
    }

    @ButtonHandler("cytoplasmicFinishMove_")
    public static void moveCytoplasmicCarrier(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] positions =
                buttonID.substring("cytoplasmicFinishMove_".length()).split("\\|", 2);
        if (positions.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile source = game.getTileByPosition(positions[0]);
        Tile destination = game.getTileByPosition(positions[1]);
        if (!isCytoplasmicTransportAvailable(game, player)
                || source == null
                || destination == null
                || !getCarrierTiles(game, player).contains(source)
                || !getSpaceDockTiles(game, player).contains(destination)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<String> units = new ArrayList<>(List.of("1 carrier"));
        for (String cargo : getCytoplasmicCargo(game, player)) {
            String[] selection = cargo.split("\\|", 2);
            if (selection.length != 2) continue;
            UnitHolder holder = source.getUnitHolders().get(selection[1]);
            UnitKey unitKey = getUnitKey(player, holder, selection[0]);
            if (holder != null
                    && unitKey != null
                    && holder.getUnitCount(unitKey) > 0
                    && isCytoplasmicCargo(player, unitKey)) {
                units.add("1 " + selection[0] + ("space".equals(selection[1]) ? "" : " " + selection[1]));
            }
        }
        MoveUnitService.moveUnits(
                event, source, game, player.getColor(), String.join(", ", units), destination, "space");
        game.setStoredValue(CYTOPLASMIC_STATE + player.getFaction(), "used");
        game.removeStoredValue(CYTOPLASMIC_CARGO + player.getFaction());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " used _Cytoplasmic Transports_ to move 1 carrier to "
                        + destination.getRepresentationForButtons(game, player) + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static void sendCytoplasmicCargoButtons(
            ButtonInteractionEvent event, Game game, Player player, Tile source, Tile destination) {
        List<String> cargo = getCytoplasmicCargo(game, player);
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder holder : source.getUnitHolders().values()) {
            for (UnitKey unitKey : holder.getUnitKeysForPlayer(player)) {
                if (!isCytoplasmicCargo(player, unitKey)) continue;
                String selection = unitKey.asyncID() + "|" + holder.getName();
                if (cargo.stream().filter(selection::equals).count() >= holder.getUnitCount(unitKey)) continue;
                UnitModel unit = player.getUnitFromUnitKey(unitKey);
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "cytoplasmicCargo_" + source.getPosition() + "|"
                                + destination.getPosition() + "|" + unitKey.asyncID() + "|" + holder.getName(),
                        "Transport " + unit.getName(),
                        unitKey.unitEmoji()));
            }
        }
        buttons.add(Buttons.blue(
                player.factionButtonChecker() + "cytoplasmicFinishMove_" + source.getPosition() + "|"
                        + destination.getPosition(),
                "Move Carrier"));
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentation()
                        + ", optionally select units to transport with the carrier.\n-# Capacity is player-enforced.",
                buttons);
    }

    private static int getCytoplasmicDiscardRequirement(Player player) {
        if (player.hasUnit("morpha_carrier2")) return 1;
        return player.hasUnit("morpha_carrier") ? 2 : 0;
    }

    private static boolean isCytoplasmicTransportAvailable(Game game, Player player) {
        return "available".equals(game.getStoredValue(CYTOPLASMIC_STATE + player.getFaction()));
    }

    private static boolean hasCarrierReinforcement(Game game, Player player) {
        return ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "carrier", true) < player.getUnitCap("cv");
    }

    private static List<Tile> getShipTiles(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                        .anyMatch(unitKey -> {
                            UnitModel unit = player.getUnitFromUnitKey(unitKey);
                            return unit != null && unit.getIsShip();
                        }))
                .toList();
    }

    private static List<Tile> getCarrierTiles(Game game, Player player) {
        return ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Carrier);
    }

    private static List<Tile> getSpaceDockTiles(Game game, Player player) {
        return ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock);
    }

    private static boolean isCytoplasmicCargo(Player player, UnitKey unitKey) {
        UnitModel unit = player.getUnitFromUnitKey(unitKey);
        return unit != null && (unitKey.unitType() == UnitType.Fighter || unit.getIsGroundForce());
    }

    private static UnitKey getUnitKey(Player player, UnitHolder holder, String asyncID) {
        if (holder == null) return null;
        return holder.getUnitKeysForPlayer(player).stream()
                .filter(unitKey -> asyncID.equals(unitKey.asyncID()))
                .findFirst()
                .orElse(null);
    }

    private static List<String> getCytoplasmicCargo(Game game, Player player) {
        String storedCargo = game.getStoredValue(CYTOPLASMIC_CARGO + player.getFaction());
        return storedCargo.isBlank() ? new ArrayList<>() : new ArrayList<>(List.of(storedCargo.split(",")));
    }
}
