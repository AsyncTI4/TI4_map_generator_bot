package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Arcanum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;

@UtilityClass
public class ArcanumPrimordialTechHandler {
    private static final List<String> PRIMORDIAL_TECHS =
            List.of("tharcanumpmy", "tharcanumpmg", "tharcanumpmr", "tharcanumpmb");
    private static final String MIRACLE = "tharcanumpmg";
    private static final String DISCARD_AC = "miracleAcDiscard";
    private static final String GAIN_CC = "miracleGainCC";
    private static final String PLACE_INF = "miraclePlaceFourInf";
    private static final String SELECT_AC = "miracleSelectAcToDiscard_";
    private static final String PLACE_INFANTRY = "miraclePlaceInfantry_";
    private static final String FINISH_INFANTRY = "miracleFinishInfantry_";
    private static final String INFANTRY_PLACED = "miracleInfantryPlaced_";
    private static final String FABRICATE_STATION = "fabricatestation";
    private static final String FABRICATE_TECH = "tharcanumpmy";
    private static final String FABRICATE_SELECT_SOURCE = "fabricateStationSource_";
    private static final String FABRICATE_SELECT_SYSTEM = "fabricateStationSystem_";
    private static final String FABRICATE_PLACE_UNIT = "fabricateStationPlace_";
    private static final String DISINTEGRATE = "tharcanumpmr";
    private static final String DISINTEGRATE_HIT = "arcanumDisintegrateHit_";
    private static final String DISINTEGRATE_DESTROY = "arcanumDisintegrateDestroy_";
    private static final String DISINTEGRATE_DESTROY_UNIT = "arcanumDisintegrateDestroyUnit_";
    private static final String PLANE_SHIFT = "tharcanumpmb";
    private static final String PLANE_SHIFT_SELECT_SYSTEM = "planeShiftSystem_";
    private static final String PLANE_SHIFT_PENDING = "planeShiftPending";
    private static final String PLANE_SHIFT_PLAYER = "planeShiftPlayer";
    private static final String PLANE_SHIFT_SYSTEM = "planeShiftSystem";

    // Forbidden Knowledge
    public static boolean hasFourTechsMatchingPrimordial(Player player) {
        return player != null
                && PRIMORDIAL_TECHS.stream()
                        .filter(player::hasTech)
                        .anyMatch(primordialTech -> hasFourTechsMatchingPrimordial(player, primordialTech));
    }

    public static boolean hasFourTechsMatchingPrimordial(Player player, String primordialTech) {
        if (player == null || primordialTech == null) {
            return false;
        }

        String resolvedPrimordialTech = AliasHandler.resolveTech(primordialTech);
        if (!PRIMORDIAL_TECHS.contains(resolvedPrimordialTech) || !player.hasTech(resolvedPrimordialTech)) {
            return false;
        }

        TechnologyModel primordialModel = Mapper.getTech(resolvedPrimordialTech);
        TechnologyModel.TechnologyType primordialType = primordialModel == null ? null : primordialModel.getFirstType();
        if (primordialType == null) {
            return false;
        }

        return player.getTechs().stream()
                        .map(AliasHandler::resolveTech)
                        // Primordial technologies never contribute to Forbidden Knowledge, including other colors.
                        .filter(tech -> !PRIMORDIAL_TECHS.contains(tech))
                        .map(Mapper::getTech)
                        .filter(tech -> tech != null && tech.getTypes().contains(primordialType))
                        .count()
                >= 4;
    }

