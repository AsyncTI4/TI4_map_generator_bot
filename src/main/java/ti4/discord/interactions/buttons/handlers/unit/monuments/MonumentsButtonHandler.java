package ti4.discord.interactions.buttons.handlers.unit.monuments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.game.MonumentsService;
import ti4.service.unit.AddUnitService;

public class MonumentsButtonHandler {
    private static final String PLACE_UNIT_ON_MONUMENT_PLANET = "placeUnitOnMonumentPlanet_";
    private static final String PLACE_ARBOREC_MONUMENT_INFANTRY = "placeArborecMonumentInfantry_";

    public static List<Button> getPlaceUnitOnOrAdjacentToPlayerMonumentButtons(Game game, Player player, String unit) {
        return MonumentsService.getPlanetsInOrAdjacentToPlayerMonumentButtons(
                game, player, PLACE_UNIT_ON_MONUMENT_PLANET + unit + "|");
    }

    // Mowshir Freeport
    public static boolean canTradeUnitsWithHacanMonument(Game game, Player player) {
        if (!game.isMonumentsMode()) {
            return false;
        }
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).isEmpty()) {
                continue;
            }
            for (Player monumentOwner : game.getRealPlayers()) {
                if (monumentOwner.hasUnit("hacan_monument")
                        && (ButtonHelper.doesPlayerHaveUnitHere("hacan_monument", monumentOwner, tile)
                                || FoWHelper.getAdjacentTilesAndNotThisTile(
                                                game, tile.getPosition(), monumentOwner, false)
                                        .stream()
                                        .map(game::getTileByPosition)
                                        .anyMatch(adjacentTile -> adjacentTile != null
                                                && ButtonHelper.doesPlayerHaveUnitHere(
                                                        "hacan_monument", monumentOwner, adjacentTile)))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<Button> getHacanMonumentUnitTradeButtons(Game game, Player sender, Player receiver) {
        if (!game.isMonumentsMode()) {
            return List.of();
        }
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values().stream()
                .sorted(Comparator.comparing(Tile::getPosition))
                .toList()) {
            boolean eligibleSystem = game.getRealPlayers().stream()
                    .anyMatch(monumentOwner -> monumentOwner.hasUnit("hacan_monument")
                            && (ButtonHelper.doesPlayerHaveUnitHere("hacan_monument", monumentOwner, tile)
                                    || FoWHelper.getAdjacentTilesAndNotThisTile(
                                                    game, tile.getPosition(), monumentOwner, false)
                                            .stream()
                                            .map(game::getTileByPosition)
                                            .anyMatch(adjacentTile -> adjacentTile != null
                                                    && ButtonHelper.doesPlayerHaveUnitHere(
                                                            "hacan_monument", monumentOwner, adjacentTile))));
            if (!eligibleSystem) {
                continue;
            }
            for (UnitKey unitKey : tile.getSpaceUnitHolder().getUnitKeysForPlayer(sender)) {
                UnitModel unit = sender.getUnitFromUnitKey(unitKey);
                if (unit == null) {
                    continue;
                }
                for (UnitState state : tile.getSpaceUnitHolder().getNonZeroUnitStates(unitKey)) {
                    String stateText = state == UnitState.none ? "" : state.humanDescr() + " ";
                    for (int copy = 1; copy <= tile.getSpaceUnitHolder().getUnitCountForState(unitKey, state); copy++) {
                        String detail = tile.getPosition() + "|" + unitKey.asyncID() + "|" + state.name() + "|" + copy;
                        String item = "sending" + sender.getFaction() + "_receiving" + receiver.getFaction()
                                + "_MonumentUnits_" + detail;
                        if (!sender.getTransactionItems().contains(item)) {
                            buttons.add(Buttons.green(
                                    "offerToTransact_MonumentUnits_" + sender.getFaction() + "_" + receiver.getFaction()
                                            + "_" + detail,
                                    "Trade " + stateText + unit.getName() + " in "
                                            + tile.getRepresentationForButtons(game, sender),
                                    unitKey.unitEmoji()));
                        }
                    }
                }
            }
        }
        return buttons;
    }

    public static void resolveHacanMonumentUnitTrade(
            ButtonInteractionEvent event, Game game, Player sender, Player receiver, String detail) {
        String[] payload = detail.split("\\|", 4);
        if (payload.length != 4 || !game.isMonumentsMode()) {
            return;
        }
        Tile sourceTile = game.getTileByPosition(payload[0]);
        UnitKey unitKey = sourceTile == null
                ? null
                : sourceTile.getSpaceUnitHolder().getUnitKeysForPlayer(sender).stream()
                        .filter(key -> key.asyncID().equals(payload[1]))
                        .findFirst()
                        .orElse(null);
        UnitState state = Units.findUnitState(payload[2]);
        boolean eligibleSystem = sourceTile != null
                && game.getRealPlayers().stream()
                        .anyMatch(monumentOwner -> monumentOwner.hasUnit("hacan_monument")
                                && (ButtonHelper.doesPlayerHaveUnitHere("hacan_monument", monumentOwner, sourceTile)
                                        || FoWHelper.getAdjacentTilesAndNotThisTile(
                                                        game, sourceTile.getPosition(), monumentOwner, false)
                                                .stream()
                                                .map(game::getTileByPosition)
                                                .anyMatch(adjacentTile -> adjacentTile != null
                                                        && ButtonHelper.doesPlayerHaveUnitHere(
                                                                "hacan_monument", monumentOwner, adjacentTile))));
        if (sourceTile == null
                || unitKey == null
                || state == null
                || sourceTile.getSpaceUnitHolder().getUnitCountForState(unitKey, state) < 1
                || !eligibleSystem) {
            MessageHelper.sendMessageToChannel(
                    receiver.getCorrectChannel(),
                    sender.getRepresentationNoPing() + " could not complete a **Mowshir Freeport** unit trade.");
            return;
        }
        sourceTile.getSpaceUnitHolder().removeUnit(unitKey, 1, state);
        List<Button> destinationButtons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values().stream()
                .sorted(Comparator.comparing(Tile::getPosition))
                .toList()) {
            if (FoWHelper.playerHasShipsInSystem(receiver, tile) && CommandCounterHelper.hasCC(receiver, tile)) {
                destinationButtons.add(Buttons.green(
                        receiver.factionButtonChecker() + "hacanMonumentPlaceUnit_" + unitKey.asyncID() + "|"
                                + tile.getPosition(),
                        "Place 1 " + unitKey.humanReadableName() + " in "
                                + tile.getRepresentationForButtons(game, receiver),
                        unitKey.unitEmoji()));
            }
        }
        if (destinationButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    receiver.getCorrectChannel(),
                    receiver.getRepresentationNoPing() + " gained 1 " + unitKey.humanReadableName()
                            + " through **Mowshir Freeport**, but has no eligible system in which to replace it."
                            + " The unit was removed.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                receiver.getCorrectChannel(),
                receiver.getRepresentationNoPing() + ", choose a system containing your ships and command token to"
                        + " replace and move the traded " + unitKey.humanReadableName() + " with **Mowshir Freeport**.",
                destinationButtons);
    }

    @ButtonHandler("hacanMonumentPlaceUnit_")
    public static void placeHacanMonumentUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.replace("hacanMonumentPlaceUnit_", "").split("\\|", 2);
        if (payload.length != 2 || !game.isMonumentsMode()) {
            return;
        }
        Tile tile = game.getTileByPosition(payload[1]);
        if (tile == null
                || !FoWHelper.playerHasShipsInSystem(player, tile)
                || !CommandCounterHelper.hasCC(player, tile)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That system is no longer eligible.");
            return;
        }
        UnitModel unit = player.getPriorityUnitByAsyncID(payload[0], tile.getSpaceUnitHolder());
        String unitName = unit == null ? payload[0] : unit.getName();
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + payload[0] + " space");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " replaced and moved 1 " + unitName + " with **Mowshir Freeport**.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_UNIT_ON_MONUMENT_PLANET)
    public static void placeUnitOnMonumentPlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.isMonumentsMode()) {
            return;
        }
        String[] payload = buttonID.replace(PLACE_UNIT_ON_MONUMENT_PLANET, "").split("\\|", 2);
        if (payload.length != 2) {
            return;
        }

        String unit = payload[0];
        String planetName = payload[1];
        String expectedButtonId =
                player.factionButtonChecker() + PLACE_UNIT_ON_MONUMENT_PLANET + unit + "|" + planetName;
        boolean eligible = MonumentsService.getPlanetsInOrAdjacentToPlayerMonumentButtons(
                        game, player, PLACE_UNIT_ON_MONUMENT_PLANET + unit + "|")
                .stream()
                .map(Button::getCustomId)
                .anyMatch(expectedButtonId::equals);
        Tile tile = game.getTileFromPlanet(planetName);
        if (!eligible || tile == null || game.getUnitHolderFromPlanet(planetName) == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That planet is no longer eligible.");
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), unit + " " + planetName);
        ButtonHelper.deleteMessage(event);
    }

    // Flaah Orbitals
    public static List<Button> getArborecMonumentPlacementButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        String prefix = player.factionButtonChecker() + PLACE_ARBOREC_MONUMENT_INFANTRY;

        for (Button button : MonumentsService.getPlanetsInOrAdjacentToPlayerMonumentButtons(
                game, player, PLACE_ARBOREC_MONUMENT_INFANTRY)) {
            String planetName = button.getCustomId().replace(prefix, "");
            Tile tile = game.getTileFromPlanet(planetName);
            if (tile != null && !tile.isMecatol(game)) {
                buttons.add(button);
            }
        }

        return buttons;
    }

    @ButtonHandler(PLACE_ARBOREC_MONUMENT_INFANTRY)
    public static void placeArborecMonumentInfantry(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.isMonumentsMode() || !player.hasUnit("arborec_monument")) {
            return;
        }

        String planetName = buttonID.replace(PLACE_ARBOREC_MONUMENT_INFANTRY, "");
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        boolean eligible = getArborecMonumentPlacementButtons(game, player).stream()
                .map(Button::getCustomId)
                .anyMatch((player.factionButtonChecker() + buttonID)::equals);

        if (!eligible || tile == null || planet == null || tile.isMecatol(game)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That planet is no longer eligible.");
            return;
        }

        boolean containsAnotherPlayersUnits = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, planet).stream()
                .anyMatch(otherPlayer -> otherPlayer != player);
        String previousCoexistenceFlag = game.getStoredValue("coexistFlag");

        if (containsAnotherPlayersUnits) {
            game.setStoredValue("coexistFlag", "yes");
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planetName);

        if (containsAnotherPlayersUnits) {
            if (previousCoexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", previousCoexistenceFlag);
            }
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed 1 infantry "
                        + (containsAnotherPlayersUnits ? "into coexistence on " : "on ")
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " with **Flaah Orbitals**.");
    }

    // Revenance Circuit
    public static void sendRevenantCircuitButtons(Game game, Tile tile, Player monumentOwner) {
        boolean hasEligibleTarget = false;
        for (String adjacentPosition :
                FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), monumentOwner, false)) {
            Tile wormholeTile = game.getTileByPosition(adjacentPosition);
            if (wormholeTile == null || wormholeTile.getWormholes(game).isEmpty()) {
                continue;
            }

            for (Player target : game.getRealPlayersExcludingThis(monumentOwner)) {
                if (FoWHelper.playerHasShipsInSystem(target, wormholeTile)) {
                    hasEligibleTarget = true;
                    break;
                }
            }
        }

        if (hasEligibleTarget) {
            List<Button> buttons = List.of(
                    Buttons.green(
                            monumentOwner.factionButtonChecker() + "revenanceCircuitProduceHits_" + tile.getPosition(),
                            "Produce Hits"),
                    Buttons.red("deleteButtons", "Decline"));

            MessageHelper.sendMessageToChannelWithButtons(
                    monumentOwner.getCorrectChannel(),
                    monumentOwner.getRepresentationNoPing()
                            + ", you may use **Revenance Circuit** to produce 1 hit against each player's ships "
                            + "in every adjacent system containing a wormhole.",
                    buttons);
        }
    }

    @ButtonHandler("revenanceCircuitProduceHits_")
    public static void resolveRevenanceCircuitHits(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String activeSystemPosition = buttonID.replace("revenanceCircuitProduceHits_", "");
        Tile monumentTile = game.getTileByPosition(activeSystemPosition);

        if (!game.isMonumentsMode()
                || monumentTile == null
                || !player.hasUnit("creuss_monument")
                || !ButtonHelper.doesPlayerHaveUnitHere("creuss_monument", player, monumentTile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        for (String adjacentPosition :
                FoWHelper.getAdjacentTilesAndNotThisTile(game, monumentTile.getPosition(), player, false)) {
            Tile wormholeTile = game.getTileByPosition(adjacentPosition);
            if (wormholeTile == null || wormholeTile.getWormholes(game).isEmpty()) {
                continue;
            }

            for (Player target : game.getRealPlayersExcludingThis(player)) {
                if (FoWHelper.playerHasShipsInSystem(target, wormholeTile)) {
                    CombatRollService.sendSpaceAssignHitsButtons(event, game, target, wormholeTile, 1);
                    MessageHelper.sendMessageToChannel(
                            target.getCorrectChannel(),
                            target.getRepresentation() + ", **Revenance Circuit** produced 1 hit against your ships in "
                                    + wormholeTile.getRepresentationForButtons(game, target) + ".");
                }
            }
        }

        ButtonHelper.deleteMessage(event);
    }
}
