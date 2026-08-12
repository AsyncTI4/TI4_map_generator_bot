package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Xytheris;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.CheckDistanceHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.thundersedge.TeHelperUnits;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class XytherisAbilityHandler {
    private static final String STING_OF_THE_HIVE = "sting_of_the_hive";
    private static final String MINE_TOKEN = "token_theodisi_mine.png";
    private static final String MINE_COUNT = "stingOfTheHiveMines_";
    private static final int MAX_MINE_TOKENS = 6;
    private static final String STING_ROLL = "stingOfTheHiveRoll_";
    private static final String STING_ROLL_COUNTER = "stingOfTheHiveRollCounter_";
    private static final String REPLACE_HIT = "stingHiveReplaceHit_";
    private static final String MOVE_MINE = "stingHiveMoveMine_";
    private static final String MOVE_MINE_FROM = "stingHiveMoveMineFrom_";
    private static final String RESOLVE_MINES = "resolveStingHiveMines_";
    private static final String PRODUCE_HITS = "stingHiveProduceHits_";
    private static final String GAIN_COMMODITY = "stingHiveGainCommodity_";

    public Optional<Pair<UnitModel, UnitHolder>> getBestHiveEchoUnit(
            Tile tile, Player player, CombatRollType rollType) {
        Game game = player.getGame();
        if (game == null
                || !player.hasAbility("hive_echo")
                || (rollType != CombatRollType.SpaceCannonOffence
                        && !FoWHelper.playerHasActualShipsInSystem(player, tile))) {
            return Optional.empty();
        }

        return CheckDistanceHelper.getTileDistances(game, player, tile.getPosition(), 2, true).entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() >= 1 && entry.getValue() <= 2)
                .map(Map.Entry::getKey)
                .map(game::getTileByPosition)
                .filter(remoteTile -> remoteTile != null
                        && !TeHelperUnits.affectedByQuietus(game, player, remoteTile)
                        && !remoteTile.isScar(game))
                .flatMap(remoteTile -> remoteTile.getUnitHolders().values().stream())
                .flatMap(remoteHolder -> remoteHolder.getUnitKeys().stream()
                        .filter(player::unitBelongsToPlayer)
                        .filter(unitKey -> remoteHolder.getUnitCount(unitKey) > 0)
                        .<Pair<UnitModel, UnitHolder>>map(unitKey -> new ImmutablePair<>(
                                player.getPriorityUnitByAsyncID(unitKey.asyncID(), remoteHolder), remoteHolder)))
                .filter(candidate -> candidate.getLeft() != null
                        && candidate.getLeft().getCombatDieCountForAbility(rollType, player) > 0)
                .max(Comparator.comparingDouble(
                        candidate -> candidate.getLeft().getCombatDieCountForAbility(rollType, player)
                                * (10 - candidate.getLeft().getCombatDieHitsOnForAbility(rollType, player))
                                / 10.0d));
    }

    public static List<Button> getStingOfTheHiveHitReplacementButtons(
            Game game, Player player, Tile tile, CombatRollType rollType, Player target, int hits) {
        if (game == null
                || player == null
                || tile == null
                || target == null
                || hits < 1
                || rollType == CombatRollType.combatround
                || !player.hasAbility(STING_OF_THE_HIVE)
                || !tile.getPosition().equals(game.getActiveSystem())) {
            return List.of();
        }
        String roll = game.getStoredValue(STING_ROLL + player.getFaction());
        if (!roll.startsWith("available|")) {
            return List.of();
        }
        String payload = tile.getPosition() + "|" + rollType.name() + "|" + target.getFaction() + "|" + hits + "|"
                + roll.substring("available|".length());
        List<Button> buttons = new ArrayList<>();
        if (getAvailableStingOfTheHiveMines(game) > 0) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + REPLACE_HIT + payload, "Place Mine", FactionEmojis.xytheris));
        }
        if (game.getTileMap().values().stream()
                .anyMatch(source -> source != tile && getStingOfTheHiveMineCount(game, source) > 0)) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + MOVE_MINE + payload, "Move Mine", FactionEmojis.xytheris));
        }
        return buttons;
    }

    public static void beginStingOfTheHiveRoll(Game game, Player player, Tile tile, CombatRollType rollType, int hits) {
        if (game == null
                || player == null
                || tile == null
                || hits < 1
                || rollType == CombatRollType.combatround
                || !player.hasAbility(STING_OF_THE_HIVE)
                || !tile.getPosition().equals(game.getActiveSystem())) {
            return;
        }
        int counter;
        try {
            counter = Integer.parseInt(game.getStoredValue(STING_ROLL_COUNTER + player.getFaction()));
        } catch (NumberFormatException e) {
            counter = 0;
        }
        counter++;
        game.setStoredValue(STING_ROLL_COUNTER + player.getFaction(), Integer.toString(counter));
        game.setStoredValue(STING_ROLL + player.getFaction(), "available|" + counter);
    }

    @ButtonHandler(REPLACE_HIT)
    public static void replaceHitWithMine(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(REPLACE_HIT.length()).split("\\|", 5);
        Tile tile = values.length == 5 ? game.getTileByPosition(values[0]) : null;
        CombatRollType rollType;
        try {
            rollType = values.length == 5 ? CombatRollType.valueOf(values[1]) : null;
        } catch (IllegalArgumentException e) {
            rollType = null;
        }
        Player target = values.length == 5 ? game.getPlayerFromColorOrFaction(values[2]) : null;
        int hits;
        try {
            hits = values.length == 5 ? Integer.parseInt(values[3]) : 0;
        } catch (NumberFormatException e) {
            hits = 0;
        }
        if (tile == null
                || target == null
                || rollType == null
                || hits < 1
                || !game.getStoredValue(STING_ROLL + player.getFaction()).equals("available|" + values[4])
                || !tile.getPosition().equals(game.getActiveSystem())
                || !player.hasAbility(STING_OF_THE_HIVE)
                || getAvailableStingOfTheHiveMines(game) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        boolean activeSystemHadNoMines = getStingOfTheHiveMineCount(game, tile) == 0;
        addMine(game, tile);
        resolveStingOfTheHiveHitReplacement(
                event, game, player, values, tile, rollType, target, hits, "placed", activeSystemHadNoMines);
    }

    @ButtonHandler(MOVE_MINE)
    public static void chooseStingOfTheHiveMineToMove(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(MOVE_MINE.length()).split("\\|", 5);
        Tile destination = values.length == 5 ? game.getTileByPosition(values[0]) : null;
        if (destination == null
                || !destination.getPosition().equals(game.getActiveSystem())
                || !player.hasAbility(STING_OF_THE_HIVE)
                || !game.getStoredValue(STING_ROLL + player.getFaction()).equals("available|" + values[4])) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = game.getTileMap().values().stream()
                .filter(source -> source != destination && getStingOfTheHiveMineCount(game, source) > 0)
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(source -> Buttons.gray(
                        player.factionButtonChecker() + MOVE_MINE_FROM + source.getPosition() + "|"
                                + String.join("|", values),
                        "Move Mine From " + source.getRepresentationForButtons(game, player),
                        FactionEmojis.xytheris))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose the system from which to move a mine token.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(MOVE_MINE_FROM)
    public static void moveStingOfTheHiveMine(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(MOVE_MINE_FROM.length()).split("\\|", 6);
        Tile source = values.length == 6 ? game.getTileByPosition(values[0]) : null;
        Tile destination = values.length == 6 ? game.getTileByPosition(values[1]) : null;
        CombatRollType rollType;
        try {
            rollType = values.length == 6 ? CombatRollType.valueOf(values[2]) : null;
        } catch (IllegalArgumentException e) {
            rollType = null;
        }
        Player target = values.length == 6 ? game.getPlayerFromColorOrFaction(values[3]) : null;
        int hits;
        try {
            hits = values.length == 6 ? Integer.parseInt(values[4]) : 0;
        } catch (NumberFormatException e) {
            hits = 0;
        }
        if (source == null
                || destination == null
                || source == destination
                || target == null
                || rollType == null
                || hits < 1
                || !destination.getPosition().equals(game.getActiveSystem())
                || !player.hasAbility(STING_OF_THE_HIVE)
                || !game.getStoredValue(STING_ROLL + player.getFaction()).equals("available|" + values[5])
                || getStingOfTheHiveMineCount(game, source) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        boolean activeSystemHadNoMines = getStingOfTheHiveMineCount(game, destination) == 0;
        removeMines(game, source, 1);
        addMine(game, destination);
        resolveStingOfTheHiveHitReplacement(
                event,
                game,
                player,
                new String[] {values[1], values[2], values[3], values[4], values[5]},
                destination,
                rollType,
                target,
                hits,
                "moved",
                activeSystemHadNoMines);
    }

    private static void resolveStingOfTheHiveHitReplacement(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            String[] values,
            Tile tile,
            CombatRollType rollType,
            Player target,
            int hits,
            String action,
            boolean offerMineRemoval) {
        int mineCount = getStingOfTheHiveMineCount(game, tile);
        game.setStoredValue(STING_ROLL + player.getFaction(), "used|" + values[4]);
        int remainingHits = hits - 1;
        ButtonHelper.deleteMessage(event);
        if (remainingHits == 0) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " used **Sting of the Hive** to " + action + " a mine "
                            + ("placed".equals(action) ? "in " : "to ")
                            + tile.getRepresentation()
                            + " instead of the produced hit. No hits remain to assign.\n-# This system now contains "
                            + mineCount + " mine token" + (mineCount == 1 ? "." : "s.")
                            + " Multiple mine tokens use a single map marker with their count displayed on it.");
            if (offerMineRemoval) {
                offerStingOfTheHiveAfterMovement(event, game, tile);
            }
            return;
        }

        List<Button> assignmentButtons = new ArrayList<>();
        String message;
        if (rollType == CombatRollType.AFB) {
            String targetChecker =
                    target.isDummy() || target.isNpc() ? target.dummyPlayerSpoof() : target.factionButtonChecker();
            assignmentButtons.add(Buttons.green(
                    targetChecker + "autoAssignAFBHits_" + tile.getPosition() + "_" + remainingHits,
                    "Auto-Assign " + remainingHits + " Hit" + (remainingHits == 1 ? "" : "s")));
            if (!target.isDummy() && !target.isNpc()) {
                assignmentButtons.add(Buttons.red(
                        targetChecker + "getDamageButtons_" + tile.getPosition() + "_afb",
                        "Manually Assign " + remainingHits + " Hit" + (remainingHits == 1 ? "" : "s")));
                assignmentButtons.add(Buttons.gray(
                        targetChecker + "cancelAFBHits_" + tile.getPosition() + "_" + remainingHits, "Cancel a Hit"));
            }
            message = target.getRepresentation() + ", you may automatically assign "
                    + (remainingHits == 1 ? "the hit" : "the hits") + " from ANTI-FIGHTER BARRAGE.";
        } else if (rollType == CombatRollType.SpaceCannonOffence) {
            String targetChecker =
                    target.isDummy() || target.isNpc() ? target.dummyPlayerSpoof() : target.factionButtonChecker();
            assignmentButtons.add(Buttons.green(
                    targetChecker + "autoAssignSpaceCannonOffenceHits_" + tile.getPosition() + "_" + remainingHits,
                    "Auto-Assign " + remainingHits + " Hit" + (remainingHits == 1 ? "" : "s")));
            if (!target.isDummy() && !target.isNpc()) {
                assignmentButtons.add(Buttons.red(
                        "getDamageButtons_" + tile.getPosition() + "deleteThis_pds",
                        "Manually Assign " + remainingHits + " Hit" + (remainingHits == 1 ? "" : "s")));
                assignmentButtons.add(Buttons.gray(
                        targetChecker + "cancelPdsOffenseHits_" + tile.getPosition() + "_" + remainingHits,
                        "Cancel a Hit"));
            }
            message = target.getRepresentationNoPing() + ", you may automatically assign "
                    + (remainingHits == 1 ? "the hit" : "the hits") + " from SPACE CANNON OFFENCE.";
        } else if (rollType == CombatRollType.SpaceCannonDefence) {
            String targetChecker =
                    target.isDummy() || target.isNpc() ? target.dummyPlayerSpoof() : target.factionButtonChecker();
            assignmentButtons.add(Buttons.green(
                    targetChecker + "autoAssignSpaceHits_" + tile.getPosition() + "_" + remainingHits,
                    "Auto-Assign " + remainingHits + " Hit" + (remainingHits == 1 ? "" : "s")));
            if (!target.isDummy() && !target.isNpc()) {
                assignmentButtons.add(Buttons.red(
                        "getDamageButtons_" + tile.getPosition() + "deleteThis_spacecombat",
                        "Manually Assign " + remainingHits + " Hit" + (remainingHits == 1 ? "" : "s")));
                assignmentButtons.add(Buttons.gray(
                        targetChecker + "cancelSpaceHits_" + tile.getPosition() + "_" + remainingHits, "Cancel a Hit"));
            }
            message = target.getRepresentationNoPing() + ", you may automatically assign "
                    + (remainingHits == 1 ? "the hit" : "the hits") + " from SPACE CANNON DEFENCE.";
        } else {
            if (target.isDummy() || target.isNpc()) {
                UnitHolder bombardmentTarget =
                        game.getUnitHolderFromPlanet(game.getStoredValue("bombardmentTarget" + player.getFaction()));
                if (bombardmentTarget != null) {
                    assignmentButtons.add(Buttons.green(
                            target.dummyPlayerSpoof() + "autoAssignGroundHits_" + bombardmentTarget.getName() + "_"
                                    + remainingHits,
                            "Auto-Assign " + remainingHits + " Hit" + (remainingHits == 1 ? "" : "s")));
                }
            } else {
                assignmentButtons.add(Buttons.red(
                        "getDamageButtons_" + tile.getPosition() + "_bombardment",
                        "Assign " + remainingHits + " Hit" + (remainingHits == 1 ? "" : "s")));
            }
            message = target.getRepresentationUnfogged() + ", please assign the remaining " + remainingHits
                    + " BOMBARDMENT hit" + (remainingHits == 1 ? "" : "s") + ".";
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " used **Sting of the Hive** to " + action + " a mine "
                        + ("placed".equals(action) ? "in " : "to ")
                        + tile.getRepresentation()
                        + " instead of 1 produced hit.\n-# This system now contains "
                        + mineCount
                        + " mine token"
                        + (mineCount == 1 ? "." : "s.")
                        + " Multiple mine tokens use a single map marker with their count displayed on it.");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, assignmentButtons);
        if (offerMineRemoval) {
            offerStingOfTheHiveAfterMovement(event, game, tile);
        }
    }

    /** Offers the mine-token effect immediately after movement in the active system. */
    public static void offerStingOfTheHiveAfterMovement(ButtonInteractionEvent event, Game game, Tile tile) {
        if (game == null
                || tile == null
                || !tile.getPosition().equals(game.getActiveSystem())
                || getStingOfTheHiveMineCount(game, tile) < 1) {
            return;
        }

        for (Player player : game.getRealPlayers()) {
            if (!player.hasAbility(STING_OF_THE_HIVE)) {
                continue;
            }
            int mines = getStingOfTheHiveMineCount(game, tile);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", there "
                            + (mines == 1 ? "is **1 mine token**" : "are **" + mines + " mine tokens**")
                            + " in the active system. You may use **Sting of the Hive** to remove any number of them to gain that many commodities or produce that many hits on the active player's ships.",
                    List.of(
                            Buttons.green(
                                    player.factionButtonChecker() + RESOLVE_MINES + tile.getPosition(),
                                    "Use Sting of the Hive",
                                    FactionEmojis.xytheris),
                            Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline")));
        }
    }

    @ButtonHandler(RESOLVE_MINES)
    public static void chooseStingOfTheHiveMineEffect(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.substring(RESOLVE_MINES.length());
        int pageMarker = position.indexOf("_page");
        if (pageMarker >= 0) {
            position = position.substring(0, pageMarker);
        }
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !player.hasAbility(STING_OF_THE_HIVE) || getStingOfTheHiveMineCount(game, tile) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> hitButtons = new ArrayList<>();
        int mines = getStingOfTheHiveMineCount(game, tile);
        for (int amount = 1; amount <= mines; amount++) {
            hitButtons.add(Buttons.red(
                    player.factionButtonChecker() + PRODUCE_HITS + position + "|" + amount,
                    "Remove " + amount + " Mine" + (amount == 1 ? "" : "s") + ": Produce " + amount + " Hit"
                            + (amount == 1 ? "" : "s")));
        }

        String message = player.getRepresentationUnfogged()
                + ", choose how many mines to remove. The hits are produced against the active player's ships.";
        String buttonPrefix = player.factionButtonChecker() + RESOLVE_MINES + position + "_";
        List<Button> extraButtons = new ArrayList<>();
        int commoditiesToGain = Math.min(mines, Math.max(0, player.getCommoditiesTotal() - player.getCommodities()));
        for (int amount = 1; amount <= commoditiesToGain; amount++) {
            extraButtons.add(Buttons.green(
                    player.factionButtonChecker() + GAIN_COMMODITY + position + "|" + amount,
                    "Remove " + amount + " Mine" + (amount == 1 ? "" : "s") + ": Gain " + amount + " Commodit"
                            + (amount == 1 ? "y" : "ies")));
        }
        extraButtons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), hitButtons, extraButtons, message, buttonPrefix, buttonID)) {
            return;
        }
        List<Button> displayedButtons;
        if (hitButtons.size() + extraButtons.size() <= 25) {
            displayedButtons = new ArrayList<>(hitButtons);
            displayedButtons.addAll(extraButtons);
        } else {
            displayedButtons = NewStuffHelper.buttonPagination(hitButtons, extraButtons, buttonPrefix, 25, 0, false);
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, displayedButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(GAIN_COMMODITY)
    public static void gainCommodityFromMine(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(GAIN_COMMODITY.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        int amount;
        try {
            amount = payload.length == 2 ? Integer.parseInt(payload[1]) : 0;
        } catch (NumberFormatException e) {
            amount = 0;
        }
        if (tile == null
                || amount < 1
                || amount > player.getCommoditiesTotal() - player.getCommodities()
                || !player.hasAbility(STING_OF_THE_HIVE)
                || !removeMines(game, tile, amount)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int commoditiesBefore = player.getCommodities();
        player.gainCommodities(amount);
        int gained = player.getCommodities() - commoditiesBefore;
        ButtonHelperStats.afterGainCommsChecks(game, player, gained);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " used **Sting of the Hive** to remove " + amount + " mine"
                        + (amount == 1 ? "" : "s")
                        + " and gained " + gained + " commodit"
                        + (gained == 1 ? "y" : "ies") + ".");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PRODUCE_HITS)
    public static void produceHitsFromMines(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(PRODUCE_HITS.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        int hits;
        try {
            hits = payload.length == 2 ? Integer.parseInt(payload[1]) : 0;
        } catch (NumberFormatException e) {
            hits = 0;
        }
        Player activePlayer = game.getActivePlayer();
        if (tile == null
                || hits < 1
                || activePlayer == null
                || !player.hasAbility(STING_OF_THE_HIVE)
                || !removeMines(game, tile, hits)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> hitButtons = List.of(
                Buttons.green(
                        activePlayer.factionButtonChecker() + "autoAssignSpaceHits_" + tile.getPosition() + "_" + hits,
                        "Auto-Assign " + hits + " Hit" + (hits == 1 ? "" : "s")),
                Buttons.red(
                        activePlayer.factionButtonChecker() + "getDamageButtons_" + tile.getPosition()
                                + "deleteThis_spacecombat",
                        "Manually Assign " + hits + " Hit" + (hits == 1 ? "" : "s")));
        MessageHelper.sendMessageToChannelWithButtons(
                activePlayer.getCorrectChannel(),
                player.getRepresentation()
                        + " used **Sting of the Hive** to remove "
                        + hits
                        + " mine"
                        + (hits == 1 ? "" : "s")
                        + " to produce "
                        + hits
                        + " hit"
                        + (hits == 1 ? "" : "s")
                        + " against "
                        + activePlayer.getRepresentationUnfogged()
                        + "'s ships in "
                        + tile.getRepresentation()
                        + ".",
                hitButtons);
        ButtonHelper.deleteMessage(event);
    }

    public static boolean addMine(Game game, Tile tile) {
        if (getAvailableStingOfTheHiveMines(game) < 1) {
            return false;
        }
        int currentCount = getStingOfTheHiveMineCount(game, tile);
        if (currentCount == 0) {
            tile.addToken(MINE_TOKEN, Constants.SPACE);
        }
        game.setStoredValue(MINE_COUNT + tile.getPosition(), Integer.toString(currentCount + 1));
        return true;
    }

    private static boolean removeMines(Game game, Tile tile, int amount) {
        int currentCount = getStingOfTheHiveMineCount(game, tile);
        if (amount < 1 || amount > currentCount) {
            return false;
        }
        int remaining = currentCount - amount;
        if (remaining == 0) {
            tile.removeToken(MINE_TOKEN, Constants.SPACE);
            game.removeStoredValue(MINE_COUNT + tile.getPosition());
        } else {
            game.setStoredValue(MINE_COUNT + tile.getPosition(), Integer.toString(remaining));
        }
        return true;
    }

    public static int getStingOfTheHiveMineCount(Game game, Tile tile) {
        if (!tile.getSpaceUnitHolder().getTokenList().contains(MINE_TOKEN)) {
            return 0;
        }
        try {
            return Math.max(1, Integer.parseInt(game.getStoredValue(MINE_COUNT + tile.getPosition())));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static boolean hasStingOfTheHiveMines(Game game) {
        return game != null
                && game.getTileMap().values().stream().anyMatch(tile -> getStingOfTheHiveMineCount(game, tile) > 0);
    }

    public static int getAvailableStingOfTheHiveMines(Game game) {
        if (game == null) {
            return 0;
        }
        int minesOnMap = game.getTileMap().values().stream()
                .mapToInt(tile -> getStingOfTheHiveMineCount(game, tile))
                .sum();
        return Math.max(0, MAX_MINE_TOKENS - minesOnMap);
    }

    public static Button getStingOfTheHiveMineLedgerButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + "showStingHiveMines", "View Mine Tokens", FactionEmojis.xytheris);
    }

    @ButtonHandler("showStingHiveMines")
    public static void showStingOfTheHiveMines(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasAbility(STING_OF_THE_HIVE)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<String> mineSystems = game.getTileMap().values().stream()
                .filter(tile -> getStingOfTheHiveMineCount(game, tile) > 0)
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> "- " + tile.getRepresentationForButtons(game, player) + " (" + tile.getPosition() + "): **"
                        + getStingOfTheHiveMineCount(game, tile) + " mine token"
                        + (getStingOfTheHiveMineCount(game, tile) == 1 ? "" : "s") + "**")
                .toList();
        String message = mineSystems.isEmpty()
                ? player.getRepresentationUnfogged() + ", there are no mine tokens on the map."
                : player.getRepresentationUnfogged()
                        + ", your **Sting of the Hive** mine tokens:\n"
                        + String.join("\n", mineSystems)
                        + "\n-# Each system shows one mine marker, even when it contains multiple mine tokens.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
    }

    public static void clearStingOfTheHiveRollState(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(STING_ROLL + player.getFaction());
            game.removeStoredValue(STING_ROLL_COUNTER + player.getFaction());
        }
    }
}