    // Power Word: Miracle
    public static void resolvePowerWordMiracle(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasTech(MIRACLE)
                || !player.getExhaustedTechs().contains(MIRACLE)
                || !hasFourTechsMatchingPrimordial(player, MIRACLE)) {
            return;
        }
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + DISCARD_AC,
                "Discard 1 Action Card and Draw 2",
                CardEmojis.getACEmoji(game)));
        buttons.add(Buttons.green(player.factionButtonChecker() + GAIN_CC, "Gain 1 Command Token"));
        buttons.add(Buttons.green(
                player.factionButtonChecker() + PLACE_INF, "Place Up to 4 Infantry", UnitEmojis.infantry));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose an effect for _Power Word: Miracle_.",
                buttons);
    }

    @ButtonHandler(DISCARD_AC)
    public static void getAcDiscardButtons(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasTech(MIRACLE)
                || !player.getExhaustedTechs().contains(MIRACLE)
                || !hasFourTechsMatchingPrimordial(player, MIRACLE)) {
            return;
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);

        List<Button> discardButtons = new ArrayList<>();
        for (Map.Entry<String, Integer> actionCard : player.getActionCards().entrySet()) {
            discardButtons.add(Buttons.blue(
                    player.factionButtonChecker() + SELECT_AC + actionCard.getValue(),
                    "(" + actionCard.getValue() + ") "
                            + Mapper.getActionCard(actionCard.getKey()).getName(),
                    CardEmojis.getACEmoji(game)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", please choose an action card to discard.",
                discardButtons);
    }

    @ButtonHandler(SELECT_AC)
    public static void resolveMiracleAcDiscard(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (player == null
                || game == null
                || !player.hasTech(MIRACLE)
                || !player.getExhaustedTechs().contains(MIRACLE)
                || !hasFourTechsMatchingPrimordial(player, MIRACLE)) {
            return;
        }

        int handIndex;
        try {
            handIndex = Integer.parseInt(buttonID.substring(SELECT_AC.length()));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (!player.getExhaustedTechs().contains(MIRACLE)
                || !player.getActionCards().containsValue(handIndex)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ActionCardHelper.discardAC(event, game, player, handIndex);
        ActionCardHelper.drawActionCards(player, 2);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(GAIN_CC)
    public static void resolveMiracleCCGain(ButtonInteractionEvent event, Game game, Player player) {
        if (player == null
                || game == null
                || !player.hasTech(MIRACLE)
                || !player.getExhaustedTechs().contains(MIRACLE)
                || !hasFourTechsMatchingPrimordial(player, MIRACLE)) {
            return;
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose where to gain 1 command token.",
                ButtonHelper.getGainCCButtons(player));
    }

    @ButtonHandler(PLACE_INF)
    public static void getMiracleInfantryPlacementButtons(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasTech(MIRACLE)
                || !player.getExhaustedTechs().contains(MIRACLE)
                || !hasFourTechsMatchingPrimordial(player, MIRACLE)) {
            return;
        }

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        int round = game.getRound();
        game.setStoredValue(INFANTRY_PLACED + player.getFaction() + "_" + round, "0");
        List<Button> planetButtons = getMiracleInfantryButtons(game, player, round);
        if (planetButtons.isEmpty()) {
            game.removeStoredValue(INFANTRY_PLACED + player.getFaction() + "_" + round);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " has no eligible planet for the infantry.");
            return;
        }

        String message = player.getRepresentation()
                + ", please choose planets on which to place up to 4 infantry with _Power Word: Miracle_.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_INFANTRY + round + "_";
        Button done = Buttons.red(player.factionButtonChecker() + FINISH_INFANTRY + round, "Done Placing Infantry");
        List<Button> displayedButtons = planetButtons.size() < 25
                ? new ArrayList<>(planetButtons)
                : NewStuffHelper.buttonPagination(planetButtons, List.of(done), buttonPrefix, 25, 0, false);
        if (planetButtons.size() < 25) {
            displayedButtons.add(done);
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, displayedButtons);
    }

    @ButtonHandler(PLACE_INFANTRY)
    public static void placeMiracleInfantry(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null
                || player == null
                || !player.hasTech(MIRACLE)
                || !player.getExhaustedTechs().contains(MIRACLE)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String placementData = buttonID.substring(PLACE_INFANTRY.length());
        int separator = placementData.indexOf('_');
        if (separator < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        int round;
        try {
            round = Integer.parseInt(placementData.substring(0, separator));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String stateKey = INFANTRY_PLACED + player.getFaction() + "_" + round;
        String placedValue = game.getStoredValue(stateKey);
        if (round != game.getRound() || placedValue.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> planetButtons = getMiracleInfantryButtons(game, player, round);
        String message = player.getRepresentation()
                + ", please choose planets on which to place up to 4 infantry with _Power Word: Miracle_.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_INFANTRY + round + "_";
        Button done = Buttons.red(player.factionButtonChecker() + FINISH_INFANTRY + round, "Done Placing Infantry");
        String planetName = placementData.substring(separator + 1);
        if (planetName.startsWith("page")) {
            try {
                int page = Integer.parseInt(planetName.substring("page".length()));
                NewStuffHelper.sendOrEditButtons(
                        event,
                        event.getMessageChannel(),
                        message,
                        NewStuffHelper.buttonPagination(planetButtons, List.of(done), buttonPrefix, 25, page, false));
            } catch (NumberFormatException e) {
                ButtonHelper.deleteMessage(event);
            }
            return;
        }

        int placed;
        try {
            placed = Integer.parseInt(placedValue);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (placed >= 4) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + " has already placed all 4 infantry. Press **Done Placing Infantry** when finished.");
            return;
        }
        if (planet == null
                || !player.getPlanetsAllianceMode().contains(planetName)
                || planet.isSpaceStation(game)
                || planet.getTokenList().stream().anyMatch(token -> token.contains("dmz"))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        AddUnitService.addUnits(
                event, game.getTileFromPlanet(planetName), game, player.getColor(), "1 gf " + planetName);
        game.setStoredValue(stateKey, String.valueOf(placed + 1));
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " placed 1 infantry on " + Helper.getPlanetRepresentation(planetName, game)
                        + " with _Power Word: Miracle_ (" + (placed + 1) + "/4)."
                        + (placed == 3 ? " Press **Done Placing Infantry** when finished." : ""));
    }

    @ButtonHandler(FINISH_INFANTRY)
    public static void finishMiracleInfantry(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        try {
            int round = Integer.parseInt(buttonID.substring(FINISH_INFANTRY.length()));
            game.removeStoredValue(INFANTRY_PLACED + player.getFaction() + "_" + round);
        } catch (NumberFormatException ignored) {
            // The message is still safe to remove if an old or malformed completion button is pressed.
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getMiracleInfantryButtons(Game game, Player player, int round) {
        List<Button> planetButtons = new ArrayList<>();
        for (String planetName : player.getPlanetsAllianceMode()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet == null
                    || planet.isSpaceStation(game)
                    || planet.getTokenList().stream().anyMatch(token -> token.contains("dmz"))) {
                continue;
            }
            planetButtons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE_INFANTRY + round + "_" + planetName,
                    "Add 1 infantry to " + Helper.getPlanetRepresentation(planetName, game),
                    UnitEmojis.infantry));
        }
        return planetButtons;
    }

    // Power Word: Plane Shift
    public static boolean canUsePowerWordPlaneShift(Game game, Player player) {
        return game != null
                && player != null
                && player == game.getActivePlayer()
                && player.hasTechReady(PLANE_SHIFT)
                && hasFourTechsMatchingPrimordial(player, PLANE_SHIFT)
                && game.getTileMap().values().stream().anyMatch(ArcanumPrimordialTechHandler::isPlaneShiftSystem);
    }

    public static void resolvePowerWordPlaneShift(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null
                || player == null
                || player != game.getActivePlayer()
                || !player.hasTech(PLANE_SHIFT)
                || !player.getExhaustedTechs().contains(PLANE_SHIFT)
                || !hasFourTechsMatchingPrimordial(player, PLANE_SHIFT)) {
            return;
        }

        List<Button> buttons = getPlaneShiftSystemButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "No system without planets is available for _Power Word: Plane Shift_.");
            return;
        }

        String prefix = player.factionButtonChecker() + PLANE_SHIFT_SELECT_SYSTEM;
        game.setStoredValue(PLANE_SHIFT_PENDING, player.getFaction());
        List<Button> displayedButtons =
                buttons.size() <= 25 ? buttons : NewStuffHelper.buttonPagination(buttons, null, prefix, 25, 0, false);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", choose the planetless system to activate with _Power Word: Plane Shift_.\n"
                        + "-# This tactical action does not spend or place a command token, and you ignore anomaly effects.",
                displayedButtons);
    }

    @ButtonHandler(PLANE_SHIFT_SELECT_SYSTEM)
    public static void selectPlaneShiftSystem(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null
                || player == null
                || player != game.getActivePlayer()
                || !player.hasTech(PLANE_SHIFT)
                || !player.getExhaustedTechs().contains(PLANE_SHIFT)
                || !player.getFaction().equalsIgnoreCase(game.getStoredValue(PLANE_SHIFT_PENDING))
                || !hasFourTechsMatchingPrimordial(player, PLANE_SHIFT)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = getPlaneShiftSystemButtons(game, player);
        String prefix = player.factionButtonChecker() + PLANE_SHIFT_SELECT_SYSTEM;
        String message = player.getRepresentation()
                + ", choose the planetless system to activate with _Power Word: Plane Shift_.\n"
                + "-# This tactical action does not spend or place a command token, and you ignore anomaly effects.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        String position = buttonID.substring(PLANE_SHIFT_SELECT_SYSTEM.length());
        Tile tile = game.getTileByPosition(position);
        if (!isPlaneShiftSystem(tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        // The normal tactical-action handler handles every subsequent step; its activation-cost hook recognizes
        // this scoped state and skips only Plane Shift's command-token placement.
        game.removeStoredValue(PLANE_SHIFT_PENDING);
        game.setStoredValue(PLANE_SHIFT_PLAYER, player.getFaction());
        game.setStoredValue(PLANE_SHIFT_SYSTEM, position);
        ButtonHelperTacticalAction.selectActiveSystem(player, game, event, "ringTile_" + position);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + " activated "
                        + tile.getRepresentationForButtons(game, player)
                        + " with _Power Word: Plane Shift_ without spending a command token. Anomaly effects are ignored for this tactical action.");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean planeShiftIgnoresAnomalies(Game game, Player player) {
        String activeSystem = game == null ? null : game.getActiveSystem();
        return game != null
                && player != null
                && player.getFaction().equalsIgnoreCase(game.getStoredValue(PLANE_SHIFT_PLAYER))
                && activeSystem != null
                && activeSystem.equalsIgnoreCase(game.getStoredValue(PLANE_SHIFT_SYSTEM));
    }

    public static void clearPowerWordPlaneShift(Game game) {
        if (game != null) {
            game.removeStoredValue(PLANE_SHIFT_PENDING);
            game.removeStoredValue(PLANE_SHIFT_PLAYER);
            game.removeStoredValue(PLANE_SHIFT_SYSTEM);
        }
    }

    private static List<Button> getPlaneShiftSystemButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (isPlaneShiftSystem(tile)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + PLANE_SHIFT_SELECT_SYSTEM + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player),
                        tile.getTileEmoji(player)));
            }
        }
        return buttons;
    }

    private static boolean isPlaneShiftSystem(Tile tile) {
        return tile != null
                && tile.getTileModel() != null
                && !tile.getTileModel().isHyperlane()
                && !"silver_flame".equalsIgnoreCase(tile.getTileID())
                && tile.getPlanetUnitHolders().isEmpty();
    }

    // Power Word: Disintegrate
    public static List<Button> getDisintegrateCombatButtons(
            Player player, Player opponent, Tile tile, UnitHolder combatHolder) {
        if (player == null
                || opponent == null
                || tile == null
                || combatHolder == null
                || !player.hasTech(DISINTEGRATE)
                || !hasFourTechsMatchingPrimordial(player, DISINTEGRATE)) {
            return List.of();
        }

        String payload = tile.getPosition() + "|" + combatHolder.getName() + "|" + opponent.getFaction();
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red(
                player.factionButtonChecker() + DISINTEGRATE_HIT + payload, "Produce 1 Hit", TechEmojis.WarfareTech));
        if (player.hasTechReady(DISINTEGRATE)) {
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + DISINTEGRATE_DESTROY + payload,
                    "Exhaust Power Word: Disintegrate",
                    TechEmojis.WarfareTech));
        }
        return buttons;
    }

    @ButtonHandler(DISINTEGRATE_HIT)
    public static void produceDisintegrateHit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        DisintegrateCombat combat = getDisintegrateCombat(game, player, buttonID, DISINTEGRATE_HIT);
        if (combat == null
                || !player.hasTech(DISINTEGRATE)
                || !hasFourTechsMatchingPrimordial(player, DISINTEGRATE)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        boolean spaceCombat = "space".equals(combat.holder().getName());
        List<Button> buttons = new ArrayList<>();
        if (spaceCombat) {
            buttons.add(Buttons.green(
                    combat.opponent().factionButtonChecker()
                            + "autoAssignSpaceHits_"
                            + combat.tile().getPosition()
                            + "_1",
                    "Auto-assign 1 Hit"));
            buttons.add(Buttons.red(
                    combat.opponent().factionButtonChecker()
                            + "getDamageButtons_"
                            + combat.tile().getPosition()
                            + "deleteThis_spacecombat",
                    "Manually Assign 1 Hit"));
        } else {
            buttons.add(Buttons.green(
                    combat.opponent().factionButtonChecker()
                            + "autoAssignGroundHits_"
                            + combat.holder().getName()
                            + "_1",
                    "Auto-assign 1 Hit"));
            buttons.add(Buttons.red(
                    combat.opponent().factionButtonChecker()
                            + "getDamageButtons_"
                            + combat.tile().getPosition()
                            + "deleteThis_groundcombat",
                    "Manually Assign 1 Hit"));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                combat.opponent().getRepresentationNoPing() + ", assign 1 hit from _Power Word: Disintegrate_.",
                buttons);
    }

    @ButtonHandler(DISINTEGRATE_DESTROY)
    public static void exhaustDisintegrateToDestroyUnit(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        DisintegrateCombat combat = getDisintegrateCombat(game, player, buttonID, DISINTEGRATE_DESTROY);
        if (combat == null
                || !player.hasTechReady(DISINTEGRATE)
                || !hasFourTechsMatchingPrimordial(player, DISINTEGRATE)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey : combat.holder().getUnitKeysForPlayer(combat.opponent())) {
            UnitModel unit = combat.opponent().getUnitFromUnitKey(unitKey);
            if (!isDisintegrateCombatUnit(combat.holder(), unit)) {
                continue;
            }
            for (UnitState state : UnitState.values()) {
                if (combat.holder().getUnitCountForState(unitKey, state) < 1) {
                    continue;
                }
                String stateLabel = state == UnitState.none ? "" : state.humanDescr() + " ";
                buttons.add(Buttons.red(
                        combat.opponent().factionButtonChecker()
                                + DISINTEGRATE_DESTROY_UNIT
                                + combat.tile().getPosition()
                                + "|"
                                + combat.holder().getName()
                                + "|"
                                + player.getFaction()
                                + "|"
                                + unitKey.unitType().value
                                + "|"
                                + state.name(),
                        "Destroy " + stateLabel + unitKey.humanReadableName()));
            }
        }
        if (buttons.isEmpty()) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        player.exhaustTech(DISINTEGRATE);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                combat.opponent().getRepresentationNoPing()
                        + ", choose 1 participating unit to destroy due to _Power Word: Disintegrate_.",
                buttons);
    }

    @ButtonHandler(DISINTEGRATE_DESTROY_UNIT)
    public static void destroyDisintegrateUnit(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(DISINTEGRATE_DESTROY_UNIT.length()).split("\\|", 5);
        Tile tile = payload.length == 5 ? game.getTileByPosition(payload[0]) : null;
        UnitHolder holder = tile == null || payload.length != 5
                ? null
                : tile.getUnitHolders().get(payload[1]);
        Player techOwner = payload.length == 5 ? game.getPlayerFromColorOrFaction(payload[2]) : null;
        UnitType unitType = payload.length == 5 ? Units.findUnitType(payload[3]) : null;
        UnitState state = payload.length == 5 ? Units.findUnitState(payload[4]) : null;
        if (tile == null
                || holder == null
                || techOwner == null
                || !techOwner.hasTech(DISINTEGRATE)
                || !hasFourTechsMatchingPrimordial(techOwner, DISINTEGRATE)
                || unitType == null
                || state == null
                || !isDisintegrateCombat(game, techOwner, player, tile, holder)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        UnitKey unitKey = holder.getUnitKeysForPlayer(player).stream()
                .filter(key -> key.unitType() == unitType)
                .findFirst()
                .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getUnitFromUnitKey(unitKey);
        if (unitKey == null
                || !isDisintegrateCombatUnit(holder, unit)
                || holder.getUnitCountForState(unitKey, state) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        DestroyUnitService.destroyUnit(event, tile, game, new ParsedUnit(unitKey, 1, holder.getName()), true, state);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " destroyed 1 "
                        + unitKey.humanReadableName().toLowerCase() + " due to _Power Word: Disintegrate_.");
    }

    private static DisintegrateCombat getDisintegrateCombat(
            Game game, Player player, String buttonID, String buttonPrefix) {
        String[] payload = buttonID.substring(buttonPrefix.length()).split("\\|", 3);
        Tile tile = payload.length == 3 ? game.getTileByPosition(payload[0]) : null;
        UnitHolder holder = tile == null || payload.length != 3
                ? null
                : tile.getUnitHolders().get(payload[1]);
        Player opponent = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[2]) : null;
        return isDisintegrateCombat(game, player, opponent, tile, holder)
                ? new DisintegrateCombat(tile, holder, opponent)
                : null;
    }

    private static boolean isDisintegrateCombat(
            Game game, Player player, Player opponent, Tile tile, UnitHolder holder) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        return combat != null
                && player != null
                && opponent != null
                && player != opponent
                && tile != null
                && holder != null
                && combat.factions().contains(player.getFaction())
                && combat.factions().contains(opponent.getFaction())
                && (combat.tilePosition() == null || combat.tilePosition().equals(tile.getPosition()))
                && (combat.unitHolderName() == null || combat.unitHolderName().equals(holder.getName()))
                && holder.getUnitKeysForPlayer(player).stream()
                        .map(player::getUnitFromUnitKey)
                        .anyMatch(unit -> isDisintegrateCombatUnit(holder, unit))
                && holder.getUnitKeysForPlayer(opponent).stream()
                        .map(opponent::getUnitFromUnitKey)
                        .anyMatch(unit -> isDisintegrateCombatUnit(holder, unit));
    }

    private static boolean isDisintegrateCombatUnit(UnitHolder holder, UnitModel unit) {
        return unit != null && ("space".equals(holder.getName()) ? unit.getIsShip() : unit.getIsGroundForce());
    }

    private record DisintegrateCombat(Tile tile, UnitHolder holder, Player opponent) {}

    // Power Word: Fabricate station ability
    public static void offerFabricateStationProduction(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!canUseFabricateStation(game, player)) {
            return;
        }
        if (!game.getStoredValue("producedUnitCostFor" + player.getFaction()).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + " must finish paying for their existing production before using **Mass Production**.");
            return;
        }
        // Completed productions intentionally retain this list for their summary; Fabricate starts a new build.
        player.resetProducedUnits();

        List<Button> sourceButtons = getFabricateSourceButtons(game, player);
        if (sourceButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " has no system with PRODUCTION available for **Mass Production**.");
            return;
        }

        String message = player.getRepresentation()
                + ", please choose the system whose PRODUCTION you will use for **Mass Production**.";
        String buttonPrefix = player.factionButtonChecker() + FABRICATE_SELECT_SOURCE;
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                sourceButtons.size() < 25
                        ? sourceButtons
                        : NewStuffHelper.buttonPagination(sourceButtons, null, buttonPrefix, 25, 0, false));
    }

    @ButtonHandler(FABRICATE_SELECT_SOURCE)
    public static void selectFabricateStationSource(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!canUseFabricateStation(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> sourceButtons = getFabricateSourceButtons(game, player);
        String message = player.getRepresentation()
                + ", please choose the system whose PRODUCTION you will use for **Mass Production**.";
        String buttonPrefix = player.factionButtonChecker() + FABRICATE_SELECT_SOURCE;
        String payload = buttonID.substring(FABRICATE_SELECT_SOURCE.length());
        if (payload.startsWith("page")) {
            sendFabricatePage(event, message, sourceButtons, buttonPrefix, payload.substring("page".length()), null);
            return;
        }

        Tile source = game.getTileByPosition(payload);
        if (!isFabricateSource(game, player, source)
                || getFabricateSystemButtons(game, player, source).isEmpty()) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + " no longer has an eligible **Mass Production** source or destination.");
            return;
        }

        ButtonHelper.deleteMessage(event);
        sendFabricateSystemButtons(event, game, player, source);
    }

    @ButtonHandler(FABRICATE_SELECT_SYSTEM)
    public static void selectFabricateStationSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!canUseFabricateStation(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String payload = buttonID.substring(FABRICATE_SELECT_SYSTEM.length());
        String[] systems = payload.split("\\|", 2);
        Tile source = systems.length == 2 ? game.getTileByPosition(systems[0]) : null;
        if (!isFabricateSource(game, player, source)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> systemButtons = getFabricateSystemButtons(game, player, source);
        String message = getFabricateSystemMessage(game, player, source);
        String buttonPrefix = player.factionButtonChecker() + FABRICATE_SELECT_SYSTEM + source.getPosition() + "|";
        String selection = systems[1];
        if (selection.startsWith("page")) {
            sendFabricatePage(event, message, systemButtons, buttonPrefix, selection.substring("page".length()), null);
            return;
        }
        if ("choose".equals(selection)) {
            sendFabricateSystemButtons(event, game, player, source);
            return;
        }

        Tile destination = game.getTileByPosition(selection);
        if (!isFabricateDestination(game, player, destination)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        sendFabricateProductionButtons(event, game, player, source, destination, 0);
    }

    @ButtonHandler(FABRICATE_PLACE_UNIT)
    public static void placeFabricateStationUnit(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!canUseFabricateStation(game, player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String[] placement = buttonID.substring(FABRICATE_PLACE_UNIT.length()).split("\\|", 3);
        Tile source = placement.length == 3 ? game.getTileByPosition(placement[0]) : null;
        Tile destination = placement.length == 3 ? game.getTileByPosition(placement[1]) : null;
        if (!isFabricateSource(game, player, source) || !isFabricateDestination(game, player, destination)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (placement[2].startsWith("page")) {
            try {
                sendFabricateProductionButtons(
                        event,
                        game,
                        player,
                        source,
                        destination,
                        Integer.parseInt(placement[2].substring("page".length())));
            } catch (NumberFormatException e) {
                ButtonHelper.deleteMessage(event);
            }
            return;
        }

        String standardPayload = placement[2].startsWith("_") ? placement[2].substring(1) : placement[2];
        int amount = getFabricateProducedUnitAmount(standardPayload);
        if (!isFabricatePlacementForDestination(game, destination, standardPayload)
                || amount < 1
                || getFabricateProducedUnitCount(player) + amount > getFabricateProductionLimit(game, player, source)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " cannot exceed the source system's PRODUCTION value of "
                            + getFabricateProductionLimit(game, player, source) + ".");
            return;
        }

        ButtonHelperModifyUnits.genericPlaceUnit("place_" + standardPayload, event, game, player);
    }

    private static void sendFabricateSystemButtons(
            GenericInteractionCreateEvent event, Game game, Player player, Tile source) {
        List<Button> buttons = getFabricateSystemButtons(game, player, source);
        String message = getFabricateSystemMessage(game, player, source);
        String buttonPrefix = player.factionButtonChecker() + FABRICATE_SELECT_SYSTEM + source.getPosition() + "|";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                buttons.size() < 25
                        ? buttons
                        : NewStuffHelper.buttonPagination(buttons, null, buttonPrefix, 25, 0, false));
    }

    private static void sendFabricateProductionButtons(
            GenericInteractionCreateEvent event, Game game, Player player, Tile source, Tile destination, int page) {
        Map<String, Integer> producedUnits = new HashMap<>(player.getCurrentProducedUnits());
        String placePrefix = FABRICATE_PLACE_UNIT + source.getPosition() + "|" + destination.getPosition() + "|";
        List<Button> productionButtons =
                Helper.getPlaceUnitButtons(event, player, game, destination, "fabricateStation", placePrefix);
        player.resetProducedUnits();
        producedUnits.forEach(player::setProducedUnit);

        List<Button> extraButtons = List.of(
                Buttons.blue(
                        player.factionButtonChecker() + FABRICATE_SELECT_SYSTEM + source.getPosition() + "|choose",
                        "Choose Another System"),
                Buttons.red(
                        player.factionButtonChecker() + "deleteButtons_" + source.getPosition(),
                        "Done Producing Units"),
                Buttons.gray(player.factionButtonChecker() + "resetProducedThings", "Reset Build"));
        String message = player.getRepresentation() + ", use the normal production buttons to produce in "
                + destination.getRepresentationForButtons(game, player) + ". Your shared **Mass Production** limit is "
                + getFabricateProductionLimit(game, player, source) + " from "
                + source.getRepresentationForButtons(game, player) + ".";
        String buttonPrefix = player.factionButtonChecker() + placePrefix;
        List<Button> buttons = productionButtons.size() + extraButtons.size() <= 25
                ? appendFabricateButtons(productionButtons, extraButtons)
                : NewStuffHelper.buttonPagination(productionButtons, extraButtons, buttonPrefix, 25, page, false);
        NewStuffHelper.sendOrEditButtons(event, event.getMessageChannel(), message, buttons);
    }

    private static List<Button> getFabricateSystemButtons(Game game, Player player, Tile source) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (isFabricateDestination(game, player, tile)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + FABRICATE_SELECT_SYSTEM + source.getPosition() + "|"
                                + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    private static String getFabricateSystemMessage(Game game, Player player, Tile source) {
        return player.getRepresentation() + ", choose a system in which to use **Mass Production** from "
                + source.getRepresentationForButtons(game, player)
                + ". The system must contain your ships and no other player's units.";
    }

    private static boolean isFabricateSource(Game game, Player player, Tile tile) {
        return tile != null && getFabricateProductionLimit(game, player, tile) > 0;
    }

    private static int getFabricateProductionLimit(Game game, Player player, Tile source) {
        int production = Helper.getProductionValue(player, game, source, false);
        if (player.getSpentThingsThisWindow().contains("warmachine")) {
            production += 4;
        }
        for (String spent : player.getSpentThingsThisWindow()) {
            if (spent.startsWith("liquidation")
                    && !spent.replace("liquidation", "").isEmpty()) {
                production -= Integer.parseInt(spent.replace("liquidation", ""));
            }
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "cabalcommander")) {
            production += 2;
        }
        return Math.max(0, production);
    }

    private static int getFabricateProducedUnitCount(Player player) {
        return player.getCurrentProducedUnits().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }

    private static int getFabricateProducedUnitAmount(String payload) {
        int separator = payload.indexOf('_');
        if (separator < 1) {
            return 0;
        }
        String unit = payload.substring(0, separator);
        return "2ff".equals(unit) || "2destroyer".equals(unit) || "2gf".equals(unit) ? 2 : 1;
    }

    private static boolean isFabricatePlacementForDestination(Game game, Tile destination, String payload) {
        int separator = payload.indexOf('_');
        if (separator < 1) {
            return false;
        }
        String location = payload.substring(separator + 1);
        if (location.startsWith("space")) {
            return destination.getPosition().equals(location.substring("space".length()));
        }
        if (destination.getPosition().equals(location)) {
            return true;
        }
        Tile planetTile = game.getTileFromPlanet(location);
        return planetTile == destination;
    }

    private static List<Button> appendFabricateButtons(List<Button> buttons, List<Button> extraButtons) {
        List<Button> allButtons = new ArrayList<>(buttons);
        allButtons.addAll(extraButtons);
        return allButtons;
    }

    private static boolean canUseFabricateStation(Game game, Player player) {
        return game != null
                && player != null
                && player.hasTech(FABRICATE_TECH)
                && player.getPlanets().contains(FABRICATE_STATION)
                && player.getExhaustedPlanetsAbilities().contains(FABRICATE_STATION);
    }

    private static List<Button> getFabricateSourceButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            int production = getFabricateProductionLimit(game, player, tile);
            if (production > 0) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + FABRICATE_SELECT_SOURCE + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player) + " (PRODUCTION " + production + ")"));
            }
        }
        return buttons;
    }

    private static boolean isFabricateDestination(Game game, Player player, Tile tile) {
        return tile != null
                && FoWHelper.playerHasShipsInSystem(player, tile)
                && !FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game);
    }

    private static void sendFabricatePage(
            ButtonInteractionEvent event,
            String message,
            List<Button> buttons,
            String buttonPrefix,
            String pageValue,
            Button extraButton) {
        try {
            int page = Integer.parseInt(pageValue);
            List<Button> extraButtons = extraButton == null ? null : List.of(extraButton);
            NewStuffHelper.sendOrEditButtons(
                    event,
                    event.getMessageChannel(),
                    message,
                    NewStuffHelper.buttonPagination(buttons, extraButtons, buttonPrefix, 25, page, false));
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
        }
    }
}
