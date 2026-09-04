package ti4.discord.interactions.buttons.handlers.unit.monuments;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.actioncards.theodisi.MirrorShieldingLLButtonHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.game.MonumentsService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

public class TwilightsFallMonumentsButtonHandler {
    private static final String BLUETF_CAPACITY = "bluetfMonumentCapacity_";
    private static final String ORANGETF_MECHS = "orangetfMonumentMechs_";
    private static final String YELLOWTF_COMMAND_TOKENS = "yellowtfMonumentCommandTokens_";
    private static final String YELLOWTF_HIT_CONTEXT = "yellowtfMonumentHitContext_";

    // Halo Cortex
    public static boolean hasPurpleTfMonument(Player player) {
        return player != null && player.hasUnit("purpletf_monument");
    }

    public static boolean hasPurpleTfMonumentInSpace(Game game, Player player, Tile tile) {
        return game != null
                && game.isMonumentsMode()
                && tile != null
                && hasPurpleTfMonument(player)
                && tile.getSpaceUnitHolder().getUnitKeysForPlayer(player).stream()
                        .map(player::getUnitFromUnitKey)
                        .anyMatch(unit -> unit != null && "purpletf_monument".equals(unit.getId()));
    }

    public static List<Button> getPurpleTfMonumentPlacementButtons(Game game, Player player) {
        if (!game.isMonumentsMode()
                || !hasPurpleTfMonument(player)
                || MonumentsService.isMonumentOnBoard(game, player, "purpletf_monument")) {
            return List.of();
        }
        LinkedHashSet<Tile> tiles = new LinkedHashSet<>();
        for (String planet : player.getPlanets()) {
            Tile tile = game.getTileFromPlanet(planet);
            if (tile != null) {
                tiles.add(tile);
            }
        }
        return tiles.stream()
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + "placePurpleTfMonument_" + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    @ButtonHandler("placePurpleTfMonument_")
    public static void placePurpleTfMonument(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.replace("placePurpleTfMonument_", ""));
        if (!game.isMonumentsMode()
                || !hasPurpleTfMonument(player)
                || MonumentsService.isMonumentOnBoard(game, player, "purpletf_monument")
                || tile == null
                || tile.getPlanetUnitHolders().stream()
                        .noneMatch(planet -> player.getPlanets().contains(planet.getName()))) {
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 monument");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed **Halo Cortex** in the space area of "
                        + tile.getRepresentationForButtons(game, player) + ".");
        ButtonHelper.deleteMessage(event);
    }

