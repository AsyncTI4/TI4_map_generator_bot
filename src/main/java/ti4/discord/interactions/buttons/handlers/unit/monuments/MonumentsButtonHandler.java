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
import ti4.helpers.NewStuffHelper;
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
import ti4.service.unit.RemoveUnitService;

public class MonumentsButtonHandler {
    private static final String PLACE_UNIT_ON_MONUMENT_PLANET = "placeUnitOnMonumentPlanet_";
    private static final String PLACE_ARBOREC_MONUMENT_INFANTRY = "placeArborecMonumentInfantry_";
    private static final String PLACE_JOLNAR_MONUMENT_INFANTRY = "jolnarMonumentInfantry_";
    private static final String USE_L1_MONUMENT = "useL1Monument";
    private static final String L1_TARGET = "l1MonumentTarget_";
    private static final String L1_MOVE_CC = "l1MonumentMoveCC_";
    private static final String SELECT_FIREFLY_SYSTEM = "selectFireflySystem_";
    private static final String PLACE_FIREFLY_UNIT = "placeFireflyUnit_";
    private static final String FIREFLY_CHOOSE_ANOTHER_SYSTEM = "fireflyChooseAnotherSystem_";
    private static final String FIREFLY_DONE_PRODUCING = "fireflyDoneProducing";

