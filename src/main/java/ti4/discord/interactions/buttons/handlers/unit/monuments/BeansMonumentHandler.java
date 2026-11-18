package ti4.discord.interactions.buttons.handlers.unit.monuments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.bloom.BloomAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.deep.DeepUnitHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.adjacency.Adjacency;
import ti4.message.MessageHelper;
import ti4.model.AttachmentModel;
import ti4.model.Mapper;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.game.MonumentsService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class BeansMonumentHandler {
    private static final String PLACE_DREAM_MONUMENT = "placeDreamMonument_";
    private static final String PLACE_DEEP_MONUMENT = "placeDeepMonument_";
    private static final String USE_TA_MONUMENT = "useTaMonument";
    private static final String SELECT_TA_SOURCE = "selectTaMonumentSource_";
    private static final String SELECT_TA_ATTACHMENT = "selectTaMonumentAttachment_";
    private static final String SELECT_TA_DESTINATION = "selectTaMonumentDestination_";
    private static final String TA_STATE = "taMonument_";
    private static final String CRYSTELLUM_STATE = "crystellumMonument_";
    private static final String USE_HEARTH_HOME = "useHearthHome";
    private static final String PLACE_HEARTH_HOME_INFANTRY = "placeHearthHomeInfantry_";
    private static final String USE_CRYSTELLUM_MONUMENT = "useCrystellumMonument";
    private static final String READY_CRYSTELLUM_PLANET = "readyCrystellumPlanet_";
    private static final String USE_PLATEAU_OF_LENG = "usePlateauOfLeng";
    private static final String PLACE_PLATEAU_OF_LENG_SHIP = "placePlateauOfLengShip_";
    private static final String DREAM_STATE = "dreamMonument_";
    private static final String USE_IRON_TIDE = "useIronTide_";
    private static final String IRON_TIDE_PRODUCTION = "ironTideProduction_";
    private static final String USE_NATAU_MONUMENT = "useNatauMonument_";
    private static final String NATAU_IGNORED_ANOMALY_TILE = "natauMonumentIgnoredAnomalyTile";
    private static final String NATAU_IGNORED_ANOMALY_PLAYER = "natauMonumentIgnoredAnomalyPlayer";
    private static final String MOVE_VALHAVN_MONUMENT_PDS = "moveValhavnMonumentPds_";
    private static final String USE_MIGO_MONUMENT_EXPLORE = "useMigoMonumentExplore_";
    private static final String REPAIR_DEEP_MONUMENT_DEEP = "repairDeepMonumentDeep_";
    private static final String USE_BLOOM_MONUMENT = "useBloomMonument";
    private static final String SELECT_BLOOM_MONUMENT_PLAYER = "selectBloomMonumentPlayer_";
    private static final String SELECT_BLOOM_MONUMENT_PLANET = "selectBloomMonumentPlanet_";
    private static final String USE_CHOIR_MONUMENT = "useChoirMonument";
    private static final String SELECT_CHOIR_MONUMENT_PLAYER = "selectChoirMonumentPlayer_";
    private static final String SELECT_CHOIR_MONUMENT_SYSTEM = "selectChoirMonumentSystem_";
    private static final String REPAIR_CHOIR_MONUMENT_SHIP = "repairChoirMonumentShip_";
    private static final String SELECT_CHOIR_MONUMENT_MODE = "selectChoirMonumentMode_";
    private static final String RESOLVE_CHOIR_MONUMENT_SHIP = "resolveChoirMonumentShip_";

    public static Button getTaMonumentButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + USE_TA_MONUMENT, "Use The Exchange");
    }

    public static Button getCrystellumMonumentButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + USE_CRYSTELLUM_MONUMENT, "Use The Glassway");
    }

    public static Button getBloomMonumentButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + USE_BLOOM_MONUMENT, "Use Symbiote", FactionEmojis.bloom);
    }

    public static Button getChoirMonumentButton(Player player) {
        return Buttons.green(
                player.factionButtonChecker() + USE_CHOIR_MONUMENT, "Use Silent Soliloquy", FactionEmojis.choir);
    }

    public static void addHearthHomeCombatButton(List<Button> buttons, Game game, Player player) {
        if (MonumentsService.isMonumentOnBoard(game, player, "ashen_monument")
                && player.getNombox().getUnitCount(UnitType.Infantry, player) > 0) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + USE_HEARTH_HOME, "Use Hearth-Home", FactionEmojis.ashen));
        }
    }

    public static void addPlateauOfLengCombatButton(List<Button> buttons, Game game, Player player) {
        if (MonumentsService.isMonumentReady(game, player, "dream_monument")) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + USE_PLATEAU_OF_LENG, "Use Plateau of Leng", FactionEmojis.dream));
        }
    }

    public static void addEtherAnchorCombatButton(List<Button> buttons, Game game, Player player, Tile tile) {
        UnitKey deepKey = DeepUnitHandler.getUnitKey(player);
        if (tile == null
                || !MonumentsService.isMonumentReady(game, player, "deep_monument")
                || MonumentsService.getMonumentTile(game, player, "deep_monument") != tile
                || tile.getSpaceUnitHolder().getDamagedUnitCount(deepKey) < 1) {
            return;
        }
        buttons.add(Buttons.gray(
                player.factionButtonChecker() + REPAIR_DEEP_MONUMENT_DEEP + tile.getPosition(),
                "Repair The Deep (End of Combat)",
                FactionEmojis.deep));
    }

    public static void offerValhavnMonumentPdsMove(Game game, Player player, Tile activatedTile) {
        List<Button> buttons = getValhavnMonumentPdsMoveButtons(game, player, activatedTile);
        if (buttons.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", you may move 1 PDS from an adjacent planet onto _Himinbjörg_'s planet.",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + MOVE_VALHAVN_MONUMENT_PDS, 0));
    }

    public static boolean isValhavnMonumentPlanet(Game game, Player player, String planetName) {
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "valhavn_monument");
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        return monumentTile != null
                && planet != null
                && monumentTile.getPlanetUnitHolders().contains(planet)
                && planet.getUnitCount(UnitType.Monument, player) > 0;
    }

    public static void offerMigoMonumentExplore(Game game, Player player) {
        Tile activeTile = game.getTileByPosition(game.getActiveSystem());
        List<Button> buttons = getMigoMonumentExploreButtons(game, player, activeTile);
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.DECLINE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", you may exhaust _The Cyst_ and remove 1 infantry from a planet you control in the active system to explore it.",
                buttons);
    }

    public static void clearIronTideProduction(Player player) {
        player.getSpentThingsThisWindow().removeIf(spentThing -> spentThing.startsWith(IRON_TIDE_PRODUCTION));
    }

    public static void offerIronTideProduction(Game game, Player producingPlayer, Tile productionTile) {
        if (producingPlayer.getTg() < 1 || productionTile == null) {
            return;
        }
        for (Player monumentOwner : game.getRealPlayersExcludingThis(producingPlayer)) {
            if (!isEligibleIronTideProduction(game, producingPlayer, monumentOwner, productionTile)) {
                continue;
            }
            String buttonId = producingPlayer.factionButtonChecker()
                    + USE_IRON_TIDE
                    + monumentOwner.getFaction()
                    + "_"
                    + productionTile.getPosition();
            String message = game.isFowMode()
                    ? producingPlayer.getRepresentationNoPing()
                            + ", you may give 1 trade good to gain +1 PRODUCTION and -2 cost for this production."
                    : producingPlayer.getRepresentationNoPing()
                            + ", you may give "
                            + monumentOwner.getRepresentationNoPing()
                            + " 1 trade good to gain +1 PRODUCTION and -2 cost for this production.";
            MessageHelper.sendMessageToChannelWithButtons(
                    producingPlayer.getCorrectChannel(),
                    message,
                    List.of(Buttons.green(buttonId, "Use The Breaker's Yard"), Buttons.DECLINE_DELETE_BUTTONS));
        }
    }

    public static int getIronTideProductionBonus(Player player, Tile tile) {
        return player.getSpentThingsThisWindow().contains(ironTideProductionKey(tile)) ? 1 : 0;
    }

    public static int getIronTideProductionDiscount(Game game, Player player, Map<String, Integer> producedUnits) {
        return producedUnits.keySet().stream()
                        .map(producedUnit -> producedUnit.split("_", 3))
                        .filter(parts -> parts.length == 3)
                        .map(parts -> game.getTileByPosition(parts[1]))
                        .filter(Objects::nonNull)
                        .anyMatch(tile -> player.getSpentThingsThisWindow().contains(ironTideProductionKey(tile)))
                ? 2
                : 0;
    }

    public static void offerNetrunnersMonumentCancellation(Game game, Player actionCardPlayer, String actionCardTitle) {
        for (Player monumentOwner : game.getRealPlayersExcludingThis(actionCardPlayer)) {
            if (!canUseNetrunnersMonumentCancellation(game, monumentOwner, actionCardPlayer)) {
                continue;
            }
            List<Button> buttons = List.of(
                    Buttons.gray(
                            "sabotage_netrunners_" + actionCardTitle + "_" + actionCardPlayer.getFaction(),
                            "Cancel " + actionCardTitle + " With Ghost Protocol"),
                    Buttons.DELETE_BUTTONS);
            MessageHelper.sendMessageToChannelWithButtons(
                    monumentOwner.getCardsInfoThread(),
                    monumentOwner.getRepresentationUnfogged()
                            + ", you may exhaust _Ghost Protocol_ and return "
                            + actionCardPlayer.getFactionEmojiOrColor()
                            + "'s control token from the Hacker Man pool to cancel _"
                            + actionCardTitle
                            + "_.",
                    buttons);
        }
    }

    public static boolean canUseNetrunnersMonumentCancellation(
            Game game, Player monumentOwner, Player actionCardPlayer) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        Tile monumentTile = MonumentsService.getMonumentTile(game, monumentOwner, "netrunners_monument");
        Tile combatTile = combat == null
                ? null
                : game.getTileByPosition(
                        combat.tilePosition() == null ? game.getActiveSystem() : combat.tilePosition());
        return monumentOwner != actionCardPlayer
                && MonumentsService.isMonumentReady(game, monumentOwner, "netrunners_monument")
                && monumentOwner.getDebtTokenCount(actionCardPlayer.getColor(), Constants.NETRUNNERS_DEBT_POOL) > 0
                && monumentTile != null
                && combatTile != null
                && Adjacency.of(game, monumentOwner)
                        .forEffects()
                        .withSelf()
                        .from(monumentTile.getPosition())
                        .contains(combatTile.getPosition());
    }

    public static boolean resolveNetrunnersMonumentCancellation(
            Game game, Player monumentOwner, Player actionCardPlayer) {
        if (!canUseNetrunnersMonumentCancellation(game, monumentOwner, actionCardPlayer)
                || !MonumentsService.exhaustMonument(game, monumentOwner, "netrunners_monument")) {
            return false;
        }
        monumentOwner.removeDebtTokens(actionCardPlayer.getColor(), 1, Constants.NETRUNNERS_DEBT_POOL);
        return true;
    }

    public static void offerNatauMonumentAnomaly(Game game, Player activatingPlayer, Tile activeTile) {
        if (activatingPlayer.getTg() < 1 || activeTile == null) {
            return;
        }
        for (Player monumentOwner : game.getRealPlayersExcludingThis(activatingPlayer)) {
            if (!canUseNatauMonumentAnomaly(game, activatingPlayer, monumentOwner, activeTile)) {
                continue;
            }
            String buttonId = activatingPlayer.factionButtonChecker()
                    + USE_NATAU_MONUMENT
                    + monumentOwner.getFaction()
                    + "|"
                    + activeTile.getPosition();
            String message = game.isFowMode()
                    ? activatingPlayer.getRepresentationNoPing()
                            + ", you may give 1 trade good to ignore this anomaly's effects during this tactical action."
                    : activatingPlayer.getRepresentationNoPing()
                            + ", you may give "
                            + monumentOwner.getRepresentationNoPing()
                            + " 1 trade good to ignore this anomaly's effects during this tactical action.";
            MessageHelper.sendMessageToChannelWithButtons(
                    activatingPlayer.getCorrectChannel(),
                    message,
                    List.of(Buttons.green(buttonId, "Use Amaluun's Blessing"), Buttons.DECLINE_DELETE_BUTTONS));
        }
    }

    public static boolean playerIgnoresNatauMonumentAnomaly(Game game, Player player, Tile tile) {
        return game != null
                && player != null
                && tile != null
                && tile.getPosition().equalsIgnoreCase(game.getStoredValue(NATAU_IGNORED_ANOMALY_TILE))
                && player.getFaction().equalsIgnoreCase(game.getStoredValue(NATAU_IGNORED_ANOMALY_PLAYER));
    }

    public static void clearNatauMonumentAnomaly(Game game) {
        game.removeStoredValue(NATAU_IGNORED_ANOMALY_TILE);
        game.removeStoredValue(NATAU_IGNORED_ANOMALY_PLAYER);
    }

    public static List<Button> getDreamMonumentPlacementButtons(Game game, Player player) {
        return getSpacePlacementButtons(
                game,
                player,
                "dream_monument",
                PLACE_DREAM_MONUMENT,
                tile -> FoWHelper.playerHasActualShipsInSystem(player, tile));
    }

    public static List<Button> getDeepMonumentPlacementButtons(Game game, Player player) {
        return getSpacePlacementButtons(
                game,
                player,
                "deep_monument",
                PLACE_DEEP_MONUMENT,
                tile -> DeepUnitHandler.getUnitTile(game, player) == tile);
    }

    @ButtonHandler(PLACE_DREAM_MONUMENT)
    public static void placeDreamMonument(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        placeSpaceMonument(
                event,
                game,
                player,
                buttonID,
                "dream_monument",
                PLACE_DREAM_MONUMENT,
                tile -> FoWHelper.playerHasActualShipsInSystem(player, tile));
    }

    @ButtonHandler(PLACE_DEEP_MONUMENT)
    public static void placeDeepMonument(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        placeSpaceMonument(
                event,
                game,
                player,
                buttonID,
                "deep_monument",
                PLACE_DEEP_MONUMENT,
                tile -> DeepUnitHandler.getUnitTile(game, player) == tile);
    }

    @ButtonHandler(USE_TA_MONUMENT)
    public static void useTaMonument(ButtonInteractionEvent event, Game game, Player player) {
        if (!MonumentsService.isMonumentReady(game, player, "ta_monument")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getTaSourceButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "There are no eligible attachments to move with _The Exchange_.");
            return;
        }
        MonumentsService.exhaustMonument(game, player, "ta_monument");
        game.setStoredValue(TA_STATE + player.getFaction(), "started");
        String message = player.getRepresentationNoPing()
                + ", please choose the planet from which to move an attachment with _The Exchange_.";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + SELECT_TA_SOURCE, 0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(USE_HEARTH_HOME)
    public static void useHearthHome(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getHearthHomePlanetButtons(game, player);
        if (buttons.isEmpty() || player.getNombox().getUnitCount(UnitType.Infantry, player) < 1) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", please choose a planet on which to place 1 captured infantry with _Hearth-Home_.",
                NewStuffHelper.buttonPagination(
                        buttons, player.factionButtonChecker() + PLACE_HEARTH_HOME_INFANTRY, 0));
    }

    @ButtonHandler(PLACE_HEARTH_HOME_INFANTRY)
    public static void placeHearthHomeInfantry(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getHearthHomePlanetButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", please choose a planet on which to place 1 captured infantry with _Hearth-Home_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + PLACE_HEARTH_HOME_INFANTRY,
                buttonID)) {
            return;
        }
        String planetName = buttonID.substring(PLACE_HEARTH_HOME_INFANTRY.length());
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (planet == null
                || player.getNombox().getUnitCount(UnitType.Infantry, player) < 1
                || buttons.stream()
                        .noneMatch(button -> button.getCustomId().endsWith(PLACE_HEARTH_HOME_INFANTRY + planetName))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        RemoveUnitService.removeUnits(event, player.getNomboxTile(), game, player.getColor(), "1 infantry");
        AddUnitService.addUnits(
                event, game.getTileFromPlanet(planetName), game, player.getColor(), "1 infantry " + planetName);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed 1 captured infantry on "
                        + Helper.getPlanetRepresentation(planetName, game) + " with _Hearth-Home_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(USE_CRYSTELLUM_MONUMENT)
    public static void useCrystellumMonument(ButtonInteractionEvent event, Game game, Player player) {
        if (!MonumentsService.isMonumentReady(game, player, "crystellum_monument")
                || player.getNombox().getUnitCount(UnitType.Fighter, player) < 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getCrystellumPlanetButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "There are no exhausted planets in or adjacent to _The Glassway_.");
            return;
        }
        MonumentsService.exhaustMonument(game, player, "crystellum_monument");
        RemoveUnitService.removeUnits(event, player.getNomboxTile(), game, player.getColor(), "3 fighter");
        game.setStoredValue(CRYSTELLUM_STATE + player.getFaction(), "pending");
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose an exhausted planet to ready with _The Glassway_.",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + READY_CRYSTELLUM_PLANET, 0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(READY_CRYSTELLUM_PLANET)
    public static void readyCrystellumPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!MonumentsService.isMonumentExhausted(game, player, "crystellum_monument")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!"pending".equals(game.getStoredValue(CRYSTELLUM_STATE + player.getFaction()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getCrystellumPlanetButtons(game, player);
        String message =
                player.getRepresentationNoPing() + ", please choose an exhausted planet to ready with _The Glassway_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + READY_CRYSTELLUM_PLANET,
                buttonID)) {
            return;
        }
        String planetName = buttonID.substring(READY_CRYSTELLUM_PLANET.length());
        if (buttons.stream().noneMatch(button -> button.getCustomId().endsWith(READY_CRYSTELLUM_PLANET + planetName))) {
            game.removeStoredValue(CRYSTELLUM_STATE + player.getFaction());
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.refreshPlanet(planetName);
        game.removeStoredValue(CRYSTELLUM_STATE + player.getFaction());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " readied " + Helper.getPlanetRepresentation(planetName, game)
                        + " with _The Glassway_.");
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(USE_PLATEAU_OF_LENG)
    public static void usePlateauOfLeng(ButtonInteractionEvent event, Game game, Player player) {
        if (!MonumentsService.isMonumentReady(game, player, "dream_monument")) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "dream_monument");
        List<Button> buttons = getPlateauOfLengShipButtons(player);
        if (monumentTile == null || buttons.isEmpty()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        MonumentsService.exhaustMonument(game, player, "dream_monument");
        game.setStoredValue(DREAM_STATE + player.getFaction(), "pending");
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose a ship to place in _Plateau of Leng_'s system.",
                NewStuffHelper.buttonPagination(
                        buttons, player.factionButtonChecker() + PLACE_PLATEAU_OF_LENG_SHIP, 0));
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(USE_IRON_TIDE)
    public static void useIronTide(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(USE_IRON_TIDE.length()).split("_", 2);
        Player monumentOwner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Tile productionTile = payload.length == 2 ? game.getTileByPosition(payload[1]) : null;
        if (monumentOwner == null
                || productionTile == null
                || player.getTg() < 1
                || !isEligibleIronTideProduction(game, player, monumentOwner, productionTile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.setTg(player.getTg() - 1);
        monumentOwner.gainTG(1);
        ButtonHelperAgents.resolveArtunoCheck(monumentOwner, 1);
        player.addSpentThing(ironTideProductionKey(productionTile));
        String message = game.isFowMode()
                ? player.getRepresentationNoPing()
                        + " gave 1 trade good for +1 PRODUCTION and -2 cost with _The Breaker's Yard_."
                : player.getRepresentationNoPing()
                        + " gave 1 trade good to "
                        + monumentOwner.getRepresentationNoPing()
                        + " for +1 PRODUCTION and -2 cost with _The Breaker's Yard_.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(USE_NATAU_MONUMENT)
    public static void useNatauMonument(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(USE_NATAU_MONUMENT.length()).split("\\|", 2);
        Player monumentOwner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Tile activeTile = payload.length == 2 ? game.getTileByPosition(payload[1]) : null;
        if (monumentOwner == null
                || activeTile == null
                || player.getTg() < 1
                || !canUseNatauMonumentAnomaly(game, player, monumentOwner, activeTile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.setTg(player.getTg() - 1);
        monumentOwner.gainTG(1, true);
        game.setStoredValue(NATAU_IGNORED_ANOMALY_TILE, activeTile.getPosition());
        game.setStoredValue(NATAU_IGNORED_ANOMALY_PLAYER, player.getFaction());
        String message = game.isFowMode()
                ? player.getRepresentationNoPing()
                        + " gave 1 trade good to ignore this anomaly's effects during this tactical action."
                : player.getRepresentationNoPing()
                        + " gave 1 trade good to "
                        + monumentOwner.getRepresentationNoPing()
                        + " and will ignore this anomaly's effects during this tactical action.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(MOVE_VALHAVN_MONUMENT_PDS)
    public static void moveValhavnMonumentPds(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(MOVE_VALHAVN_MONUMENT_PDS.length()).split("\\|", 3);
        Tile activatedTile = payload.length == 3 ? game.getTileByPosition(payload[2]) : null;
        List<Button> buttons = getValhavnMonumentPdsMoveButtons(game, player, activatedTile);
        String message = player.getRepresentationNoPing()
                + ", you may move 1 PDS from an adjacent planet onto _Himinbjörg_'s planet.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + MOVE_VALHAVN_MONUMENT_PDS,
                buttonID)) {
            return;
        }
        if (payload.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Planet sourcePlanet = game.getUnitHolderFromPlanet(payload[0]);
        Planet monumentPlanet = game.getUnitHolderFromPlanet(payload[1]);
        Tile sourceTile = game.getTileFromPlanet(payload[0]);
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "valhavn_monument");
        if (!isEligibleValhavnMonumentPdsMove(
                game, player, monumentTile, sourceTile, sourcePlanet, monumentPlanet, activatedTile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<RemovedUnit> removed =
                RemoveUnitService.removeUnit(event, sourceTile, game, player, sourcePlanet, UnitType.Pds, 1);
        if (!removed.isEmpty()) {
            AddUnitService.addUnits(
                    event,
                    game,
                    removed.stream()
                            .map(unit -> unit.onUnitHolder(monumentPlanet))
                            .toList());
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " moved 1 PDS from "
                            + Helper.getPlanetRepresentation(sourcePlanet.getName(), game) + " to "
                            + Helper.getPlanetRepresentation(monumentPlanet.getName(), game) + " with _Himinbjörg_.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(REPAIR_DEEP_MONUMENT_DEEP)
    public static void repairDeepMonumentDeep(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.substring(REPAIR_DEEP_MONUMENT_DEEP.length()));
        UnitKey deepKey = DeepUnitHandler.getUnitKey(player);
        if (tile == null
                || MonumentsService.getMonumentTile(game, player, "deep_monument") != tile
                || tile.getSpaceUnitHolder().getDamagedUnitCount(deepKey) < 1
                || !MonumentsService.exhaustMonument(game, player, "deep_monument")) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
            return;
        }
        tile.getSpaceUnitHolder().removeDamagedUnit(deepKey, 1);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " repaired _The Deep_ with _Ether Anchor_.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event, false);
    }

    public static void moveEtherAnchorWithTheDeep(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Tile sourceTile,
            Tile destinationTile,
            String destinationHolder,
            List<RemovedUnit> movedUnits) {
        if (player == null
                || sourceTile == null
                || destinationTile == null
                || sourceTile == destinationTile
                || !Constants.SPACE.equals(destinationHolder)
                || movedUnits.stream().noneMatch(unit -> unit.unitKey().unitType() == UnitType.Deep)) {
            return;
        }
        moveEtherAnchor(event, game, player, sourceTile, destinationTile);
    }

    private static void moveEtherAnchor(
            GenericInteractionCreateEvent event, Game game, Player player, Tile sourceTile, Tile destinationTile) {
        if (MonumentsService.getMonumentTile(game, player, "deep_monument") != sourceTile) {
            return;
        }
        UnitKey monumentKey = sourceTile.getSpaceUnitHolder().getUnitKeys().stream()
                .filter(unit -> player.unitBelongsToPlayer(unit) && unit.unitType() == UnitType.Monument)
                .findFirst()
                .orElse(null);
        if (monumentKey == null) {
            return;
        }
        List<RemovedUnit> removed =
                RemoveUnitService.removeUnit(event, sourceTile, game, new ParsedUnit(monumentKey, 1, Constants.SPACE));
        AddUnitService.addUnits(
                event,
                game,
                removed.stream()
                        .map(unit -> unit.onUnitHolder(destinationTile.getSpaceUnitHolder()))
                        .toList());
    }

    public static void moveEtherAnchorWithTheDeep(
            GenericInteractionCreateEvent event, Game game, Player player, Tile destinationTile) {
        if (player == null || destinationTile == null) {
            return;
        }
        for (Map.Entry<String, Map<UnitKey, List<Integer>>> entry :
                game.getTacticalActionDisplacement().entrySet()) {
            if (entry.getValue().entrySet().stream()
                    .noneMatch(unit -> player.unitBelongsToPlayer(unit.getKey())
                            && unit.getKey().unitType() == UnitType.Deep
                            && unit.getValue().stream()
                                            .mapToInt(Integer::intValue)
                                            .sum()
                                    > 0)) {
                continue;
            }
            int separator = entry.getKey().indexOf('-');
            if (separator < 1 || !Constants.SPACE.equals(entry.getKey().substring(separator + 1))) {
                continue;
            }
            Tile sourceTile = game.getTileByPosition(entry.getKey().substring(0, separator));
            moveEtherAnchor(event, game, player, sourceTile, destinationTile);
            return;
        }
    }

    @ButtonHandler(USE_MIGO_MONUMENT_EXPLORE)
    public static void useMigoMonumentExplore(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(USE_MIGO_MONUMENT_EXPLORE.length()).split("\\|", 2);
        String planetName = payload.length == 2 ? payload[0] : "";
        Tile activeTile = payload.length == 2 ? game.getTileByPosition(payload[1]) : null;
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (!isEligibleMigoMonumentExplore(game, player, activeTile, planet)
                || !MonumentsService.exhaustMonument(game, player, "migo_monument")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        RemoveUnitService.removeUnit(event, activeTile, game, player, planet, UnitType.Infantry, 1);
        List<Button> exploreButtons = ButtonHelper.getPlanetExplorationButtons(game, planet, player);
        if (!exploreButtons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + ", please explore "
                            + Helper.getPlanetRepresentation(planetName, game) + " with _The Cyst_.",
                    exploreButtons);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(USE_BLOOM_MONUMENT)
    public static void useBloomMonument(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getBloomMonumentPlayerButtons(game, player);
        if (!MonumentsService.isMonumentReady(game, player, "bloom_monument") || buttons.isEmpty()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", please choose the player to resolve _Symbiote_.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_BLOOM_MONUMENT_PLAYER)
    public static void selectBloomMonumentPlayer(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String targetFaction = buttonID.substring(SELECT_BLOOM_MONUMENT_PLAYER.length());
        Player target = game.getPlayerFromColorOrFaction(targetFaction);
        if (!MonumentsService.isMonumentReady(game, player, "bloom_monument")
                || target == null
                || target == player
                || getBloomMonumentPlayerButtons(game, player).stream()
                        .noneMatch(button ->
                                button.getCustomId().endsWith(SELECT_BLOOM_MONUMENT_PLAYER + targetFaction))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getBloomMonumentPlanetButtons(game, player, target);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationNoPing() + ", please choose the exhausted planet to ready with _Symbiote_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_BLOOM_MONUMENT_PLANET)
    public static void selectBloomMonumentPlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(SELECT_BLOOM_MONUMENT_PLANET.length()).split("\\|", 2);
        Player monumentOwner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        String planetName = payload.length == 2 ? payload[1] : "";
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (monumentOwner == null
                || planet == null
                || !isEligibleBloomMonumentPlanet(game, monumentOwner, player, planet)
                || !MonumentsService.exhaustMonument(game, monumentOwner, "bloom_monument")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.refreshPlanet(planetName);
        placeBloomMonumentInfantry(event, game, monumentOwner, planetName);
        BloomAbilityHandler.syncCoexistingAbilities(game, monumentOwner);
        String message = game.isFowMode()
                ? player.getRepresentationNoPing() + " readied " + Helper.getPlanetRepresentation(planetName, game)
                        + " and placed 1 infantry into coexistence there with _Symbiote_."
                : player.getRepresentationNoPing() + " readied " + Helper.getPlanetRepresentation(planetName, game)
                        + " and " + monumentOwner.getRepresentationNoPing()
                        + " placed 1 infantry into coexistence there with _Symbiote_.";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(USE_CHOIR_MONUMENT)
    public static void useChoirMonument(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getChoirMonumentPlayerButtons(game, player);
        if (!MonumentsService.isMonumentReady(game, player, "choir_monument") || buttons.isEmpty()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", please choose the player to resolve _Silent Soliloquy_.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_CHOIR_MONUMENT_PLAYER)
    public static void selectChoirMonumentPlayer(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String targetFaction = buttonID.substring(SELECT_CHOIR_MONUMENT_PLAYER.length());
        Player target = game.getPlayerFromColorOrFaction(targetFaction);
        if (!MonumentsService.isMonumentReady(game, player, "choir_monument")
                || target == null
                || target == player
                || getChoirMonumentPlayerButtons(game, player).stream()
                        .noneMatch(button ->
                                button.getCustomId().endsWith(SELECT_CHOIR_MONUMENT_PLAYER + targetFaction))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getChoirMonumentSystemButtons(game, player, target);
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationNoPing() + ", please select the system in which to resolve _Silent Soliloquy_.",
                NewStuffHelper.buttonPagination(
                        buttons, target.factionButtonChecker() + SELECT_CHOIR_MONUMENT_SYSTEM, 0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_CHOIR_MONUMENT_SYSTEM)
    public static void selectChoirMonumentSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(SELECT_CHOIR_MONUMENT_SYSTEM.length()).split("\\|", 2);
        Player monumentOwner = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        List<Button> systemButtons =
                monumentOwner == null ? List.of() : getChoirMonumentSystemButtons(game, monumentOwner, player);
        String message =
                player.getRepresentationNoPing() + ", please select the system in which to resolve _Silent Soliloquy_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                systemButtons,
                message,
                player.factionButtonChecker() + SELECT_CHOIR_MONUMENT_SYSTEM,
                buttonID)) {
            return;
        }
        Tile selectedTile = payload.length == 2 ? game.getTileByPosition(payload[1]) : null;
        if (monumentOwner == null
                || selectedTile == null
                || systemButtons.stream()
                        .noneMatch(button -> button.getCustomId().endsWith(buttonID))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> repairButtons = getChoirMonumentRepairButtons(game, monumentOwner, player, selectedTile);
        if (repairButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There are no damaged ships you can repair in that system.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose a damaged ship to repair with _Silent Soliloquy_.",
                repairButtons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(REPAIR_CHOIR_MONUMENT_SHIP)
    public static void repairChoirMonumentShip(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(REPAIR_CHOIR_MONUMENT_SHIP.length()).split("\\|", 4);
        Player monumentOwner = payload.length == 4 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        Tile activeTile = payload.length == 4 ? game.getTileByPosition(payload[1]) : null;
        UnitHolder holder = activeTile == null ? null : activeTile.getSpaceUnitHolder();
        UnitKey unitKey = holder == null || payload.length != 4 ? null : findUnitKey(holder, payload[2], payload[3]);
        UnitModel unit = unitKey == null ? null : game.getUnitFromUnitKey(unitKey);
        if (monumentOwner == null
                || activeTile == null
                || monumentOwner == player
                || unit == null
                || !unit.getIsShip()
                || !player.unitBelongsToPlayer(unitKey)
                || holder.getDamagedUnitCount(unitKey) < 1
                || getChoirMonumentRepairButtons(game, monumentOwner, player, activeTile).stream()
                        .noneMatch(button -> button.getCustomId().endsWith(buttonID))
                || !MonumentsService.exhaustMonument(game, monumentOwner, "choir_monument")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        holder.removeDamagedUnit(unitKey, 1);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " repaired 1 " + unit.getName() + " with _Silent Soliloquy_.");
        offerChoirMonumentOwnerShipChoice(game, monumentOwner);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_CHOIR_MONUMENT_MODE)
    public static void selectChoirMonumentMode(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String mode = buttonID.substring(SELECT_CHOIR_MONUMENT_MODE.length());
        boolean repair = "repair".equals(mode);
        boolean damage = "damage".equals(mode);
        List<Button> buttons = repair || damage ? getChoirMonumentOwnerShipButtons(game, player, repair) : List.of();
        if ((!repair && !damage)
                || !MonumentsService.isMonumentOnBoard(game, player, "choir_monument")
                || buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", please choose a ship in _Silent Soliloquy_'s system to "
                        + (repair ? "repair." : "damage."),
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(RESOLVE_CHOIR_MONUMENT_SHIP)
    public static void resolveChoirMonumentShip(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(RESOLVE_CHOIR_MONUMENT_SHIP.length()).split("\\|", 4);
        boolean repair = payload.length == 4 && "repair".equals(payload[0]);
        boolean damage = payload.length == 4 && "damage".equals(payload[0]);
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "choir_monument");
        UnitHolder holder = monumentTile == null ? null : monumentTile.getSpaceUnitHolder();
        UnitKey unitKey = holder == null || payload.length != 4 ? null : findUnitKey(holder, payload[2], payload[3]);
        UnitModel unit = unitKey == null ? null : game.getUnitFromUnitKey(unitKey);
        if ((!repair && !damage)
                || monumentTile == null
                || !MonumentsService.isMonumentOnBoard(game, player, "choir_monument")
                || !monumentTile.getPosition().equals(payload[1])
                || !isEligibleChoirMonumentOwnerShip(player, holder, unitKey, unit, repair)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (repair) {
            holder.removeDamagedUnit(unitKey, 1);
        } else {
            holder.addDamagedUnit(unitKey, 1);
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " " + (repair ? "repaired" : "damaged") + " 1 " + unit.getName()
                        + " with _Silent Soliloquy_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_PLATEAU_OF_LENG_SHIP)
    public static void placePlateauOfLengShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!"pending".equals(game.getStoredValue(DREAM_STATE + player.getFaction()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getPlateauOfLengShipButtons(player);
        String message =
                player.getRepresentationNoPing() + ", please choose a ship to place in _Plateau of Leng_'s system.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + PLACE_PLATEAU_OF_LENG_SHIP,
                buttonID)) {
            return;
        }
        String unitType = buttonID.substring(PLACE_PLATEAU_OF_LENG_SHIP.length());
        UnitModel unit = player.getUnitByBaseType(unitType);
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "dream_monument");
        if (unit == null
                || monumentTile == null
                || buttons.stream()
                        .noneMatch(button -> button.getCustomId().endsWith(PLACE_PLATEAU_OF_LENG_SHIP + unitType))) {
            game.removeStoredValue(DREAM_STATE + player.getFaction());
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, monumentTile, game, player.getColor(), "1 " + unit.getAsyncId());
        game.removeStoredValue(DREAM_STATE + player.getFaction());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed 1 " + unitType + " in _Plateau of Leng_'s system.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_TA_SOURCE)
    public static void selectTaMonumentSource(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getTaSourceButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", please choose the planet from which to move an attachment with _The Exchange_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + SELECT_TA_SOURCE,
                buttonID)) {
            return;
        }
        String sourcePlanetName = buttonID.substring(SELECT_TA_SOURCE.length());
        Planet sourcePlanet = game.getPlanetsInfo().get(sourcePlanetName);
        if (!isEligibleTaSource(game, player, sourcePlanet)) {
            clearTaMonumentAction(game, player);
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(TA_STATE + player.getFaction(), sourcePlanetName);
        List<Button> attachmentButtons = getTaAttachmentButtons(player, sourcePlanet);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose the attachment to move with _The Exchange_.",
                NewStuffHelper.buttonPagination(
                        attachmentButtons, player.factionButtonChecker() + SELECT_TA_ATTACHMENT, 0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_TA_ATTACHMENT)
    public static void selectTaMonumentAttachment(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Planet sourcePlanet = game.getPlanetsInfo().get(game.getStoredValue(TA_STATE + player.getFaction()));
        if (!isEligibleTaSource(game, player, sourcePlanet)) {
            clearTaMonumentAction(game, player);
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getTaAttachmentButtons(player, sourcePlanet);
        String message =
                player.getRepresentationNoPing() + ", please choose the attachment to move with _The Exchange_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + SELECT_TA_ATTACHMENT,
                buttonID)) {
            return;
        }
        int attachmentIndex = NumberUtils.toInt(buttonID.substring(SELECT_TA_ATTACHMENT.length()), -1);
        List<String> attachments = getTaMovableAttachments(sourcePlanet);
        if (attachmentIndex < 0 || attachmentIndex >= attachments.size()) {
            clearTaMonumentAction(game, player);
            ButtonHelper.deleteMessage(event);
            return;
        }
        String attachment = attachments.get(attachmentIndex);
        List<Button> destinationButtons = getTaDestinationButtons(game, player, sourcePlanet.getName());
        if (destinationButtons.isEmpty()) {
            clearTaMonumentAction(game, player);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " has no eligible planet to receive "
                            + getAttachmentName(attachment) + " with _The Exchange_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(TA_STATE + player.getFaction(), sourcePlanet.getName() + "|" + attachmentIndex);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose the planet to receive "
                        + getAttachmentName(attachment) + " with _The Exchange_.",
                NewStuffHelper.buttonPagination(
                        destinationButtons, player.factionButtonChecker() + SELECT_TA_DESTINATION, 0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_TA_DESTINATION)
    public static void selectTaMonumentDestination(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] state = game.getStoredValue(TA_STATE + player.getFaction()).split("\\|", 2);
        String destinationPlanetName = buttonID.substring(SELECT_TA_DESTINATION.length());
        Planet sourcePlanet = state.length == 2 ? game.getPlanetsInfo().get(state[0]) : null;
        int attachmentIndex = state.length == 2 ? NumberUtils.toInt(state[1], -1) : -1;
        if (!isEligibleTaSource(game, player, sourcePlanet)
                || attachmentIndex < 0
                || attachmentIndex >= getTaMovableAttachments(sourcePlanet).size()) {
            clearTaMonumentAction(game, player);
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getTaDestinationButtons(game, player, sourcePlanet.getName());
        String message = player.getRepresentationNoPing()
                + ", please choose the planet to receive an attachment with _The Exchange_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + SELECT_TA_DESTINATION,
                buttonID)) {
            return;
        }
        Planet destinationPlanet = game.getPlanetsInfo().get(destinationPlanetName);
        if (!isEligibleTaDestination(game, player, destinationPlanet, sourcePlanet.getName())) {
            clearTaMonumentAction(game, player);
            ButtonHelper.deleteMessage(event);
            return;
        }
        String attachment = getTaMovableAttachments(sourcePlanet).get(attachmentIndex);
        String destinationAttachment = getTaDestinationAttachment(attachment, destinationPlanet);
        sourcePlanet.removeToken(attachment);
        destinationPlanet.addToken(destinationAttachment);
        clearTaMonumentAction(game, player);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " moved " + getAttachmentName(attachment) + " from "
                        + Helper.getPlanetRepresentation(sourcePlanet.getName(), game) + " to "
                        + Helper.getPlanetRepresentation(destinationPlanetName, game) + " with _The Exchange_.");
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    public static void clearMonumentActions(Game game) {
        for (Player player : game.getRealPlayers()) {
            clearTaMonumentAction(game, player);
            game.removeStoredValue(CRYSTELLUM_STATE + player.getFaction());
            game.removeStoredValue(DREAM_STATE + player.getFaction());
        }
    }

    public static void checkSpaceMonumentBlockades(Game game, GenericInteractionCreateEvent event) {
        checkSpaceMonumentBlockade(game, event, "dream_monument", "Plateau of Leng");
        checkSpaceMonumentBlockade(game, event, "deep_monument", "Ether Anchor");
    }

    private static List<Button> getSpacePlacementButtons(
            Game game, Player player, String monumentId, String buttonPrefix, Predicate<Tile> isEligible) {
        if (!game.isMonumentsMode()
                || !MonumentsService.hasMonument(game, player, monumentId)
                || MonumentsService.isMonumentOnBoard(game, player, monumentId)) {
            return List.of();
        }
        return game.getTileMap().values().stream()
                .filter(tile ->
                        tile.getTileModel() != null && !tile.getTileModel().isHyperlane())
                .filter(isEligible)
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.green(
                        player.factionButtonChecker() + buttonPrefix + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static List<Button> getValhavnMonumentPdsMoveButtons(Game game, Player player, Tile activatedTile) {
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "valhavn_monument");
        Planet monumentPlanet = getValhavnMonumentPlanet(monumentTile, player);
        if (monumentTile == null
                || monumentPlanet == null
                || activatedTile == null
                || !MonumentsService.isMonumentOnBoard(game, player, "valhavn_monument")
                || !Adjacency.of(game, player)
                        .forEffects()
                        .from(monumentTile.getPosition())
                        .contains(activatedTile.getPosition())) {
            return List.of();
        }
        return game.getTileMap().values().stream()
                .filter(tile -> Adjacency.of(game, player)
                        .forEffects()
                        .from(monumentTile.getPosition())
                        .contains(tile.getPosition()))
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .filter(planet -> planet.getUnitCount(UnitType.Pds, player) > 0)
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + MOVE_VALHAVN_MONUMENT_PDS + planet.getName() + "|"
                                + monumentPlanet.getName() + "|" + activatedTile.getPosition(),
                        "Move PDS From " + Helper.getPlanetRepresentation(planet.getName(), game)))
                .toList();
    }

    private static List<Button> getMigoMonumentExploreButtons(Game game, Player player, Tile activeTile) {
        if (activeTile == null
                || !MonumentsService.isMonumentReady(game, player, "migo_monument")
                || !MonumentsService.isInOrAdjacentToMonumentSystem(game, player, "migo_monument", activeTile)) {
            return new ArrayList<>();
        }
        return activeTile.getPlanetUnitHolders().stream()
                .filter(planet -> isEligibleMigoMonumentExplore(game, player, activeTile, planet))
                .filter(planet -> !ButtonHelper.getPlanetExplorationButtons(game, planet, player)
                        .isEmpty())
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + USE_MIGO_MONUMENT_EXPLORE + planet.getName() + "|"
                                + activeTile.getPosition(),
                        "Use The Cyst: " + Helper.getPlanetRepresentation(planet.getName(), game)))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static List<Button> getBloomMonumentPlayerButtons(Game game, Player monumentOwner) {
        if (!MonumentsService.isMonumentReady(game, monumentOwner, "bloom_monument")) {
            return List.of();
        }
        return game.getRealPlayersExcludingThis(monumentOwner).stream()
                .filter(target -> !getBloomMonumentPlanetButtons(game, monumentOwner, target)
                        .isEmpty())
                .map(target -> FoWHelper.fogSafeTargetButton(
                        monumentOwner.factionButtonChecker() + SELECT_BLOOM_MONUMENT_PLAYER + target.getFaction(),
                        "green",
                        target,
                        monumentOwner))
                .toList();
    }

    private static List<Button> getChoirMonumentPlayerButtons(Game game, Player monumentOwner) {
        if (!MonumentsService.isMonumentReady(game, monumentOwner, "choir_monument")) {
            return List.of();
        }
        return game.getRealPlayersExcludingThis(monumentOwner).stream()
                .filter(target -> !getChoirMonumentSystemButtons(game, monumentOwner, target)
                        .isEmpty())
                .map(target -> FoWHelper.fogSafeTargetButton(
                        monumentOwner.factionButtonChecker() + SELECT_CHOIR_MONUMENT_PLAYER + target.getFaction(),
                        "green",
                        target,
                        monumentOwner))
                .toList();
    }

    private static List<Button> getChoirMonumentSystemButtons(Game game, Player monumentOwner, Player target) {
        Tile monumentTile = MonumentsService.getMonumentTile(game, monumentOwner, "choir_monument");
        if (monumentTile == null || !MonumentsService.isMonumentReady(game, monumentOwner, "choir_monument")) {
            return List.of();
        }
        return game.getTileMap().values().stream()
                .filter(tile -> FoWHelper.knowsTile(game, target, tile.getPosition()))
                .filter(tile -> isEligibleChoirMonumentSystem(game, monumentOwner, target, tile))
                .sorted(Comparator.comparing(Tile::getPosition))
                .map(tile -> Buttons.green(
                        target.factionButtonChecker() + SELECT_CHOIR_MONUMENT_SYSTEM + monumentOwner.getFaction() + "|"
                                + tile.getPosition(),
                        tile.getRepresentationForButtons(game, target)))
                .toList();
    }

    private static List<Button> getChoirMonumentRepairButtons(
            Game game, Player monumentOwner, Player target, Tile selectedTile) {
        if (!isEligibleChoirMonumentSystem(game, monumentOwner, target, selectedTile)) {
            return List.of();
        }
        UnitHolder holder = selectedTile.getSpaceUnitHolder();
        return holder.getUnitKeys().stream()
                .filter(unitKey -> isEligibleChoirMonumentRepairShip(game, target, holder, unitKey))
                .map(unitKey -> Buttons.green(
                        target.factionButtonChecker() + REPAIR_CHOIR_MONUMENT_SHIP + monumentOwner.getFaction() + "|"
                                + selectedTile.getPosition() + "|" + unitKey.asyncID() + "|" + unitKey.colorID(),
                        "Repair " + game.getUnitFromUnitKey(unitKey).getName()))
                .toList();
    }

    private static boolean isEligibleChoirMonumentSystem(
            Game game, Player monumentOwner, Player target, Tile selectedTile) {
        Tile monumentTile = MonumentsService.getMonumentTile(game, monumentOwner, "choir_monument");
        return selectedTile != null
                && monumentTile != null
                && monumentOwner != target
                && FoWHelper.playerHasActualShipsInSystem(target, selectedTile)
                && Adjacency.of(game, monumentOwner)
                        .forEffects()
                        .from(monumentTile.getPosition())
                        .contains(selectedTile.getPosition());
    }

    private static boolean isEligibleChoirMonumentRepairShip(
            Game game, Player target, UnitHolder holder, UnitKey unitKey) {
        UnitModel unit = game.getUnitFromUnitKey(unitKey);
        return unit != null
                && unit.getIsShip()
                && target.unitBelongsToPlayer(unitKey)
                && holder.getDamagedUnitCount(unitKey) > 0;
    }

    private static void offerChoirMonumentOwnerShipChoice(Game game, Player monumentOwner) {
        List<Button> buttons = new ArrayList<>();
        if (!getChoirMonumentOwnerShipButtons(game, monumentOwner, true).isEmpty()) {
            buttons.add(Buttons.green(
                    monumentOwner.factionButtonChecker() + SELECT_CHOIR_MONUMENT_MODE + "repair", "Repair Ship"));
        }
        if (!getChoirMonumentOwnerShipButtons(game, monumentOwner, false).isEmpty()) {
            buttons.add(Buttons.red(
                    monumentOwner.factionButtonChecker() + SELECT_CHOIR_MONUMENT_MODE + "damage", "Damage Ship"));
        }
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.DECLINE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                monumentOwner.getCorrectChannel(),
                monumentOwner.getRepresentationNoPing()
                        + ", you may damage or repair 1 ship in _Silent Soliloquy_'s system.",
                buttons);
    }

    private static List<Button> getChoirMonumentOwnerShipButtons(Game game, Player player, boolean repair) {
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "choir_monument");
        if (monumentTile == null) {
            return List.of();
        }
        UnitHolder holder = monumentTile.getSpaceUnitHolder();
        return holder.getUnitKeys().stream()
                .filter(unitKey -> isEligibleChoirMonumentOwnerShip(
                        player, holder, unitKey, game.getUnitFromUnitKey(unitKey), repair))
                .map(unitKey -> Buttons.green(
                        player.factionButtonChecker() + RESOLVE_CHOIR_MONUMENT_SHIP + (repair ? "repair|" : "damage|")
                                + monumentTile.getPosition() + "|" + unitKey.asyncID() + "|" + unitKey.colorID(),
                        (repair ? "Repair " : "Damage ")
                                + game.getUnitFromUnitKey(unitKey).getName()))
                .toList();
    }

    private static boolean isEligibleChoirMonumentOwnerShip(
            Player player, UnitHolder holder, UnitKey unitKey, UnitModel unit, boolean repair) {
        return unit != null
                && unit.getIsShip()
                && player.unitBelongsToPlayer(unitKey)
                && (repair
                        ? holder.getDamagedUnitCount(unitKey) > 0
                        : unit.getSustainDamage()
                                && holder.getUnitCount(unitKey) > holder.getDamagedUnitCount(unitKey));
    }

    private static UnitKey findUnitKey(UnitHolder holder, String asyncId, String colorId) {
        return holder.getUnitKeys().stream()
                .filter(unitKey ->
                        unitKey.asyncID().equals(asyncId) && unitKey.colorID().equals(colorId))
                .findFirst()
                .orElse(null);
    }

    private static List<Button> getBloomMonumentPlanetButtons(Game game, Player monumentOwner, Player target) {
        return target.getExhaustedPlanets().stream()
                .map(game::getUnitHolderFromPlanet)
                .filter(Objects::nonNull)
                .filter(planet -> isEligibleBloomMonumentPlanet(game, monumentOwner, target, planet))
                .map(planet -> Buttons.green(
                        target.factionButtonChecker() + SELECT_BLOOM_MONUMENT_PLANET + monumentOwner.getFaction() + "|"
                                + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)))
                .toList();
    }

    private static boolean isEligibleBloomMonumentPlanet(
            Game game, Player monumentOwner, Player target, Planet planet) {
        Tile monumentTile = MonumentsService.getMonumentTile(game, monumentOwner, "bloom_monument");
        Tile planetTile = game.getTileFromPlanet(planet.getName());
        return monumentOwner != target
                && target.getPlanets().contains(planet.getName())
                && target.getExhaustedPlanets().contains(planet.getName())
                && MonumentsService.isMonumentReady(game, monumentOwner, "bloom_monument")
                && monumentTile != null
                && planetTile != null
                && Adjacency.of(game, monumentOwner)
                        .forEffects()
                        .withSelf()
                        .from(monumentTile.getPosition())
                        .contains(planetTile.getPosition());
    }

    private static void placeBloomMonumentInfantry(
            GenericInteractionCreateEvent event, Game game, Player monumentOwner, String planetName) {
        String coexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            AddUnitService.addUnits(
                    event,
                    game.getTileFromPlanet(planetName),
                    game,
                    monumentOwner.getColor(),
                    "1 infantry " + planetName);
        } finally {
            if (coexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", coexistenceFlag);
            }
        }
    }

    private static boolean isEligibleMigoMonumentExplore(Game game, Player player, Tile activeTile, Planet planet) {
        return activeTile != null
                && activeTile.getPosition().equals(game.getActiveSystem())
                && planet != null
                && player.getPlanets().contains(planet.getName())
                && planet.getUnitCount(UnitType.Infantry, player) > 0
                && MonumentsService.isMonumentReady(game, player, "migo_monument")
                && MonumentsService.isInOrAdjacentToMonumentSystem(game, player, "migo_monument", activeTile);
    }

    private static boolean isEligibleValhavnMonumentPdsMove(
            Game game,
            Player player,
            Tile monumentTile,
            Tile sourceTile,
            Planet sourcePlanet,
            Planet monumentPlanet,
            Tile activatedTile) {
        return monumentTile != null
                && sourceTile != null
                && sourcePlanet != null
                && monumentPlanet != null
                && activatedTile != null
                && MonumentsService.isMonumentOnBoard(game, player, "valhavn_monument")
                && monumentTile.getPlanetUnitHolders().contains(monumentPlanet)
                && monumentPlanet.getUnitCount(UnitType.Monument, player) > 0
                && sourcePlanet.getUnitCount(UnitType.Pds, player) > 0
                && Adjacency.of(game, player)
                        .forEffects()
                        .from(monumentTile.getPosition())
                        .contains(activatedTile.getPosition())
                && Adjacency.of(game, player)
                        .forEffects()
                        .from(monumentTile.getPosition())
                        .contains(sourceTile.getPosition());
    }

    private static Planet getValhavnMonumentPlanet(Tile monumentTile, Player player) {
        if (monumentTile == null) {
            return null;
        }
        return monumentTile.getPlanetUnitHolders().stream()
                .filter(planet -> planet.getUnitCount(UnitType.Monument, player) > 0)
                .findFirst()
                .orElse(null);
    }

    private static boolean isEligibleIronTideProduction(
            Game game, Player producingPlayer, Player monumentOwner, Tile productionTile) {
        Tile monumentTile = MonumentsService.getMonumentTile(game, monumentOwner, "iron_monument");
        return producingPlayer != monumentOwner
                && MonumentsService.isMonumentOnBoard(game, monumentOwner, "iron_monument")
                && monumentTile != null
                && Adjacency.of(game, producingPlayer)
                        .forEffects()
                        .from(productionTile.getPosition())
                        .contains(monumentTile.getPosition());
    }

    private static boolean canUseNatauMonumentAnomaly(
            Game game, Player activatingPlayer, Player monumentOwner, Tile activeTile) {
        Tile monumentTile = MonumentsService.getMonumentTile(game, monumentOwner, "natau_monument");
        return activatingPlayer != monumentOwner
                && activatingPlayer == game.getActivePlayer()
                && activeTile.getPosition().equals(game.getActiveSystem())
                && !activeTile.isHomeSystem(game)
                && activeTile.isAnomaly(game, activatingPlayer)
                && MonumentsService.isMonumentOnBoard(game, monumentOwner, "natau_monument")
                && monumentTile != null
                && Adjacency.of(game, monumentOwner)
                        .forEffects()
                        .withSelf()
                        .from(monumentTile.getPosition())
                        .contains(activeTile.getPosition());
    }

    private static String ironTideProductionKey(Tile tile) {
        return IRON_TIDE_PRODUCTION + tile.getPosition();
    }

    private static void placeSpaceMonument(
            ButtonInteractionEvent event,
            Game game,
            Player player,
            String buttonID,
            String monumentId,
            String buttonPrefix,
            Predicate<Tile> isEligible) {
        List<Button> buttons = getSpacePlacementButtons(game, player, monumentId, buttonPrefix, isEligible);
        String monumentName = "dream_monument".equals(monumentId) ? "Plateau of Leng" : "Ether Anchor";
        String message = player.getRepresentationNoPing()
                + ", please choose the system in which to place _"
                + monumentName
                + "_ in space.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + buttonPrefix,
                buttonID)) {
            return;
        }
        String position = buttonID.replace(buttonPrefix, "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null
                || buttons.stream().noneMatch(button -> button.getCustomId().endsWith(buttonPrefix + position))) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 monument");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed _" + monumentName + "_ in the space area of "
                        + tile.getRepresentationForButtons(game, player) + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getTaSourceButtons(Game game, Player player) {
        return player.getPlanets().stream()
                .map(game.getPlanetsInfo()::get)
                .filter(planet -> isEligibleTaSource(game, player, planet))
                .filter(planet ->
                        !getTaDestinationButtons(game, player, planet.getName()).isEmpty())
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + SELECT_TA_SOURCE + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)))
                .toList();
    }

    private static List<Button> getHearthHomePlanetButtons(Game game, Player player) {
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "ashen_monument");
        if (monumentTile == null) {
            return List.of();
        }
        return monumentTile.getPlanetUnitHolders().stream()
                .map(Planet::getName)
                .map(planetName -> Buttons.green(
                        player.factionButtonChecker() + PLACE_HEARTH_HOME_INFANTRY + planetName,
                        Helper.getPlanetRepresentation(planetName, game)))
                .toList();
    }

    private static List<Button> getCrystellumPlanetButtons(Game game, Player player) {
        return player.getExhaustedPlanets().stream()
                .filter(planetName -> MonumentsService.isInOrAdjacentToMonumentSystem(
                        game, player, "crystellum_monument", game.getTileFromPlanet(planetName)))
                .map(planetName -> Buttons.green(
                        player.factionButtonChecker() + READY_CRYSTELLUM_PLANET + planetName,
                        Helper.getPlanetRepresentation(planetName, game)))
                .toList();
    }

    private static List<Button> getPlateauOfLengShipButtons(Player player) {
        return player.getUnitModels().stream()
                .filter(UnitModel::getIsShip)
                .map(UnitModel::getBaseType)
                .distinct()
                .map(player::getUnitByBaseType)
                .filter(java.util.Objects::nonNull)
                .map(unit -> Buttons.green(
                        player.factionButtonChecker() + PLACE_PLATEAU_OF_LENG_SHIP + unit.getBaseType(),
                        "Place " + unit.getName()))
                .toList();
    }

    private static void checkSpaceMonumentBlockade(
            Game game, GenericInteractionCreateEvent event, String monumentId, String monumentName) {
        if (game == null || !game.isMonumentsMode()) {
            return;
        }
        Player neutral = game.getPlayerFromColorOrFaction("neutral");
        for (Player owner : game.getRealPlayers()) {
            Tile tile = MonumentsService.getMonumentTile(game, owner, monumentId);
            if (tile == null
                    || FoWHelper.playerHasActualShipsInSystem(owner, tile)
                    || game.getRealPlayers().stream()
                                    .noneMatch(other ->
                                            other != owner && FoWHelper.playerHasActualShipsInSystem(other, tile))
                            && (neutral == null || !FoWHelper.playerHasActualShipsInSystem(neutral, tile))) {
                continue;
            }
            DestroyUnitService.destroyUnits(event, tile, game, owner.getColor(), "1 monument", false);
            MessageHelper.sendMessageToChannel(
                    owner.getCorrectChannel(),
                    owner.getRepresentation() + " destroyed _" + monumentName + "_ because it was blockaded.");
        }
    }

    private static List<Button> getTaAttachmentButtons(Player player, Planet sourcePlanet) {
        List<String> attachments = getTaMovableAttachments(sourcePlanet);
        return java.util.stream.IntStream.range(0, attachments.size())
                .mapToObj(index -> Buttons.green(
                        player.factionButtonChecker() + SELECT_TA_ATTACHMENT + index,
                        "Move " + getAttachmentName(attachments.get(index))))
                .toList();
    }

    private static List<Button> getTaDestinationButtons(Game game, Player player, String sourcePlanetName) {
        return player.getPlanets().stream()
                .map(game.getPlanetsInfo()::get)
                .filter(planet -> isEligibleTaDestination(game, player, planet, sourcePlanetName))
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + SELECT_TA_DESTINATION + planet.getName(),
                        Helper.getPlanetRepresentation(planet.getName(), game)))
                .toList();
    }

    private static boolean isEligibleTaSource(Game game, Player player, Planet planet) {
        return planet != null && !getTaMovableAttachments(planet).isEmpty() && isEligibleTaPlanet(game, player, planet);
    }

    private static boolean isEligibleTaDestination(Game game, Player player, Planet planet, String sourcePlanetName) {
        return planet != null
                && !planet.getName().equals(sourcePlanetName)
                && getTaMovableAttachments(planet).isEmpty()
                && isEligibleTaPlanet(game, player, planet);
    }

    private static List<String> getTaMovableAttachments(Planet planet) {
        List<String> attachments = new ArrayList<>(planet.getAttachments());
        planet.getTokenList().stream()
                .filter(token -> {
                    AttachmentModel attachment = Mapper.getAttachmentInfo(token);
                    return attachment != null && attachment.getAlias().startsWith("design");
                })
                .forEach(attachments::add);
        return attachments;
    }

    private static String getTaDestinationAttachment(String attachment, Planet destinationPlanet) {
        AttachmentModel attachmentModel = Mapper.getAttachmentInfo(attachment);
        String attachmentId = attachmentModel == null ? "" : attachmentModel.getAlias();
        String facilityId = attachmentId.endsWith("stat")
                ? attachmentId.substring(0, attachmentId.length() - "stat".length())
                : attachmentId;
        if (!List.of("biotic", "cybernetic", "propulsion", "warfare", "weapon", "diversifiedresearchfacility")
                .contains(facilityId)) {
            return attachment;
        }
        String destinationFacility =
                destinationPlanet.getTechSpecialities().isEmpty() ? facilityId : facilityId + "stat";
        String destinationAttachment = Mapper.getAttachmentImagePath(destinationFacility);
        return destinationAttachment == null ? attachment : destinationAttachment;
    }

    private static boolean isEligibleTaPlanet(Game game, Player player, Planet planet) {
        return player.getPlanets().contains(planet.getName())
                && !planet.isHomePlanet(game)
                && MonumentsService.isInOrAdjacentToMonumentSystem(
                        game, player, "ta_monument", game.getTileFromPlanet(planet.getName()));
    }

    private static String getAttachmentName(String attachment) {
        AttachmentModel attachmentModel = Mapper.getAttachmentInfo(attachment);
        return attachmentModel == null ? attachment : attachmentModel.getName();
    }

    private static void clearTaMonumentAction(Game game, Player player) {
        game.removeStoredValue(TA_STATE + player.getFaction());
    }
}
