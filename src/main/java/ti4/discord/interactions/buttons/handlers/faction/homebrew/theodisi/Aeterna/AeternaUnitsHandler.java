package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Aeterna;

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
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class AeternaUnitsHandler {
    private static final String MAUSOLEUM_DESTROY = "useMausoleumAbility_";
    private static final String CHOOSE_MECH = "chooseMausoleumToDestroy_";
    private static final String GRAVEYARD_COMM = "aeternaGraveyardComm_";
    private static final String GRAVEYARD_PRODUCE = "aeternaGraveyardProduce_";
    private static final String GRAVEYARD_II = "aeternaGraveyardII_";
    private static final String GRAVEYARD_DECLINE = "aeternaGraveyardDecline_";
    private static final String GRAVEYARD_PRODUCE_LOCATION = "aeternaGraveyardProduceLocation_";
    private static final String SHOW_CRYPT_CAPACITY = "showCryptCapacity";
    private static final String CRYPT_TOKEN_COUNT = "aeternaCryptControlTokens_";
    private static final String CRYPT_USED_ACTION = "aeternaCryptUsedAction_";
    private static final String GRAVEYARD_USED_ACTION = "aeternaGraveyardUsedAction_";

    public static void getMausoleumButton(
            Player player, Player opponent, Tile tile, UnitHolder holder, List<Button> combatButtons) {
        if (player == null
                || opponent == null
                || tile == null
                || holder == null
                || !player.hasUnit("aeterna_mech")
                || holder.getUnitCount(UnitType.Mech, player) < 1
                || holder.getUnitCount(UnitType.Infantry, opponent) < 1) {
            return;
        }

        combatButtons.add(Buttons.red(
                player.factionButtonChecker() + MAUSOLEUM_DESTROY + tile.getPosition() + "|" + holder.getName() + "|"
                        + opponent.getFaction(),
                "Use Walking Mausoleum",
                FactionEmojis.aeterna));
    }

    @ButtonHandler(MAUSOLEUM_DESTROY)
    public static void chooseMausoleumToDestroy(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(MAUSOLEUM_DESTROY.length()).split("\\|", 3);
        if (game == null || player == null || payload.length != 3 || !player.hasUnit("aeterna_mech")) {
            return;
        }

        Tile tile = game.getTileByPosition(payload[0]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolderFromPlanet(payload[1]);
        Player opponent = game.getPlayerFromColorOrFaction(payload[2]);
        if (holder == null
                || opponent == null
                || opponent.equals(player)
                || holder.getUnitCount(UnitType.Infantry, opponent) < 1) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey : holder.getUnitKeysForPlayer(player)) {
            if (unitKey.unitType() != UnitType.Mech) {
                continue;
            }

            for (UnitState state : holder.getNonZeroUnitStates(unitKey)) {
                int count = holder.getUnitCountForState(unitKey, state);
                String stateText =
                        switch (state) {
                            case dmg -> "damaged ";
                            case glv -> "galvanized ";
                            case dmg_glv -> "damaged galvanized ";
                            default -> "";
                        };
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + CHOOSE_MECH + tile.getPosition() + "|" + holder.getName() + "|"
                                + opponent.getFaction() + "|" + unitKey.asyncID() + "|" + state,
                        "Destroy 1 " + stateText + "Mech (" + count + ")",
                        unitKey.unitEmoji()));
            }
        }

        if (buttons.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose a Walking Mausoleum to destroy.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(CHOOSE_MECH)
    public static void resolveMausoleum(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(CHOOSE_MECH.length()).split("\\|", 5);
        if (game == null || player == null || payload.length != 5 || !player.hasUnit("aeterna_mech")) {
            return;
        }

        Tile tile = game.getTileByPosition(payload[0]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolderFromPlanet(payload[1]);
        Player opponent = game.getPlayerFromColorOrFaction(payload[2]);
        UnitKey mechKey = holder == null
                ? null
                : holder.getUnitKeysForPlayer(player).stream()
                        .filter(unitKey -> unitKey.unitType() == UnitType.Mech)
                        .filter(unitKey -> unitKey.asyncID().equals(payload[3]))
                        .findFirst()
                        .orElse(null);
        UnitState state = Units.findUnitState(payload[4]);
        if (holder == null
                || opponent == null
                || opponent.equals(player)
                || mechKey == null
                || state == null
                || holder.getUnitCountForState(mechKey, state) < 1
                || holder.getUnitCount(UnitType.Infantry, opponent) < 1) {
            return;
        }

        DestroyUnitService.destroyUnit(event, tile, game, new ParsedUnit(mechKey, 1, holder.getName()), true, state);

        int infantryDestroyed = 0;
        for (UnitKey unitKey : new ArrayList<>(holder.getUnitKeysForPlayer(opponent))) {
            if (unitKey.unitType() != UnitType.Infantry || infantryDestroyed >= 2) {
                continue;
            }
            int amount = Math.min(2 - infantryDestroyed, holder.getUnitCount(unitKey));
            DestroyUnitService.destroyUnit(event, tile, game, new ParsedUnit(unitKey, amount, holder.getName()), true);
            infantryDestroyed += amount;
        }

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " destroyed a Walking Mausoleum (Aeterna mech) and "
                        + infantryDestroyed + " of " + opponent.getRepresentationNoPing() + "'s infantry.");
        ButtonHelper.deleteMessage(event);
    }

    public static void offerGraveyardEffectsForDestroyedUnits(
            net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent event,
            Game game,
            List<RemovedUnit> units) {
        if (game == null || units.isEmpty()) {
            return;
        }

        String actionKey = getCurrentActionKey(game);
        for (Player player : game.getRealPlayers()) {
            boolean graveyardII = player.hasUnit("aeterna_spacedock2");
            if (!graveyardII && !player.hasUnit("aeterna_spacedock")) {
                continue;
            }

            for (Tile dockTile : game.getTileMap().values()) {
                for (UnitHolder dockHolder : dockTile.getUnitHolders().values()) {
                    if (dockHolder.getUnitCount(UnitType.Spacedock, player) < 1) {
                        continue;
                    }

                    String dockKey = player.getFaction() + "_" + dockTile.getPosition() + "_" + dockHolder.getName();
                    if (actionKey.equals(game.getStoredValue(GRAVEYARD_USED_ACTION + dockKey))) {
                        continue;
                    }

                    for (RemovedUnit destroyed : units) {
                        if (!isInOrAdjacentSystem(game, player, dockTile, destroyed.tile())) {
                            continue;
                        }

                        UnitType unitType = destroyed.unitKey().unitType();
                        String payload =
                                dockTile.getPosition() + "|" + dockHolder.getName() + "|" + unitType + "|" + actionKey;
                        List<Button> buttons = new ArrayList<>();
                        if (graveyardII) {
                            buttons.add(Buttons.green(
                                    player.factionButtonChecker() + GRAVEYARD_II + payload,
                                    "Gain 1 Commodity and Produce 1 " + unitType.humanReadableName(),
                                    FactionEmojis.aeterna));
                        } else {
                            buttons.add(Buttons.blue(
                                    player.factionButtonChecker() + GRAVEYARD_COMM + payload,
                                    "Gain 1 Commodity",
                                    FactionEmojis.aeterna));
                            if (canProduceFromGraveyard(game, player, dockTile, dockHolder, unitType)) {
                                buttons.add(Buttons.green(
                                        player.factionButtonChecker() + GRAVEYARD_PRODUCE + payload,
                                        "Produce 1 " + unitType.humanReadableName(),
                                        FactionEmojis.aeterna));
                            }
                        }
                        buttons.add(
                                Buttons.red(player.factionButtonChecker() + GRAVEYARD_DECLINE + payload, "Decline"));
                        MessageHelper.sendMessageToChannelWithButtons(
                                player.getCorrectChannel(),
                                player.getRepresentationNoPing() + ", use _Graveyard " + (graveyardII ? "II" : "I")
                                        + "_ at " + getGraveyardLocation(game, player, dockTile, dockHolder)
                                        + " for the destroyed " + unitType.humanReadableName() + ".",
                                buttons);
                    }
                }
            }
        }
    }

    @ButtonHandler(GRAVEYARD_COMM)
    public static void resolveGraveyardCommodity(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!resolveGraveyardIAction(event, game, player, buttonID, GRAVEYARD_COMM, false)) {
            return;
        }
        ButtonHelperStats.gainComms(event, game, player, 1, false);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(GRAVEYARD_PRODUCE)
    public static void resolveGraveyardProduction(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!resolveGraveyardIAction(event, game, player, buttonID, GRAVEYARD_PRODUCE, true)) {
            return;
        }
    }

    @ButtonHandler(GRAVEYARD_II)
    public static void resolveGraveyardII(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(GRAVEYARD_II.length()).split("\\|", 4);
        if (!resolveGraveyardAction(game, player, payload, true, false)) {
            return;
        }

        Tile dockTile = game.getTileByPosition(payload[0]);
        UnitHolder dockHolder = dockTile.getUnitHolders().get(payload[1]);
        UnitType unitType = Units.findUnitType(payload[2]);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " activated _Graveyard II_ at "
                        + getGraveyardLocation(game, player, dockTile, dockHolder) + ".");
        ButtonHelperStats.gainComms(event, game, player, 1, false);
        produceFromGraveyard(event, game, player, dockTile, dockHolder, unitType);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(GRAVEYARD_DECLINE)
    public static void declineGraveyard(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(GRAVEYARD_DECLINE.length()).split("\\|", 4);
        if (game == null || player == null || payload.length != 4) {
            return;
        }
        ButtonHelper.deleteMessage(event);
    }

    private static boolean resolveGraveyardIAction(
            ButtonInteractionEvent event, Game game, Player player, String buttonID, String prefix, boolean produce) {
        String[] payload = buttonID.substring(prefix.length()).split("\\|", 4);
        if (!resolveGraveyardAction(game, player, payload, false, produce)) {
            return false;
        }

        Tile dockTile = game.getTileByPosition(payload[0]);
        UnitHolder dockHolder = dockTile.getUnitHolders().get(payload[1]);
        UnitType unitType = Units.findUnitType(payload[2]);
        if (produce) {
            produceFromGraveyard(event, game, player, dockTile, dockHolder, unitType);
            ButtonHelper.deleteMessage(event);
        }
        return true;
    }

    private static boolean resolveGraveyardAction(
            Game game, Player player, String[] payload, boolean graveyardII, boolean produce) {
        if (game == null
                || player == null
                || payload.length != 4
                || (graveyardII ? !player.hasUnit("aeterna_spacedock2") : !player.hasUnit("aeterna_spacedock"))
                || (!graveyardII && player.hasUnit("aeterna_spacedock2"))) {
            return false;
        }

        Tile dockTile = game.getTileByPosition(payload[0]);
        UnitHolder dockHolder =
                dockTile == null ? null : dockTile.getUnitHolders().get(payload[1]);
        UnitType unitType = Units.findUnitType(payload[2]);
        String dockKey = player.getFaction() + "_" + payload[0] + "_" + payload[1];
        if (dockHolder == null
                || dockHolder.getUnitCount(UnitType.Spacedock, player) < 1
                || unitType == null
                || payload[3].equals(game.getStoredValue(GRAVEYARD_USED_ACTION + dockKey))
                || (produce && !canProduceFromGraveyard(game, player, dockTile, dockHolder, unitType))) {
            return false;
        }

        game.setStoredValue(GRAVEYARD_USED_ACTION + dockKey, payload[3]);
        return true;
    }

    private static boolean canProduceFromGraveyard(
            Game game, Player player, Tile dockTile, UnitHolder dockHolder, UnitType unitType) {
        UnitModel unit = getProducedUnit(player, unitType);
        if (unit == null
                || player.getUnitCap(unit.getAsyncId())
                        <= ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, unit.getAsyncId())) {
            return false;
        }
        return unit.getIsShip()
                || (!Constants.SPACE.equals(dockHolder.getName())
                        && player.getPlanets().contains(dockHolder.getName()));
    }

    private static void produceFromGraveyard(
            net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Tile dockTile,
            UnitHolder dockHolder,
            UnitType unitType) {
        if (!canProduceFromGraveyard(game, player, dockTile, dockHolder, unitType)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " could not produce a " + unitType.humanReadableName()
                            + " with _Graveyard_ because none are available.");
            return;
        }

        UnitModel unit = getProducedUnit(player, unitType);
        if (unit.getIsGroundForce()) {
            String payload = dockTile.getPosition() + "|" + dockHolder.getName() + "|" + unitType;
            List<Button> buttons = List.of(
                    Buttons.green(
                            player.factionButtonChecker() + GRAVEYARD_PRODUCE_LOCATION + payload + "|"
                                    + Constants.SPACE,
                            "Produce 1 " + unit.getName() + " in Space",
                            FactionEmojis.aeterna),
                    Buttons.green(
                            player.factionButtonChecker() + GRAVEYARD_PRODUCE_LOCATION + payload + "|"
                                    + dockHolder.getName(),
                            "Produce 1 " + unit.getName() + " on " + dockHolder.getName(),
                            FactionEmojis.aeterna));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + ", please choose where _Graveyard_ at "
                            + getGraveyardLocation(game, player, dockTile, dockHolder) + " produces the "
                            + unit.getName() + ".",
                    buttons);
            return;
        }

        String location = unit.getIsShip() ? Constants.SPACE : dockHolder.getName();
        AddUnitService.addUnits(event, dockTile, game, player.getColor(), "1 " + unit.getAsyncId() + " " + location);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " produced 1 " + unit.getName() + " with _Graveyard_ at "
                        + getGraveyardLocation(game, player, dockTile, dockHolder) + ".");
        sendGraveyardPaymentPrompt(game, player, unit, dockTile, dockHolder);
    }

    @ButtonHandler(GRAVEYARD_PRODUCE_LOCATION)
    public static void resolveGraveyardProductionLocation(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(GRAVEYARD_PRODUCE_LOCATION.length()).split("\\|", 4);
        if (game == null || player == null || payload.length != 4) {
            return;
        }

        Tile dockTile = game.getTileByPosition(payload[0]);
        UnitHolder dockHolder =
                dockTile == null ? null : dockTile.getUnitHolders().get(payload[1]);
        UnitType unitType = Units.findUnitType(payload[2]);
        UnitModel unit = unitType == null ? null : getProducedUnit(player, unitType);
        if (dockHolder == null
                || dockHolder.getUnitCount(UnitType.Spacedock, player) < 1
                || unit == null
                || !unit.getIsGroundForce()
                || !canProduceFromGraveyard(game, player, dockTile, dockHolder, unitType)
                || (!Constants.SPACE.equals(payload[3]) && !dockHolder.getName().equals(payload[3]))) {
            return;
        }

        AddUnitService.addUnits(event, dockTile, game, player.getColor(), "1 " + unit.getAsyncId() + " " + payload[3]);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " produced 1 " + unit.getName() + " with _Graveyard_ at "
                        + getGraveyardLocation(game, player, dockTile, dockHolder) + ".");
        sendGraveyardPaymentPrompt(game, player, unit, dockTile, dockHolder);
        ButtonHelper.deleteMessage(event);
    }

    private static void sendGraveyardPaymentPrompt(
            Game game, Player player, UnitModel unit, Tile dockTile, UnitHolder dockHolder) {
        int cost = (int) Math.ceil(unit.getCost());
        game.setStoredValue("producedUnitCostFor" + player.getFaction(), Integer.toString(cost));
        player.setTotalExpenses(player.getTotalExpenses() + cost);

        List<Button> paymentButtons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, "res"));
        paymentButtons.add(Buttons.red("deleteButtons_graveyard", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", please choose planets to exhaust to pay " + cost
                        + " for the " + unit.getName() + " produced by _Graveyard_ at "
                        + getGraveyardLocation(game, player, dockTile, dockHolder) + ".",
                paymentButtons);
    }

    private static String getGraveyardLocation(Game game, Player player, Tile dockTile, UnitHolder dockHolder) {
        return dockHolder.getRepresentation(game) + " in " + dockTile.getRepresentationForButtons(game, player);
    }

    private static UnitModel getProducedUnit(Player player, UnitType unitType) {
        return player.getUnitsByAsyncID(unitType.getValue()).stream()
                .findFirst()
                .orElse(null);
    }

    public static void addCryptControlTokenForDestroyedFighters(Game game, List<RemovedUnit> units) {
        if (game == null || units.stream().noneMatch(unit -> unit.unitKey().unitType() == UnitType.Fighter)) {
            return;
        }

        String actionKey = getCurrentActionKey(game);
        for (Player player : game.getRealPlayers()) {
            if (!player.hasUnit("aeterna_flagship")
                    || actionKey.equals(game.getStoredValue(CRYPT_USED_ACTION + player.getFaction()))
                    || units.stream()
                            .filter(unit -> unit.unitKey().unitType() == UnitType.Fighter)
                            .noneMatch(unit -> hasCryptInOrAdjacentSystem(game, player, unit.tile()))) {
                continue;
            }

            int tokenCount = getCryptTokenCount(game, player) + 1;
            game.setStoredValue(CRYPT_TOKEN_COUNT + player.getFaction(), Integer.toString(tokenCount));
            game.setStoredValue(CRYPT_USED_ACTION + player.getFaction(), actionKey);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + " added a control token to _The Crypt_ after a fighter was destroyed. It now has "
                            + tokenCount + " control token" + (tokenCount <= 1 ? "" : "s ") + "on it and a capacity of "
                            + getCryptEffectiveCapacity(game, player) + ".");
        }
    }

    public static Button getCryptCapacityInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + SHOW_CRYPT_CAPACITY, "Check The Crypt Capacity", FactionEmojis.aeterna);
    }

    @ButtonHandler(SHOW_CRYPT_CAPACITY)
    public static void showCryptCapacity(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasUnit("aeterna_flagship")) {
            return;
        }
        int tokenCount = getCryptTokenCount(game, player);
        MessageHelper.sendMessageToEventChannel(
                event,
                "_The Crypt_ has " + tokenCount + " control token" + (tokenCount == 1 ? "" : "s")
                        + " and effective capacity " + getCryptEffectiveCapacity(game, player) + ".");
    }

    public static int getCryptTokenCount(Game game, Player player) {
        if (game == null || player == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(game.getStoredValue(CRYPT_TOKEN_COUNT + player.getFaction())));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int getCryptEffectiveCapacity(Game game, Player player) {
        return 2 + 2 * getCryptTokenCount(game, player);
    }

    public static void clearCryptActionState(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }
        game.removeStoredValue(CRYPT_USED_ACTION + player.getFaction());
    }

    public static void clearCryptActionState(Game game) {
        if (game == null) {
            return;
        }
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(CRYPT_USED_ACTION))
                .toList()
                .forEach(game::removeStoredValue);
    }

    public static void clearGraveyardActionState(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(GRAVEYARD_USED_ACTION + player.getFaction() + "_"))
                .toList()
                .forEach(game::removeStoredValue);
    }

    public static void clearGraveyardActionState(Game game) {
        if (game == null) {
            return;
        }
        game.getStoredValueMap().keySet().stream()
                .filter(key -> key.startsWith(GRAVEYARD_USED_ACTION))
                .toList()
                .forEach(game::removeStoredValue);
    }

    private static String getCurrentActionKey(Game game) {
        Player activePlayer = game.getActivePlayer();
        return game.getRound() + "_" + game.getPhaseOfGame() + "_"
                + (activePlayer == null
                        ? "none"
                        : activePlayer.getFaction() + "_" + activePlayer.getInRoundTurnCount());
    }

    private static boolean hasCryptInOrAdjacentSystem(Game game, Player player, Tile tile) {
        if (tile == null) {
            return false;
        }
        if (ButtonHelper.doesPlayerHaveFSHere("aeterna_flagship", player, tile)) {
            return true;
        }
        return FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false).stream()
                .map(game::getTileByPosition)
                .anyMatch(adjacentTile -> adjacentTile != null
                        && ButtonHelper.doesPlayerHaveFSHere("aeterna_flagship", player, adjacentTile));
    }

    private static boolean isInOrAdjacentSystem(Game game, Player player, Tile sourceTile, Tile targetTile) {
        if (sourceTile == null || targetTile == null) {
            return false;
        }
        return sourceTile.getPosition().equals(targetTile.getPosition())
                || FoWHelper.getAdjacentTilesAndNotThisTile(game, sourceTile.getPosition(), player, false)
                        .contains(targetTile.getPosition());
    }
}