    // Glory Furnace
    public static void offerGloryFurnace(Game game, Player player, String planetName) {
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "muaat_monument")) return;
        Tile tile = MonumentsService.getMonumentTile(game, player, "muaat_monument");
        if (tile == null
                || tile.getUnitHolderFromPlanet(planetName) == null
                || tile.getUnitHolderFromPlanet(planetName).getUnitCount(UnitType.Monument, player) == 0) return;
        int choices = tile.getSpaceUnitHolder().getUnitCount(UnitType.Warsun, player)
                + tile.getSpaceUnitHolder().getUnitCount(UnitType.Flagship, player);
        for (String position : FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false)) {
            Tile adjacent = game.getTileByPosition(position);
            if (adjacent != null && adjacent.isSupernova()) choices++;
        }
        if (choices > 0) sendGloryFurnaceChoices(game, player, choices, 0);
    }

    private static void sendGloryFurnaceChoices(Game game, Player player, int remaining, int page) {
        List<Button> buttons = new ArrayList<>();
        String prefix = player.factionButtonChecker() + "resolveGloryFurnace_" + remaining + "|";
        buttons.add(Buttons.green(prefix + "votes", "Cast 3 Additional Votes"));
        for (Tile tile : MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player)) {
            for (Player target : game.getPlayers().values()) {
                if (target != player && FoWHelper.playerHasShipsInSystem(target, tile)) {
                    buttons.add(Buttons.red(
                            prefix + tile.getPosition() + "|" + target.getFaction(),
                            "Hit " + target.getColor() + " in " + tile.getRepresentationForButtons(game, player)));
                }
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", resolve _Glory Furnace_: " + remaining + " choice"
                        + (remaining == 1 ? " remains." : "s remain.")
                        + "\n-# Finish these choices before submitting your votes.",
                NewStuffHelper.buttonPagination(buttons, prefix, page));
    }

    @ButtonHandler("resolveGloryFurnace_")
    public static void resolveGloryFurnace(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "muaat_monument")) return;
        String[] parts = buttonID.substring("resolveGloryFurnace_".length()).split("\\|");
        int remaining = Integer.parseInt(parts[0]);
        if (remaining < 1) return;
        if (parts[1].startsWith("page")) {
            ButtonHelper.deleteMessage(event);
            sendGloryFurnaceChoices(game, player, remaining, Integer.parseInt(parts[1].substring(4)));
            return;
        }
        if ("votes".equals(parts[1])) {
            int votes = 3;
            for (String spent : new ArrayList<>(player.getSpentThingsThisWindow())) {
                if (spent.startsWith("muaatMonument_")) {
                    votes += Integer.parseInt(spent.substring("muaatMonument_".length()));
                    player.removeSpentThing(spent);
                }
            }
            player.addSpentThing("muaatMonument_" + votes);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), Helper.buildSpentThingsMessageForVoting(player, game, false));
        } else {
            Tile tile = game.getTileByPosition(parts[1]);
            Player target = game.getPlayerFromColorOrFaction(parts[2]);
            if (tile == null
                    || target == null
                    || target == player
                    || !MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player)
                            .contains(tile)
                    || !FoWHelper.playerHasShipsInSystem(target, tile)) return;
            CombatRollService.sendSpaceAssignHitsButtons(event, game, target, tile, 1);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    target.getRepresentation() + ", your ships in " + tile.getRepresentationForButtons(game, target)
                            + " suffer 1 hit from _Glory Furnace_.");
        }
        ButtonHelper.deleteMessage(event);
        if (remaining > 1) sendGloryFurnaceChoices(game, player, remaining - 1, 0);
    }

    // G'hom Firefly Beacon
    public static void sendFireflyProduction(Game game, Player player, int remaining, int page) {
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "norr_monument")) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        String prefix = player.factionButtonChecker() + SELECT_FIREFLY_SYSTEM + remaining + "|";
        for (Tile tile : MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player)) {
            if (tile.getPlanetUnitHolders().stream()
                    .noneMatch(planet -> player.getPlanets().contains(planet.getName()))) {
                continue;
            }
            buttons.add(Buttons.green(
                    prefix + tile.getPosition(), "Produce in " + tile.getRepresentationForButtons(game, player)));
        }
        List<Button> done = List.of(Buttons.red(player.factionButtonChecker() + FIREFLY_DONE_PRODUCING, "Done"));
        if (buttons.size() <= 24) buttons.addAll(done);
        else buttons = NewStuffHelper.buttonPagination(buttons, done, prefix, 25, page, false);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose a system to produce up to " + remaining
                        + " more infantry or fighters without spending resources with _G'hom Firefly Beacon_.",
                buttons);
    }

    @ButtonHandler(SELECT_FIREFLY_SYSTEM)
    public static void selectFireflySystem(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "norr_monument")) {
            return;
        }
        String[] parts = buttonID.substring(SELECT_FIREFLY_SYSTEM.length()).split("\\|", 2);
        if (parts.length != 2) {
            return;
        }
        int remaining;
        try {
            remaining = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return;
        }
        if (parts[1].startsWith("page")) {
            ButtonHelper.deleteMessage(event);
            sendFireflyProduction(game, player, remaining, Integer.parseInt(parts[1].substring(4)));
            return;
        }
        Tile tile = game.getTileByPosition(parts[1]);
        if (remaining < 1
                || remaining > 3
                || tile == null
                || (tile.getTileModel() != null && tile.getTileModel().isHyperlane())
                || !MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player)
                        .contains(tile)
                || tile.getPlanetUnitHolders().stream()
                        .noneMatch(planet -> player.getPlanets().contains(planet.getName()))) {
            return;
        }
        ButtonHelper.deleteMessage(event);
        sendFireflyUnitChoices(game, player, tile, remaining);
    }

    @ButtonHandler(PLACE_FIREFLY_UNIT)
    public static void placeFireflyUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "norr_monument")) {
            return;
        }
        String[] parts = buttonID.substring(PLACE_FIREFLY_UNIT.length()).split("\\|", 4);
        if (parts.length != 4) {
            return;
        }
        int remaining;
        try {
            remaining = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return;
        }
        Tile tile = game.getTileByPosition(parts[1]);
        if (remaining < 1
                || remaining > 3
                || tile == null
                || (tile.getTileModel() != null && tile.getTileModel().isHyperlane())
                || !("ff".equals(parts[2]) || "gf".equals(parts[2]))
                || !MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player)
                        .contains(tile)
                || tile.getPlanetUnitHolders().stream()
                        .noneMatch(planet -> player.getPlanets().contains(planet.getName()))
                || ("space".equals(parts[3])
                        && tile.getSpaceUnitHolder()
                                .getUnitKeysForPlayer(player)
                                .isEmpty())
                || (!"space".equals(parts[3])
                        && (!"gf".equals(parts[2])
                                || !player.getPlanets().contains(parts[3])
                                || tile.getUnitHolderFromPlanet(parts[3]) == null))) return;
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + parts[2] + " " + parts[3]);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " produced 1 " + ("ff".equals(parts[2]) ? "fighter" : "infantry") + " "
                        + ("space".equals(parts[3])
                                ? "in " + tile.getRepresentationForButtons(game, player)
                                : "on " + Helper.getPlanetRepresentation(parts[3], game))
                        + " with _G'hom Firefly Beacon_.");
        ButtonHelper.deleteMessage(event);
        if (remaining > 1) {
            sendFireflyUnitChoices(game, player, tile, remaining - 1);
        }
    }

    @ButtonHandler(FIREFLY_CHOOSE_ANOTHER_SYSTEM)
    public static void chooseAnotherFireflySystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int remaining;
        try {
            remaining = Integer.parseInt(buttonID.substring(FIREFLY_CHOOSE_ANOTHER_SYSTEM.length()));
        } catch (NumberFormatException e) {
            return;
        }
        if (!game.isMonumentsMode()
                || !MonumentsService.isMonumentOnBoard(game, player, "norr_monument")
                || remaining < 1
                || remaining > 3) {
            return;
        }
        ButtonHelper.deleteMessage(event);
        sendFireflyProduction(game, player, remaining, 0);
    }

    @ButtonHandler(FIREFLY_DONE_PRODUCING)
    public static void finishFireflyProduction(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }

    private static void sendFireflyUnitChoices(Game game, Player player, Tile tile, int remaining) {
        List<Button> buttons = new ArrayList<>();
        String prefix = player.factionButtonChecker() + PLACE_FIREFLY_UNIT + remaining + "|" + tile.getPosition() + "|";
        if (!tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).isEmpty()) {
            buttons.add(Buttons.green(
                    prefix + "ff|space", "Produce Fighter in " + tile.getRepresentationForButtons(game, player)));
            buttons.add(Buttons.green(
                    prefix + "gf|space", "Produce Infantry in " + tile.getRepresentationForButtons(game, player)));
        }
        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (player.getPlanets().contains(planet.getName())) {
                buttons.add(Buttons.green(
                        prefix + "gf|" + planet.getName(),
                        "Produce Infantry on " + Helper.getPlanetRepresentation(planet.getName(), game)));
            }
        }
        buttons.add(Buttons.gray(
                player.factionButtonChecker() + FIREFLY_CHOOSE_ANOTHER_SYSTEM + remaining, "Done Producing"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose a unit to produce in "
                        + tile.getRepresentationForButtons(game, player) + " with _G'hom Firefly Beacon_.",
                buttons);
    }

    public static void offerFireflyReplacement(Game game, Player player) {
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "norr_monument")) return;
        List<Button> buttons = new ArrayList<>();
        for (UnitType type : List.of(UnitType.Pds, UnitType.Spacedock)) {
            UnitModel unit = player.getUnitByType(type);
            if (unit != null
                    && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, type.toString())
                            < player.getUnitCap(type.toString()))
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "replaceFireflyBeacon_" + type,
                        "Replace with " + unit.getName(),
                        unit.getUnitEmoji()));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may replace _G'hom Firefly Beacon_ with a structure from your reinforcements.",
                buttons);
    }

    @ButtonHandler("replaceFireflyBeacon_")
    public static void replaceFireflyBeacon(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "norr_monument")) return;
        String type = buttonID.substring("replaceFireflyBeacon_".length());
        if (!"pds".equals(type) && !"sd".equals(type)) return;
        if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, type) >= player.getUnitCap(type)) return;
        Tile tile = MonumentsService.getMonumentTile(game, player, "norr_monument");
        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (planet.getUnitCount(UnitType.Monument, player) == 0) continue;
            RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 monument " + planet.getName());
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + type + " " + planet.getName());
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " replaced _G'hom Firefly Beacon_ with a " + ("pds".equals(type) ? "PDS" : "space dock")
                            + " on " + Helper.getPlanetRepresentation(planet.getName(), game) + ".");
            ButtonHelper.deleteMessage(event);
            return;
        }
    }

    // Erwan's Fist
    public static Button getMentakMonumentButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + "useMentakMonument", "Use Erwan's Fist", FactionEmojis.Mentak);
    }

    @ButtonHandler("useMentakMonument")
    public static void useMentakMonument(ButtonInteractionEvent event, Game game, Player player) {
        if (!MonumentsService.isMonumentReady(game, player, "mentak_monument")) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player)) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "resolveMentakMonument_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", choose a system for _Erwan's Fist_. Each ship there with SUSTAIN DAMAGE will become damaged."
                        + "\n-# Use this at the start of a player's turn.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("resolveMentakMonument_")
    public static void resolveMentakMonument(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring("resolveMentakMonument_".length()));
        if (!MonumentsService.isMonumentReady(game, player, "mentak_monument")
                || tile == null
                || !MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player)
                        .contains(tile)
                || !MonumentsService.exhaustMonument(game, player, "mentak_monument")) {
            return;
        }
        int damaged = 0;
        for (var holder : tile.getUnitHolders().values()) {
            for (Player target : game.getPlayers().values()) {
                for (UnitKey key : holder.getUnitKeysForPlayer(target)) {
                    UnitModel unit = target.getPriorityUnitByAsyncID(key.asyncID(), holder);
                    if (unit != null && unit.getIsShip() && unit.getSustainDamage()) {
                        damaged += holder.addDamagedUnit(key, holder.getUnitCount(key));
                    }
                }
            }
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " exhausted _Erwan's Fist_ and damaged " + damaged
                        + (damaged == 1 ? " ship" : " ships") + " in "
                        + tile.getRepresentationForButtons(game, player) + ".");
        ButtonHelper.deleteMessage(event);
    }

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
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "l1z1x_monument")) {
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
                        target.getFactionModel().getShortName(),
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
                || !MonumentsService.isMonumentOnBoard(game, player, "l1z1x_monument")) {
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
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "jolnar_monument")) {
            return;
        }
        UnitKey monumentKey = Units.getUnitKey(UnitType.Monument, player.getColor());
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "jolnar_monument");
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
        if (amount < 1 || amount > 2 || !MonumentsService.isMonumentOnBoard(game, player, "jolnar_monument")) {
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
                if (MonumentsService.isMonumentOnBoard(game, monumentOwner, "hacan_monument")
                        && MonumentsService.isInOrAdjacentToMonumentSystem(
                                game, monumentOwner, "hacan_monument", tile)) {
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
                .filter(tile ->
                        tile.getTileModel() == null || !tile.getTileModel().isHyperlane())
                .sorted(Comparator.comparing(Tile::getPosition))
                .toList()) {
            boolean eligibleSystem = game.getRealPlayers().stream()
                    .anyMatch(monumentOwner -> MonumentsService.isMonumentOnBoard(game, monumentOwner, "hacan_monument")
                            && MonumentsService.isInOrAdjacentToMonumentSystem(
                                    game, monumentOwner, "hacan_monument", tile));
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
                && (sourceTile.getTileModel() == null
                        || !sourceTile.getTileModel().isHyperlane())
                && game.getRealPlayers().stream()
                        .anyMatch(monumentOwner ->
                                MonumentsService.isMonumentOnBoard(game, monumentOwner, "hacan_monument")
                                        && MonumentsService.isInOrAdjacentToMonumentSystem(
                                                game, monumentOwner, "hacan_monument", sourceTile));
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
                .filter(tile ->
                        tile.getTileModel() == null || !tile.getTileModel().isHyperlane())
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
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "arborec_monument")) {
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
                || !MonumentsService.isMonumentOnBoard(game, player, "creuss_monument")
                || monumentTile != MonumentsService.getMonumentTile(game, player, "creuss_monument")) {
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