    // Caracas
    public static Button getBlueTfMonumentButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + "useBlueTfMonumentCapacity", "Use Caracas", FactionEmojis.bluetf);
    }

    public static void sendBlueTfMonumentButton(Game game, Player player) {
        if (game.isMonumentsMode()
                && player.hasUnit("bluetf_monument")
                && MonumentsService.isMonumentOnBoard(game, player, "bluetf_monument")) {
            MessageHelper.sendMessageToChannelWithButton(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + ", you may use **Caracas**.",
                    getBlueTfMonumentButton(player));
        }
    }

    @ButtonHandler("useBlueTfMonumentCapacity")
    public static void useBlueTfMonumentCapacity(ButtonInteractionEvent event, Game game, Player player) {
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "bluetf_monument");
        if (!game.isMonumentsMode()
                || !player.hasUnit("bluetf_monument")
                || monumentTile == null
                || !game.getStoredValue(BLUETF_CAPACITY + player.getFaction()).isEmpty()) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (UnitType unitType : List.of(UnitType.Destroyer, UnitType.Cruiser)) {
            var unit = player.getUnitByType(unitType);
            if (unit != null
                    && unit.getCapacityValue() == 0
                    && monumentTile.getSpaceUnitHolder().getUnitCount(unitType, player) > 0) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + "selectBlueTfMonumentCapacity_" + unitType.name(),
                        "Give " + unitType.humanReadableName() + " Capacity 1",
                        unit.getUnitEmoji()));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Caracas has no eligible destroyers or cruisers.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose which ships **Caracas** gives Capacity 1.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("selectBlueTfMonumentCapacity_")
    public static void selectBlueTfMonumentCapacity(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        UnitType unitType =
                switch (buttonID.replace("selectBlueTfMonumentCapacity_", "")) {
                    case "Cruiser", "ca" -> UnitType.Cruiser;
                    case "Destroyer", "dd" -> UnitType.Destroyer;
                    default -> null;
                };
        if (unitType == null) {
            return;
        }
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "bluetf_monument");
        var unit = player.getUnitByType(unitType);
        if (unit == null
                || unit.getCapacityValue() != 0
                || monumentTile == null
                || monumentTile.getSpaceUnitHolder().getUnitCount(unitType, player) < 1
                || !game.getStoredValue(BLUETF_CAPACITY + player.getFaction()).isEmpty()) {
            return;
        }
        int eligibleShips = monumentTile.getSpaceUnitHolder().getUnitCount(unitType, player);
        game.setStoredValue(BLUETF_CAPACITY + player.getFaction(), unitType.toString());
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " gave " + eligibleShips + " " + unitType.humanReadableName()
                        + (eligibleShips == 1 ? "" : "s")
                        + " in the Caracas system Capacity 1 until this tactical action ends.");
    }

    public static int getBlueTfMonumentCapacity(Game game, Player player, UnitType unitType, int capacity) {
        if (capacity != 0
                || (unitType != UnitType.Destroyer && unitType != UnitType.Cruiser)
                || !game.getStoredValue(BLUETF_CAPACITY + player.getFaction()).equals(unitType.toString())) {
            return 0;
        }
        return 1;
    }

    public static void clearBlueTfMonumentCapacity(Game game) {
        game.getRealPlayers().forEach(player -> game.removeStoredValue(BLUETF_CAPACITY + player.getFaction()));
    }

    public static Button getOrangeTfMonumentButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + "useOrangeTfMonument",
                "Use The Starlit Redoubt",
                FactionEmojis.orangetf);
    }

    // The Starlit Redoubt
    @ButtonHandler("useOrangeTfMonument")
    public static void useOrangeTfMonument(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.isMonumentsMode()
                || !player.hasUnit("orangetf_monument")
                || !MonumentsService.isMonumentOnBoard(game, player, "orangetf_monument")) {
            return;
        }
        List<Tile> monumentTiles = MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player);
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (ButtonHelper.getTilesOfPlayersSpecificUnits(game, target, UnitType.Mech).stream()
                    .noneMatch(monumentTiles::contains)) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + "selectOrangeTfMonumentTarget_" + target.getFaction(),
                    "Choose " + target.getFactionModel().getShortName(),
                    target.getFactionEmoji()));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "No player has an eligible mech.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose whose eligible mechs gain +1 to combat rolls this action.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("selectOrangeTfMonumentTarget_")
    public static void selectOrangeTfMonumentTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("selectOrangeTfMonumentTarget_", ""));
        if (target == null
                || !game.isMonumentsMode()
                || !player.hasUnit("orangetf_monument")
                || !MonumentsService.isMonumentOnBoard(game, player, "orangetf_monument")) {
            return;
        }
        List<Tile> monumentTiles = MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player);
        if (ButtonHelper.getTilesOfPlayersSpecificUnits(game, target, UnitType.Mech).stream()
                .noneMatch(monumentTiles::contains)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That player has no eligible mech.");
            return;
        }
        List<String> selectedMechs = monumentTiles.stream()
                .flatMap(tile -> tile.getUnitHolders().values().stream())
                .flatMap(holder -> holder.getUnitKeysForPlayer(target).stream())
                .filter(unitKey -> unitKey.unitType() == UnitType.Mech)
                .map(UnitKey::asyncID)
                .distinct()
                .toList();
        game.setStoredValue(
                ORANGETF_MECHS + target.getFaction(), player.getFaction() + "|" + String.join(",", selectedMechs));
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                target.getRepresentationNoPing()
                        + "'s mechs in and adjacent to "
                        + player.getFactionModel().getShortName()
                        + "'s **The Starlit Redoubt** system gain +1 to combat rolls this action.");
    }

    public static void addOrangeTfMonumentModifier(
            List<NamedCombatModifierModel> modifiers, Game game, Player player, Tile tile, CombatRollType rollType) {
        if (rollType != CombatRollType.combatround) {
            return;
        }
        String[] effect =
                game.getStoredValue(ORANGETF_MECHS + player.getFaction()).split("\\|", 2);
        if (effect.length != 2 || effect[1].isEmpty()) {
            return;
        }
        Player monumentOwner = game.getPlayerFromColorOrFaction(effect[0]);
        if (monumentOwner == null
                || !monumentOwner.hasUnit("orangetf_monument")
                || !MonumentsService.isMonumentOnBoard(game, monumentOwner, "orangetf_monument")) {
            return;
        }
        var modifier = Mapper.getCombatModifiers().get("plus1_orangetf_monument_mechs");
        if (modifier == null) {
            return;
        }
        for (String asyncId : effect[1].split(",")) {
            CombatModifierModel selectedMechModifier = new CombatModifierModel();
            selectedMechModifier.setAlias(modifier.getAlias() + "_" + asyncId);
            selectedMechModifier.setType(modifier.getType());
            selectedMechModifier.setValue(modifier.getValue());
            selectedMechModifier.setScope(asyncId);
            selectedMechModifier.setForCombatAbility(modifier.getForCombatAbility());
            selectedMechModifier.setPersistenceType(modifier.getPersistenceType());
            modifiers.add(new NamedCombatModifierModel(selectedMechModifier, "+1 from The Starlit Redoubt"));
        }
    }

    public static void clearOrangeTfMonumentMechs(Game game) {
        game.getRealPlayers().forEach(player -> game.removeStoredValue(ORANGETF_MECHS + player.getFaction()));
    }

    public static int getYellowTfMonumentCommandTokenCount(Game game, Player player) {
        if (game == null || player == null || !game.isMonumentsMode() || !player.hasUnit("yellowtf_monument")) {
            return 0;
        }
        String storedCount = game.getStoredValue(YELLOWTF_COMMAND_TOKENS + player.getFaction());
        return storedCount.isBlank() ? 0 : Integer.parseInt(storedCount);
    }

    public static Button getYellowTfMonumentStatusButton(Game game, Player player) {
        int tokens = getYellowTfMonumentCommandTokenCount(game, player);
        return Buttons.gray(
                player.factionButtonChecker() + "checkYellowTfMonumentTokens",
                "Aura Vault: " + tokens + " Command Token" + (tokens == 1 ? "" : "s"),
                FactionEmojis.yellowtf);
    }

    public static Button getYellowTfMonumentCancelHitButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + "useYellowTfMonumentCancelFromCardsInfo",
                "Cancel a Hit with Aura Vault",
                FactionEmojis.yellowtf);
    }

    private static boolean isInOrAdjacentToYellowTfMonument(Game game, Player player, Tile tile) {
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "yellowtf_monument");
        return monumentTile != null
                && tile != null
                && (monumentTile == tile
                        || FoWHelper.getAdjacentTilesAndNotThisTile(game, monumentTile.getPosition(), player, false)
                                .contains(tile.getPosition()));
    }

    public static void addYellowTfMonumentGeneralCombatButton(
            List<Button> buttons, Game game, Player player, Tile tile) {
        if (getYellowTfMonumentCommandTokenCount(game, player) > 0
                && isInOrAdjacentToYellowTfMonument(game, player, tile)) {
            buttons.add(getYellowTfMonumentCancelHitButton(player));
        }
    }

    private static boolean canPlaceYellowTfMonumentCommandToken(Game game, Player player) {
        if (!game.isMonumentsMode() || !player.hasUnit("yellowtf_monument")) {
            return false;
        }
        int commandTokenLimit = 16;
        if (!game.getStoredValue("ccLimit").isBlank()) {
            commandTokenLimit = Integer.parseInt(game.getStoredValue("ccLimit"));
        }
        if (!game.getStoredValue("ccLimit" + player.getColor()).isBlank()) {
            commandTokenLimit = Integer.parseInt(game.getStoredValue("ccLimit" + player.getColor()));
        }
        if (player.hasRelic("endurance_steroids")) {
            commandTokenLimit += 2;
        }
        return Helper.getCCCount(game, player.getColor()) < commandTokenLimit;
    }

    public static void offerYellowTfMonumentCommandToken(Game game, Player player) {
        if (!canPlaceYellowTfMonumentCommandToken(game, player)) {
            return;
        }
        List<Button> buttons = List.of(
                Buttons.gray(
                        player.factionButtonChecker() + "placeYellowTfMonumentCommandToken",
                        "Place a Command Token on Aura Vault",
                        FactionEmojis.yellowtf),
                Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", you may place 1 command token from your reinforcements on **Aura Vault**.",
                buttons);
    }

    @ButtonHandler("placeYellowTfMonumentCommandToken")
    public static void placeYellowTfMonumentCommandToken(ButtonInteractionEvent event, Game game, Player player) {
        if (!canPlaceYellowTfMonumentCommandToken(game, player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Aura Vault cannot take a command token right now.");
            return;
        }
        int tokens = getYellowTfMonumentCommandTokenCount(game, player) + 1;
        game.setStoredValue(YELLOWTF_COMMAND_TOKENS + player.getFaction(), Integer.toString(tokens));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed a command token on **Aura Vault** (" + tokens + ").");
    }

    @ButtonHandler("checkYellowTfMonumentTokens")
    public static void checkYellowTfMonumentTokens(ButtonInteractionEvent event, Game game, Player player) {
        int tokens = getYellowTfMonumentCommandTokenCount(game, player);
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "**Aura Vault** has " + tokens + " command token" + (tokens == 1 ? "" : "s") + " on it.");
    }

    public static void addYellowTfMonumentCancelHitButton(
            List<Button> buttons, Game game, Player player, Tile tile, String hitType, int hits) {
        if (!game.isMonumentsMode()
                || !player.hasUnit("yellowtf_monument")
                || !MonumentsService.isMonumentOnBoard(game, player, "yellowtf_monument")) {
            return;
        }
        game.removeStoredValue(YELLOWTF_HIT_CONTEXT + player.getFaction());
        if (hits < 1
                || getYellowTfMonumentCommandTokenCount(game, player) < 1
                || !isInOrAdjacentToYellowTfMonument(game, player, tile)) {
            return;
        }
        if ("ground".equals(hitType)) {
            StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
            if (combat != null && combat.unitHolderName() != null) {
                hitType += ":" + combat.unitHolderName();
            }
        }
        game.setStoredValue(
                YELLOWTF_HIT_CONTEXT + player.getFaction(), tile.getPosition() + "|" + hitType + "|" + hits);
        buttons.add(Buttons.gray(
                player.factionButtonChecker() + "useYellowTfMonumentCancel_" + tile.getPosition() + "|" + hitType + "|"
                        + hits,
                "Cancel a Hit with Aura Vault",
                FactionEmojis.yellowtf));
    }

    @ButtonHandler("useYellowTfMonumentCancel_")
    public static void useYellowTfMonumentCancel(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        resolveYellowTfMonumentCancel(event, game, player, buttonID, true);
    }

    private static void resolveYellowTfMonumentCancel(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            String buttonID,
            boolean replaceHitAssignmentMessage) {
        String[] payload = buttonID.replace("useYellowTfMonumentCancel_", "").split("\\|", 3);
        if (payload.length != 3) {
            return;
        }
        Tile tile = game.getTileByPosition(payload[0]);
        int hits;
        try {
            hits = Integer.parseInt(payload[2]);
        } catch (NumberFormatException e) {
            return;
        }
        String[] hitType = payload[1].split(":", 2);
        if (tile == null
                || hits < 1
                || getYellowTfMonumentCommandTokenCount(game, player) < 1
                || !isInOrAdjacentToYellowTfMonument(game, player, tile)) {
            return;
        }
        int remainingTokens = getYellowTfMonumentCommandTokenCount(game, player) - 1;
        game.setStoredValue(YELLOWTF_COMMAND_TOKENS + player.getFaction(), Integer.toString(remainingTokens));
        game.removeStoredValue(YELLOWTF_HIT_CONTEXT + player.getFaction());
        int remainingHits = hits - 1;
        if ("ground".equals(hitType[0]) || "space".equals(hitType[0])) {
            MirrorShieldingLLButtonHandler.recordCancelledHits(game, player, tile, 1);
        }
        List<Button> buttons = new ArrayList<>();
        String factionChecker = player.factionButtonChecker();
        if (remainingHits > 0) {
            switch (hitType[0]) {
                case "ground" -> {
                    buttons.add(Buttons.green(
                            factionChecker + "autoAssignGroundHits_"
                                    + (hitType.length == 2 ? hitType[1] : tile.getPosition()) + "_" + remainingHits,
                            "Auto-assign Hit" + (remainingHits == 1 ? "" : "s")));
                    buttons.add(Buttons.red(
                            "getDamageButtons_" + tile.getPosition() + "_groundcombat",
                            "Manually Assign Hit" + (remainingHits == 1 ? "" : "s")));
                    buttons.add(Buttons.gray(
                            "cancelGroundHits_" + tile.getPosition() + "_" + remainingHits, "Cancel a Hit"));
                }
                case "space" -> {
                    buttons.add(Buttons.green(
                            factionChecker + "autoAssignSpaceHits_" + tile.getPosition() + "_" + remainingHits,
                            "Auto-assign Hit" + (remainingHits == 1 ? "" : "s")));
                    buttons.add(Buttons.red(
                            "getDamageButtons_" + tile.getPosition() + "_spacecombat",
                            "Manually Assign Hit" + (remainingHits == 1 ? "" : "s")));
                    buttons.add(Buttons.gray(
                            "cancelSpaceHits_" + tile.getPosition() + "_" + remainingHits, "Cancel a Hit"));
                }
                case "afb" -> {
                    buttons.add(Buttons.green(
                            factionChecker + "autoAssignAFBHits_" + tile.getPosition() + "_" + remainingHits,
                            "Auto-assign Hit" + (remainingHits == 1 ? "" : "s")));
                    buttons.add(Buttons.red(
                            "getDamageButtons_" + tile.getPosition() + "_afb",
                            "Manually Assign Hit" + (remainingHits == 1 ? "" : "s")));
                    buttons.add(
                            Buttons.gray("cancelAFBHits_" + tile.getPosition() + "_" + remainingHits, "Cancel a Hit"));
                }
                case "pds" -> {
                    buttons.add(Buttons.green(
                            factionChecker + "autoAssignSpaceCannonOffenceHits_" + tile.getPosition() + "_"
                                    + remainingHits,
                            "Auto-assign Hit" + (remainingHits == 1 ? "" : "s")));
                    buttons.add(Buttons.red(
                            "getDamageButtons_" + tile.getPosition() + "_pds",
                            "Manually Assign Hit" + (remainingHits == 1 ? "" : "s")));
                    buttons.add(Buttons.gray(
                            "cancelPdsOffenseHits_" + tile.getPosition() + "_" + remainingHits, "Cancel a Hit"));
                }
                default -> {
                    return;
                }
            }
        }
        addYellowTfMonumentCancelHitButton(buttons, game, player, tile, payload[1], remainingHits);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), player.getRepresentationUnfogged() + " canceled 1 hit with **Aura Vault**.");
        String assignmentMessage = remainingHits < 1
                ? "All hits were canceled."
                : "You may assign the remaining hit" + (remainingHits == 1 ? "." : "s.");
        if (replaceHitAssignmentMessage) {
            event.getMessage()
                    .editMessage(assignmentMessage)
                    .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                    .queue();
        } else {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), assignmentMessage, buttons);
            ButtonHelper.deleteTheOneButton(event);
        }
    }

    @ButtonHandler("useYellowTfMonumentCancelFromCardsInfo")
    public static void useYellowTfMonumentCancelFromCardsInfo(ButtonInteractionEvent event, Game game, Player player) {
        String context = game.getStoredValue(YELLOWTF_HIT_CONTEXT + player.getFaction());
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        String[] payload = context.split("\\|", 3);
        if (payload.length != 3
                || combat == null
                || combat.tilePosition() == null
                || !combat.tilePosition().equals(payload[0])
                || !combat.factions().contains(player.getFaction())) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Aura Vault has no pending combat hit to cancel.");
            return;
        }
        resolveYellowTfMonumentCancel(event, game, player, "useYellowTfMonumentCancel_" + context, false);
    }

    public static void clearYellowTfMonumentHitContexts(Game game) {
        game.getRealPlayers().forEach(player -> game.removeStoredValue(YELLOWTF_HIT_CONTEXT + player.getFaction()));
    }

    // The Crown of Thorns
    public static boolean blocksCoexistence(Game game, Tile tile, String unitList) {
        return !game.getStoredValue("coexistFlag").isEmpty()
                && !unitList.toLowerCase().contains(" space")
                && preventsCoexistence(game, tile);
    }

    public static boolean preventsCoexistence(Game game, Tile tile) {
        return game != null
                && tile != null
                && game.isMonumentsMode()
                && game.getRealPlayers().stream()
                        .anyMatch(player -> player.hasUnit("greentf_monument")
                                && MonumentsService.isMonumentOnBoard(game, player, "greentf_monument")
                                && MonumentsService.getMonumentTile(game, player, "greentf_monument") == tile);
    }

    public static void addGreenTfMonumentButtons(List<Button> buttons, Game game, Tile combatTile, String planetName) {
        UnitHolder combatHolder = combatTile.getUnitHolderFromPlanet(planetName);
        if (combatHolder == null) {
            return;
        }
        for (Player monumentOwner : game.getRealPlayers()) {
            if (!monumentOwner.hasUnit("greentf_monument")
                    || !MonumentsService.isMonumentOnBoard(game, monumentOwner, "greentf_monument")
                    || !MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, monumentOwner)
                            .contains(combatTile)
                    || combatHolder.getUnitKeysForPlayer(monumentOwner).stream()
                            .anyMatch(unitKey -> unitKey.unitType() != UnitType.Monument)) {
                continue;
            }
            if (ButtonHelper.getPlayersWithUnitsOnAPlanet(game, combatHolder).stream()
                    .anyMatch(target -> target != monumentOwner
                            && combatHolder.getUnitKeysForPlayer(target).stream()
                                    .anyMatch(unitKey -> unitKey.unitType() != UnitType.Monument))) {
                buttons.add(Buttons.gray(
                        monumentOwner.factionButtonChecker() + "useGreenTfMonument_" + combatTile.getPosition() + "|"
                                + planetName,
                        "Use The Crown Of Thorns",
                        FactionEmojis.greentf));
            }
        }
    }

    @ButtonHandler("useGreenTfMonument_")
    public static void useGreenTfMonument(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.replace("useGreenTfMonument_", "").split("\\|", 2);
        Tile combatTile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        UnitHolder combatHolder =
                combatTile == null || payload.length != 2 ? null : combatTile.getUnitHolderFromPlanet(payload[1]);
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "greentf_monument");
        if (combatTile == null
                || combatHolder == null
                || !game.isMonumentsMode()
                || !player.hasUnit("greentf_monument")
                || monumentTile == null
                || !MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player)
                        .contains(combatTile)
                || combatHolder.getUnitKeysForPlayer(player).stream()
                        .anyMatch(unitKey -> unitKey.unitType() != UnitType.Monument)) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Player target : ButtonHelper.getPlayersWithUnitsOnAPlanet(game, combatHolder)) {
            if (target == player
                    || combatHolder.getUnitKeysForPlayer(target).stream()
                            .noneMatch(unitKey -> unitKey.unitType() != UnitType.Monument)) {
                continue;
            }
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + "resolveGreenTfMonument_" + target.getFaction() + "|"
                            + combatTile.getPosition() + "|" + combatHolder.getName(),
                    "Produce 1 Hit Against " + target.getFactionModel().getShortName(),
                    target.getFactionEmoji()));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "There are no eligible players on that planet.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose whose units suffer 1 hit from **The Crown Of Thorns**.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("resolveGreenTfMonument_")
    public static void resolveGreenTfMonument(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.replace("resolveGreenTfMonument_", "").split("\\|", 3);
        Player target = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Tile combatTile = payload.length == 3 ? game.getTileByPosition(payload[1]) : null;
        UnitHolder combatHolder =
                combatTile == null || payload.length != 3 ? null : combatTile.getUnitHolderFromPlanet(payload[2]);
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "greentf_monument");
        if (target == null
                || combatTile == null
                || combatHolder == null
                || !game.isMonumentsMode()
                || !player.hasUnit("greentf_monument")
                || monumentTile == null
                || !MonumentsService.getTilesInOrAdjacentToPlayerMonument(game, player)
                        .contains(combatTile)
                || combatHolder.getUnitKeysForPlayer(player).stream()
                        .anyMatch(unitKey -> unitKey.unitType() != UnitType.Monument)
                || combatHolder.getUnitKeysForPlayer(target).stream()
                        .noneMatch(unitKey -> unitKey.unitType() != UnitType.Monument)) {
            return;
        }
        List<Button> buttons = List.of(
                Buttons.green(
                        target.factionButtonChecker() + "autoAssignGroundHits_" + combatHolder.getName() + "_1",
                        "Auto-assign Hit"),
                Buttons.red(
                        "getDamageButtons_" + combatTile.getPosition() + "deleteThis_groundcombat",
                        "Manually Assign Hit"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                target.getRepresentationNoPing() + " suffered 1 hit from **The Crown Of Thorns**.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    // The Serrated Throne
    public static void addRedTfMonumentButtons(List<Button> buttons, Game game, Tile tile, String planetName) {
        Planet planet = tile.getUnitHolderFromPlanet(planetName);
        if (planet == null) {
            return;
        }
        List<String> planetTypes = new ArrayList<>(planet.getPlanetTypes());
        if (planet.isLegendary()) {
            planetTypes.add("LEGENDARY");
        }
        if (planet.getPlanetModel() != null
                && planet.getPlanetModel().getPlanetTypes().stream()
                        .anyMatch(type -> "lightning".equalsIgnoreCase(type.toString()))) {
            planetTypes.add("LIGHTNING");
        }
        for (Player player : game.getRealPlayers()) {
            UnitModel monument = player.getUnitByBaseType("monument");
            if (!game.isMonumentsMode()
                    || !player.hasUnit("redtf_monument")
                    || MonumentsService.isMonumentOnBoard(game, player, "redtf_monument")
                    || planet.getUnitKeysForPlayer(player).isEmpty()
                    || monument == null
                    || !monument.canBePlacedOnPlanetTypes(planetTypes)) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + "useRedTfMonument_" + tile.getPosition() + "|" + planet.getName(),
                    "Deploy The Serrated Throne",
                    FactionEmojis.redtf));
        }
    }

    @ButtonHandler("useRedTfMonument_")
    public static void useRedTfMonument(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.replace("useRedTfMonument_", "").split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        Planet planet = tile == null || payload.length != 2 ? null : tile.getUnitHolderFromPlanet(payload[1]);
        UnitModel monument = player.getUnitByBaseType("monument");
        if (tile == null
                || planet == null
                || !game.isMonumentsMode()
                || !player.hasUnit("redtf_monument")
                || MonumentsService.isMonumentOnBoard(game, player, "redtf_monument")
                || planet.getUnitKeysForPlayer(player).isEmpty()
                || monument == null) {
            return;
        }
        List<String> planetTypes = new ArrayList<>(planet.getPlanetTypes());
        if (planet.isLegendary()) {
            planetTypes.add("LEGENDARY");
        }
        if (planet.getPlanetModel() != null
                && planet.getPlanetModel().getPlanetTypes().stream()
                        .anyMatch(type -> "lightning".equalsIgnoreCase(type.toString()))) {
            planetTypes.add("LIGHTNING");
        }
        if (!monument.canBePlacedOnPlanetTypes(planetTypes)) {
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 monument " + planet.getName());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " deployed **The Serrated Throne** on "
                        + planet.getRepresentation(game) + ".");
        for (Player target : ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, planet.getName())) {
            if (target == player) {
                continue;
            }
            List<Button> hitButtons = List.of(
                    Buttons.green(
                            target.factionButtonChecker() + "autoAssignGroundHits_" + planet.getName() + "_2",
                            "Auto-assign 2 Hits"),
                    Buttons.red(
                            "getDamageButtons_" + tile.getPosition() + "deleteThis_groundcombat",
                            "Manually Assign 2 Hits"));
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    target.getRepresentationNoPing() + " suffered 2 hits from **The Serrated Throne**.",
                    hitButtons);
        }
        ButtonHelper.deleteTheOneButton(event);
    }

    // The Flesh Cathedral
    public static void captureBlacktfDestroyedInfantry(
            GenericInteractionCreateEvent event, Game game, List<RemovedUnit> destroyedUnits) {
        if (!game.isMonumentsMode()) {
            return;
        }
        for (Player monumentOwner : game.getRealPlayers()) {
            if (!monumentOwner.hasUnit("blacktf_monument")
                    || !MonumentsService.isMonumentOnBoard(game, monumentOwner, "blacktf_monument")) {
                continue;
            }
            Tile monumentTile = MonumentsService.getMonumentTile(game, monumentOwner, "blacktf_monument");
            for (RemovedUnit unit : destroyedUnits) {
                Player destroyedUnitOwner = unit.getPlayer(game);
                if (monumentTile != unit.tile()
                        || unit.unitKey().unitType() != UnitType.Infantry
                        || destroyedUnitOwner == null) {
                    continue;
                }
                ButtonHelperFactionSpecific.cabalEatsUnit(
                        destroyedUnitOwner, game, monumentOwner, unit.getTotalRemoved(), "infantry", event);
            }
        }
    }

    public static boolean canSpendBlacktfCapturedInfantry(Game game, Player player) {
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "blacktf_monument");
        return game.isMonumentsMode()
                && player.hasUnit("blacktf_monument")
                && monumentTile != null
                && player.getNomboxTile().getSpaceUnitHolder().getUnitCount(UnitType.Infantry, player) > 0
                && !player.getCurrentProducedUnits().isEmpty()
                && player.getCurrentProducedUnits().keySet().stream().allMatch(unit -> {
                    String[] parts = unit.split("_", 3);
                    return parts.length == 3 && monumentTile.getPosition().equals(parts[1]);
                });
    }

    public static int getBlacktfCapturedInfantrySpent(Player player) {
        return player.getSpentThingsThisWindow().stream()
                .filter(thing -> thing.startsWith("blacktfCapturedInfantry_"))
                .mapToInt(thing -> Integer.parseInt(thing.substring("blacktfCapturedInfantry_".length())))
                .sum();
    }

    @ButtonHandler("spendBlacktfCapturedInfantry")
    public static void spendBlacktfCapturedInfantry(ButtonInteractionEvent event, Game game, Player player) {
        if (!canSpendBlacktfCapturedInfantry(game, player)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Captured infantry can only pay for production in The Flesh Cathedral's system.");
            return;
        }
        RemoveUnitService.removeUnits(event, player.getNomboxTile(), game, player.getColor(), "1 infantry");
        int spent = getBlacktfCapturedInfantrySpent(player);
        player.getSpentThingsThisWindow().stream()
                .filter(thing -> thing.startsWith("blacktfCapturedInfantry_"))
                .toList()
                .forEach(player::removeSpentThing);
        player.addSpentThing("blacktfCapturedInfantry_" + (spent + 1));
        event.getMessage()
                .editMessage(Helper.buildSpentThingsMessage(player, game, "res"))
                .queue(_ -> {
                    if (player.getNomboxTile().getSpaceUnitHolder().getUnitCount(UnitType.Infantry, player) < 1) {
                        ButtonHelper.deleteTheOneButton(event);
                    }
                });
    }
}
