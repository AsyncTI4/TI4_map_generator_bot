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
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.game.MonumentsService;
import ti4.service.unit.AddUnitService;

public class MonumentsButtonHandler {
    private static final String PLACE_UNIT_ON_MONUMENT_PLANET = "placeUnitOnMonumentPlanet_";
    private static final String PLACE_ARBOREC_MONUMENT_INFANTRY = "placeArborecMonumentInfantry_";
    private static final String PLACE_JOLNAR_MONUMENT_INFANTRY = "jolnarMonumentInfantry_";
    private static final String USE_L1_MONUMENT = "useL1Monument";
    private static final String L1_TARGET = "l1MonumentTarget_";
    private static final String L1_MOVE_CC = "l1MonumentMoveCC_";

    // [0.0.2]
    public static Button getL1MonumentButton(Player player) {
        return Buttons.gray(player.factionButtonChecker() + USE_L1_MONUMENT, "Use [0.0.2]", FactionEmojis.L1Z1X);
    }

    @ButtonHandler(USE_L1_MONUMENT)
    public static void useL1Monument(ButtonInteractionEvent event, Game game, Player player) {
        if (!MonumentsService.isMonumentReady(game, player, "l1z1x_monument")) {
            return;
        }
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "l1z1x_monument");
        if (monumentTile == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Your [0.0.2] is not on the board.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player)) {
            if (tile == monumentTile || game.getRealPlayers().stream().noneMatch(tile::hasPlayerCC)) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + L1_TARGET + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "No adjacent systems contain a command token.");
            return;
        }
        if (!MonumentsService.exhaustMonument(game, player, "l1z1x_monument")) {
            return;
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " exhausted **[0.0.2]**. Choose an adjacent system from which to move a command token.",
                buttons);
    }

    @ButtonHandler(L1_TARGET)
    public static void selectL1MonumentTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.isMonumentsMode()
                || !player.hasUnit("l1z1x_monument")
                || !MonumentsService.hasMonumentOnBoard(game, player)) {
            return;
        }
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "l1z1x_monument");
        Tile targetTile = game.getTileByPosition(buttonID.replace(L1_TARGET, ""));
        if (monumentTile == null
                || targetTile == null
                || targetTile == monumentTile
                || !MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player)
                        .contains(targetTile)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That system is no longer adjacent to your [0.0.2].");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (targetTile.hasPlayerCC(target)) {
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + L1_MOVE_CC + targetTile.getPosition() + "|" + target.getColor(),
                        target.getRepresentationNoPing(),
                        target.getFactionEmoji()));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That system no longer contains a command token.");
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose whose command token to move into your [0.0.2] system.",
                buttons);
    }

    @ButtonHandler(L1_MOVE_CC)
    public static void moveL1MonumentCommandToken(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.replace(L1_MOVE_CC, "").split("\\|", 2);
        if (payload.length != 2
                || !game.isMonumentsMode()
                || !player.hasUnit("l1z1x_monument")
                || !MonumentsService.hasMonumentOnBoard(game, player)) {
            return;
        }
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "l1z1x_monument");
        Tile targetTile = game.getTileByPosition(payload[0]);
        Player tokenOwner = game.getPlayerFromColorOrFaction(payload[1]);
        if (monumentTile == null
                || targetTile == null
                || tokenOwner == null
                || targetTile == monumentTile
                || !MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player)
                        .contains(targetTile)
                || !targetTile.hasPlayerCC(tokenOwner)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That command token can no longer be moved.");
            return;
        }
        targetTile.removeCC(Mapper.getCCID(tokenOwner.getColor()));
        CommandCounterHelper.addCC(event, tokenOwner, monumentTile);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " moved " + tokenOwner.getRepresentationNoPing()
                        + "'s command token from " + targetTile.getRepresentationForButtons(game, player) + " to "
                        + monumentTile.getRepresentationForButtons(game, player) + " with **[0.0.2]**.");
        ButtonHelper.deleteMessage(event);
    }

    // Calm Seas Sanatorium
    public static void offerJolNarMonumentInfantry(Game game, Player player) {
        if (!game.isMonumentsMode()
                || !player.hasUnit("jolnar_monument")
                || !MonumentsService.hasMonumentOnBoard(game, player)) {
            return;
        }
        UnitKey monumentKey = Units.getUnitKey(UnitType.Monument, player.getColor());
        Tile monumentTile = game.getTileMap().values().stream()
                .filter(tile -> ButtonHelper.doesPlayerHaveUnitHere("jolnar_monument", player, tile))
                .findFirst()
                .orElse(null);
        if (monumentTile == null) {
            return;
        }
        Planet monumentPlanet = monumentTile.getPlanetUnitHolders().stream()
                .filter(planet -> planet.getUnitCount(monumentKey) > 0)
                .findFirst()
                .orElse(null);
        if (monumentPlanet == null || !player.getPlanets().contains(monumentPlanet.getName())) {
            return;
        }
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + PLACE_JOLNAR_MONUMENT_INFANTRY + "1|"
                                + monumentPlanet.getName(),
                        "Place 1 Infantry"),
                Buttons.green(
                        player.factionButtonChecker() + PLACE_JOLNAR_MONUMENT_INFANTRY + "2|"
                                + monumentPlanet.getName(),
                        "Place 2 Infantry"),
                Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", **Calm Seas Sanatorium** triggered. Place up to 2 infantry on "
                        + monumentPlanet.getRepresentation(game) + ".",
                buttons);
    }

    @ButtonHandler(PLACE_JOLNAR_MONUMENT_INFANTRY)
    public static void placeJolNarMonumentInfantry(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.replace(PLACE_JOLNAR_MONUMENT_INFANTRY, "").split("\\|", 2);
        if (payload.length != 2 || !game.isMonumentsMode()) {
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(payload[0]);
        } catch (NumberFormatException e) {
            return;
        }
        if (amount < 1
                || amount > 2
                || !player.hasUnit("jolnar_monument")
                || !MonumentsService.hasMonumentOnBoard(game, player)) {
            return;
        }
        Tile tile = game.getTileFromPlanet(payload[1]);
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(payload[1]);
        UnitKey monumentKey = Units.getUnitKey(UnitType.Monument, player.getColor());
        if (planet == null || !player.getPlanets().contains(planet.getName()) || planet.getUnitCount(monumentKey) < 1) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That planet is no longer eligible.");
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " infantry " + planet.getName());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed " + amount + " infantry on "
                        + planet.getRepresentation(game) + " with **Calm Seas Sanatorium**.");
        ButtonHelper.deleteMessage(event);
    }

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
                        && MonumentsService.hasMonumentOnBoard(game, monumentOwner)
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
                            && MonumentsService.hasMonumentOnBoard(game, monumentOwner)
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
                                && MonumentsService.hasMonumentOnBoard(game, monumentOwner)
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
        if (!game.isMonumentsMode()
                || !player.hasUnit("arborec_monument")
                || !MonumentsService.hasMonumentOnBoard(game, player)) {
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
                || !MonumentsService.hasMonumentOnBoard(game, player)
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
