package ti4.discord.interactions.buttons.handlers.unit.monuments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;
import ti4.model.UnitModel;
import ti4.service.agenda.MonumentsAgendaService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.game.MonumentsService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.MoveUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class MonumentsBRButtonHandler {
    private static final String RESOLVE_DEEPMANTLE = "resolveDeepmantle";
    private static final String SELECT_ARMAGEDDON_PROJECT = "selectArmageddonProject_";
    private static final String SHOW_ARMAGEDDON_PROJECT_OPTIONS = "showArmageddonProjectOptions";
    private static final String USE_ARMAGEDDON_PROJECT = "useArmageddonProject_";
    private static final String ARMAGEDDON_PROJECT_GROM_TARGET = "armageddonProjectGromTarget_";
    private static final String ARMAGEDDON_PROJECT_MORS_TARGET = "armageddonProjectMorsTarget_";
    private static final String ARMAGEDDON_PROJECT_AVAILYN_TARGET = "armageddonProjectAvailynTarget_";
    private static final String ARMAGEDDON_PROJECT_SUPERWEAPONS = "armageddonProjectSuperweapons_";
    private static final String SACRED_POOLS_SELECT = "sacredPoolsSelect_";
    private static final String SACRED_POOLS_MOVE = "sacredPoolsMove_";
    private static final String SACRED_POOLS_DONE = "sacredPoolsDone_";
    private static final String SACRED_POOLS_SESSION = "sacredPoolsSession_";
    private static final String CHANGE_PATH = "changeSetPath";
    private static final String SET_OASIS_PATH = "setOasisPath_";
    private static final String PLACE_KALTRIM_MONUMENT = "placeKaltrimMonument_";

    // Hidden Oasis
    public static Button getOasisButton(Game game, Player player) {
        if (!MonumentsService.isMonumentReady(game, player, "uydai_monument")) {
            return null;
        }
        return Buttons.gray(player.factionButtonChecker() + CHANGE_PATH, "Use Hidden Oasis", FactionEmojis.uydai);
    }

    @ButtonHandler(CHANGE_PATH)
    public static void resolveChangePath(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.isMonumentsMode()
                || !MonumentsService.isMonumentOnBoard(game, player, "uydai_monument")
                || !MonumentsService.getMonumentTile(game, player, "uydai_monument")
                        .hasPlayerCC(player)
                || !MonumentsService.isMonumentReady(game, player, "uydai_monument")) {
            return;
        }

        MonumentsService.exhaustMonument(game, player, "uydai_monument");

        game.removeStoredValue("pathOf" + player.getFaction());

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + " is using _Hidden Oasis_ to change their selected path.",
                getOasisPathButtons(game, player));

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SET_OASIS_PATH)
    public static void setOasisPath(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String path = buttonID.substring(SET_OASIS_PATH.length());
        if (path.isBlank()) {
            return;
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " successfully set their **Path** to " + path + ".");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please choose whether to accept or refuse the path.",
                List.of(
                        Buttons.green(player.factionButtonChecker() + "acceptPath", "Accept Path"),
                        Buttons.red(player.factionButtonChecker() + "declinePath", "Refuse Path")));
    }

    private static List<Button> getOasisPathButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(
                Buttons.blue(player.factionButtonChecker() + SET_OASIS_PATH + "Tactical Action", "Tactical Action"));
        buttons.add(
                Buttons.green(player.factionButtonChecker() + SET_OASIS_PATH + "Component Action", "Component Action"));
        boolean hasUnplayedStrategyCard =
                player.getSCs().stream().anyMatch(sc -> !game.getPlayedSCs().contains(sc));
        buttons.add(
                hasUnplayedStrategyCard
                        ? Buttons.gray(
                                player.factionButtonChecker() + SET_OASIS_PATH + "Strategic Action", "Strategic Action")
                        : Buttons.red(player.factionButtonChecker() + SET_OASIS_PATH + "Pass Action", "Pass"));
        return buttons;
    }

    // Toldar Helpers
    public static void checkMonumentHonorDishonorFlip(Game game, Player player) {
        if (!game.isMonumentsMode() || !player.hasAnyUnit("toldar_monumenthonor", "toldar_monumentdishonor")) {
            return;
        }

        UnitModel monumentHonor = Mapper.getUnit("toldar_monumenthonor");
        UnitModel monumentDishonor = Mapper.getUnit("toldar_monumentdishonor");

        if (player.getHonorCounter() > player.getDishonorCounter() && !player.hasUnit("toldar_monumenthonor")) {
            player.removeOwnedUnitByID("toldar_monumentdishonor");
            player.addOwnedUnitByID("toldar_monumenthonor");

            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " has more Honor than Dishonor, and as such flipped their monument to it's other side.",
                    monumentHonor.getRepresentationEmbed());
        } else if (player.getDishonorCounter() > player.getHonorCounter()
                && !player.hasUnit("toldar_monumentdishonor")) {
            player.removeOwnedUnitByID("toldar_monumenthonor");
            player.addOwnedUnitByID("toldar_monumentdishonor");

            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " has more Dishonor than Honor, and as such flipped their monument to it's other side.",
                    monumentDishonor.getRepresentationEmbed());
        }
    }

    // Charnel Fane
    public static void checkDishonorMonumentCondition(Game game, Player player) {
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "toldar_monumentdishonor")) {
            return;
        }

        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "toldar_monumentdishonor");
        Planet monumentPlanet = MonumentsService.getPlayerMonumentPlanet(game, player);
        if (monumentTile == null || monumentPlanet == null) {
            return;
        }

        if (player.getDishonorCounter() == 8) {
            MoveUnitService.moveUnits(
                    null,
                    monumentTile,
                    game,
                    player.getColor(),
                    "1 monument " + monumentPlanet.getName(),
                    monumentTile,
                    Constants.SPACE);
        }
    }

    public static boolean preventsCharnelFaneSpaceCombat(Game game, Tile tile) {
        Player owner = MonumentsService.getMonumentOwner(game, "toldar_monumentdishonor");
        return owner != null
                && game.isMonumentsMode()
                && MonumentsService.isMonumentOnBoard(game, owner, "toldar_monumentdishonor")
                && tile == MonumentsService.getMonumentTile(game, owner, "toldar_monumentdishonor")
                && tile.getSpaceUnitHolder().getUnitCount(UnitType.Monument, owner) > 0
                && tile.getSpaceUnitHolder().getUnitKeysForPlayer(owner).stream()
                        .allMatch(unitKey -> unitKey.unitType() == UnitType.Monument);
    }

    // Black Pyramid
    public static Button offerBlackPyramidDeploy(Game game, Player player, Planet planet) {
        return Buttons.green(
                player.factionButtonChecker() + "place_monument_" + planet.getName(),
                "DEPLOY to " + planet.getRepresentation(game),
                FactionEmojis.pharadn);
    }

    // Raider Stronghold
    public static boolean canPlaceSarcosaMonument(Game game, Player player, Tile tile, Planet planet) {
        Player neutral = game.getPlayerFromColorOrFaction("neutral");
        return game.isMonumentsMode()
                && player.hasUnit("sarcosa_monument")
                && !MonumentsService.isMonumentOnBoard(game, player, "sarcosa_monument")
                && tile != null
                && planet != null
                && !tile.isHomeSystem(game)
                && !tile.isFracture()
                && game.getTileMap().values().stream()
                        .filter(system -> FoWHelper.playerHasUnitsInSystem(player, system)
                                || (neutral != null && FoWHelper.playerHasUnitsInSystem(neutral, system)))
                        .flatMap(system ->
                                FoWHelper.getAdjacentTilesAndNotThisTile(game, system.getPosition(), player, false)
                                        .stream())
                        .anyMatch(tile.getPosition()::equals);
    }

    public static List<Button> getSarcosaMonumentPlacementButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        UnitModel monument = Mapper.getUnit("sarcosa_monument");
        if (monument == null) {
            return buttons;
        }
        Set<String> planets = new LinkedHashSet<>(player.getPlanetsAllianceMode());
        for (Tile tile : game.getTileMap().values()) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (canPlaceSarcosaMonument(game, player, tile, planet)) {
                    planets.add(planet.getName());
                }
            }
        }
        for (String planetName : planets) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet == null) {
                continue;
            }
            if (!monument.canBePlacedOnPlanetTypes(planet.getPlanetTypes())
                    || planet.isSpaceStation(game)
                    || planet.getTokenList().stream().anyMatch(token -> token.contains("dmz"))) {
                continue;
            }
            Tile tile = game.getTileFromPlanet(planetName);
            if (tile == null) {
                continue;
            }
            if (!player.getPlanetsAllianceMode().contains(planetName)
                    && !canPlaceSarcosaMonument(game, player, tile, planet)) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "place_monument_" + planet.getName(),
                    Helper.getPlanetRepresentation(planet.getName(), game)));
        }
        return buttons;
    }

    // Sacred Pools
    public static void addSacredPoolsGroundCombatButton(
            List<Button> buttons, Game game, Player player, Tile tile, String unitHolderName) {
        if (!MonumentsService.isMonumentReady(game, player, "qhet_monument")
                || MonumentsService.getPlayerMonumentPlanet(game, player) == null) {
            return;
        }
        UnitHolder source = tile.getUnitHolders().get(unitHolderName);
        if (source == null || source.getUnitCount(UnitType.Infantry, player) < 1) {
            return;
        }
        Planet destination = MonumentsService.getPlayerMonumentPlanet(game, player);
        buttons.add(Buttons.green(
                player.factionButtonChecker() + SACRED_POOLS_SELECT + tile.getPosition() + "|" + unitHolderName,
                "Move Infantry to " + destination.getRepresentation(game),
                FactionEmojis.qhet));
    }

    @ButtonHandler(SACRED_POOLS_SELECT)
    public static void selectSacredPoolsInfantry(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SACRED_POOLS_SELECT.length()).split("\\|", 2);
        if (payload.length != 2 || !MonumentsService.isMonumentReady(game, player, "qhet_monument")) {
            return;
        }
        Tile sourceTile = game.getTileByPosition(payload[0]);
        UnitHolder source =
                sourceTile == null ? null : sourceTile.getUnitHolders().get(payload[1]);
        if (source == null || source.getUnitCount(UnitType.Infantry, player) < 1) {
            return;
        }
        if (!MonumentsService.exhaustMonument(game, player, "qhet_monument")) {
            return;
        }
        game.setStoredValue(SACRED_POOLS_SESSION + player.getFaction(), String.join("|", payload));
        List<Button> buttons = new ArrayList<>();
        for (int amount = 1; amount <= Math.min(2, source.getUnitCount(UnitType.Infantry, player)); amount++) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SACRED_POOLS_MOVE + payload[0] + "|" + payload[1] + "|" + amount,
                    "Move " + amount + " Infantry",
                    FactionEmojis.qhet));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + SACRED_POOLS_DONE + String.join("|", payload), "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose infantry to move to _Sacred Pools_.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SACRED_POOLS_MOVE)
    public static void resolveSacredPools(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SACRED_POOLS_MOVE.length()).split("\\|", 3);
        if (payload.length != 3
                || !String.join("|", payload[0], payload[1])
                        .equals(game.getStoredValue(SACRED_POOLS_SESSION + player.getFaction()))) {
            return;
        }
        Tile sourceTile = game.getTileByPosition(payload[0]);
        UnitHolder source =
                sourceTile == null ? null : sourceTile.getUnitHolders().get(payload[1]);
        Planet destination = MonumentsService.getPlayerMonumentPlanet(game, player);
        Tile destinationTile = destination == null ? null : game.getTileFromPlanet(destination.getName());
        int amount;
        try {
            amount = Integer.parseInt(payload[2]);
        } catch (NumberFormatException e) {
            return;
        }
        UnitKey infantry = Units.getUnitKey(UnitType.Infantry, player.getColor());
        if (source == null
                || destination == null
                || destinationTile == null
                || amount < 1
                || source.getUnitCount(infantry) < amount) {
            return;
        }
        List<RemovedUnit> removed = RemoveUnitService.removeUnit(
                event, sourceTile, game, new ParsedUnit(infantry, amount, source.getName()), UnitState.none);
        AddUnitService.addUnits(
                event,
                game,
                removed.stream()
                        .map(unit -> new RemovedUnit(unit.unitKey(), destinationTile, destination, unit.states()))
                        .toList());
        List<Button> buttons = new ArrayList<>();
        for (int moveAmount = 1;
                moveAmount <= Math.min(2, source.getUnitCount(UnitType.Infantry, player));
                moveAmount++) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SACRED_POOLS_MOVE + payload[0] + "|" + payload[1] + "|"
                            + moveAmount,
                    "Move " + moveAmount + " Infantry",
                    FactionEmojis.qhet));
        }
        buttons.add(Buttons.red(
                player.factionButtonChecker() + SACRED_POOLS_DONE + String.join("|", payload[0], payload[1]), "Done"));
        MessageHelper.editMessageButtons(event, buttons);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed " + amount + " infantry on "
                        + destination.getRepresentation(game) + " with _Sacred Pools_ instead of destroying them.");
    }

    @ButtonHandler(SACRED_POOLS_DONE)
    public static void finishSacredPools(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String session = buttonID.substring(SACRED_POOLS_DONE.length());
        if (!session.equals(game.getStoredValue(SACRED_POOLS_SESSION + player.getFaction()))) {
            return;
        }
        game.removeStoredValue(SACRED_POOLS_SESSION + player.getFaction());
        event.getHook()
                .editOriginal(event.getMessage().getContentRaw())
                .setComponents()
                .queue();
    }

    // The Crown Temple
    public static void offerKaltrimMonumentDeploy(Game game, Player player) {
        if (!game.isMonumentsMode()
                || !player.ownsUnit("kaltrim_monument")
                || MonumentsService.isMonumentOnBoard(game, player, "kaltrim_monument")) {
            return;
        }

        Tile homeSystem = player.getHomeSystemTile();
        if (homeSystem == null) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Planet planet : homeSystem.getPlanetUnitHolders()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE_KALTRIM_MONUMENT + planet.getName(),
                    "Place Crown Temple on " + planet.getRepresentation(game),
                    FactionEmojis.kaltrim));
        }

        if (!buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", you may place _The Crown Temple_ on a planet in your home system.",
                    buttons);
        }
    }

    @ButtonHandler(PLACE_KALTRIM_MONUMENT)
    public static void placeKaltrimMonument(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring(PLACE_KALTRIM_MONUMENT.length());
        Tile homeSystem = player.getHomeSystemTile();
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (!game.isMonumentsMode()
                || !player.ownsUnit("kaltrim_monument")
                || MonumentsService.isMonumentOnBoard(game, player, "kaltrim_monument")
                || homeSystem == null
                || planet == null
                || game.getTileFromPlanet(planetName) != homeSystem) {
            return;
        }
        AddUnitService.addUnits(event, homeSystem, game, player.getColor(), "monument " + planetName);
        MonumentsAgendaService.resolveCathedralOfIxthPlacement(game, player, planetName);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed _The Crown Temple_ on " + planet.getRepresentation(game)
                        + ".");
        ButtonHelper.deleteMessage(event);
    }

    // Deepmantle Interface
    public static void offerDeepmantle(Game game, Player player) {
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "atokera_monument")) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + RESOLVE_DEEPMANTLE, "Produce Up to 1 Unit", FactionEmojis.atokera));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you correctly voted for or predicted the outcome of this agenda and thus may produce up to 1 unit in the system containing _Deepmantle Interface_.",
                buttons);
    }

    @ButtonHandler(RESOLVE_DEEPMANTLE)
    public static void resolveDeepmantle(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.isMonumentsMode() || !MonumentsService.isMonumentOnBoard(game, player, "atokera_monument")) {
            return;
        }

        Tile tile = MonumentsService.getMonumentTile(game, player, "atokera_monument");
        if (tile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Unable to locate Monument tile.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "warfare", "place");

        int productionValue = Helper.getProductionValue(player, game, tile, false);

        String message = player.getRepresentation() + ", use these buttons to produce.\n"
                + ButtonHelper.getListOfStuffAvailableToSpend(player, game)
                + "\nYou have " + productionValue + " PRODUCTION value in this system.";

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "__Produce Units__", buttons);

        ButtonHelper.deleteMessage(event);
    }

    // Armageddon Project
    public static void offerArmageddonProject(Game game, Player player) {
        if (game == null
                || !game.isMonumentsMode()
                || !MonumentsService.hasMonument(game, player, "belkosea_monument")) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        Set<String> superweapons = getArmageddonProjectSuperweapons(game, player);
        for (String relic : player.getRelics()) {
            if (!relic.startsWith("superweapon") || superweapons.contains(relic)) {
                continue;
            }
            RelicModel relicModel = Mapper.getRelic(relic);
            if (relicModel != null) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + SELECT_ARMAGEDDON_PROJECT + relic,
                        "Place Token on " + relicModel.getName(),
                        FactionEmojis.belkosea));
            }
        }
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing()
                        + ", choose a Superweapon card on which to place a control token for _Armageddon Project_.",
                buttons);
    }

    @ButtonHandler(SELECT_ARMAGEDDON_PROJECT)
    public static void selectArmageddonProject(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String relic = buttonID.substring(SELECT_ARMAGEDDON_PROJECT.length());
        if (game == null
                || !game.isMonumentsMode()
                || !MonumentsService.isMonumentOnBoard(game, player, "belkosea_monument")
                || !player.hasRelic(relic)
                || !relic.startsWith("superweapon")) {
            return;
        }
        Set<String> superweapons = getArmageddonProjectSuperweapons(game, player);
        if (superweapons.contains(relic)) {
            return;
        }
        superweapons.add(relic);
        game.setStoredValue(ARMAGEDDON_PROJECT_SUPERWEAPONS + player.getFaction(), String.join(",", superweapons));
        RelicModel relicModel = Mapper.getRelic(relic);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed a control token on _"
                        + (relicModel == null ? relic : relicModel.getName()) + "_ for _Armageddon Project_.");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean hasArmageddonProjectSuperweapon(Game game, Player player, String relic) {
        return game != null
                && game.isMonumentsMode()
                && MonumentsService.isMonumentOnBoard(game, player, "belkosea_monument")
                && player.hasRelic(relic)
                && getArmageddonProjectSuperweapons(game, player).contains(relic);
    }

    public static boolean makesTileNebula(Game game, Tile tile) {
        return game != null
                && game.isMonumentsMode()
                && tile != null
                && game.getRealPlayers().stream()
                        .anyMatch(player -> hasArmageddonProjectSuperweapon(game, player, "superweaponavailyn")
                                && tile == MonumentsService.getMonumentTile(game, player, "belkosea_monument"));
    }

    public static boolean ignoresFighterCapacity(Game game, Player player, Tile tile) {
        return hasArmageddonProjectSuperweapon(game, player, "superweaponglatison")
                && tile == MonumentsService.getMonumentTile(game, player, "belkosea_monument");
    }

    public static void addArmageddonProjectCardsInfoButtons(List<Button> buttons, Game game, Player player) {
        if (game == null
                || !game.isMonumentsMode()
                || !MonumentsService.isMonumentReady(game, player, "belkosea_monument")) {
            return;
        }
        if (getArmageddonProjectSuperweapons(game, player).stream().anyMatch(player::hasRelic)) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + SHOW_ARMAGEDDON_PROJECT_OPTIONS,
                    "Use Armageddon Project",
                    FactionEmojis.belkosea));
        }
    }

    @ButtonHandler(SHOW_ARMAGEDDON_PROJECT_OPTIONS)
    public static void showArmageddonProjectOptions(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null
                || !game.isMonumentsMode()
                || !MonumentsService.isMonumentReady(game, player, "belkosea_monument")) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String superweapon : List.of("availyn", "grom", "mors", "glatison")) {
            String relic = "superweapon" + superweapon;
            if (!hasArmageddonProjectSuperweapon(game, player, relic)) {
                continue;
            }
            RelicModel relicModel = Mapper.getRelic(relic);
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + USE_ARMAGEDDON_PROJECT + superweapon,
                    "Use " + (relicModel == null ? superweapon : relicModel.getName()),
                    FactionEmojis.belkosea));
        }
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose a copied Superweapon text ability to resolve.",
                buttons);
    }

    public static Button getArmageddonProjectCaledButton(Game game, Player player, Tile tile) {
        if (game == null
                || !game.isMonumentsMode()
                || !MonumentsService.isMonumentReady(game, player, "belkosea_monument")
                || !hasArmageddonProjectSuperweapon(game, player, "superweaponcaled")) {
            return null;
        }
        return Buttons.gray(
                player.factionButtonChecker() + USE_ARMAGEDDON_PROJECT + "caled|" + tile.getPosition(),
                "Use Armageddon Project: Caled",
                FactionEmojis.belkosea);
    }

    @ButtonHandler(USE_ARMAGEDDON_PROJECT)
    public static void useArmageddonProject(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(USE_ARMAGEDDON_PROJECT.length()).split("\\|", 2);
        String superweapon = payload[0];
        String relic = "superweapon" + superweapon;
        if (game == null || !game.isMonumentsMode() || !hasArmageddonProjectSuperweapon(game, player, relic)) {
            return;
        }
        Tile monumentTile = MonumentsService.getMonumentTile(game, player, "belkosea_monument");
        if (monumentTile == null) {
            return;
        }
        if ("caled".equals(superweapon) && payload.length == 2) {
            if (!MonumentsService.exhaustMonument(game, player, "belkosea_monument")) {
                return;
            }
            ButtonHelperModifyUnits.resolveAssaultCannonNDihmohnCommander(
                    "id_caled_" + payload[1], event, player, game);
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        if ("availyn".equals(superweapon)) {
            List<Button> buttons = game.getTileMap().values().stream()
                    .filter(tile -> FoWHelper.playerHasActualShipsInSystem(player, tile))
                    .map(tile -> Buttons.green(
                            player.factionButtonChecker() + ARMAGEDDON_PROJECT_AVAILYN_TARGET + tile.getPosition(),
                            tile.getRepresentationForButtons(game, player)))
                    .toList();
            if (buttons.isEmpty() || !MonumentsService.exhaustMonument(game, player, "belkosea_monument")) {
                return;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + ", choose a system in which to produce up to 3 fighters with _Armageddon Project_.",
                    buttons);
        } else if ("grom".equals(superweapon)) {
            List<Button> buttons = new ArrayList<>();
            for (String position : FoWHelper.getAdjacentTiles(game, monumentTile.getPosition(), player, true)) {
                Tile tile = game.getTileByPosition(position);
                if (tile != null
                        && (tile.getTileModel() == null || !tile.getTileModel().isHyperlane())) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + ARMAGEDDON_PROJECT_GROM_TARGET + position,
                            tile.getRepresentationForButtons(game, player)));
                }
            }
            if (buttons.isEmpty() || !MonumentsService.exhaustMonument(game, player, "belkosea_monument")) {
                return;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + ", choose a system in which to resolve Grom's copied text ability.",
                    buttons);
        } else if ("mors".equals(superweapon)) {
            List<Button> buttons = new ArrayList<>();
            Set<String> adjacent =
                    FoWHelper.getAdjacentTilesAndNotThisTile(game, monumentTile.getPosition(), player, true);
            for (Tile tile : game.getTileMap().values()) {
                if (!tile.getPosition().equals(monumentTile.getPosition())
                        && !adjacent.contains(tile.getPosition())
                        && (tile.getTileModel() == null || !tile.getTileModel().isHyperlane())) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + ARMAGEDDON_PROJECT_MORS_TARGET + tile.getPosition(),
                            tile.getRepresentationForButtons(game, player)));
                }
            }
            if (buttons.isEmpty() || !MonumentsService.exhaustMonument(game, player, "belkosea_monument")) {
                return;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + ", choose a non-adjacent system in which to resolve Mors's copied text ability.",
                    buttons);
        } else if ("glatison".equals(superweapon)) {
            if (!MonumentsService.exhaustMonument(game, player, "belkosea_monument")) {
                return;
            }
            for (Tile tile : game.getTileMap().values()) {
                tile.removeAllUnitDamage(player.getColor());
            }
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " repaired all damaged units with Glatison's copied text ability.");
        } else {
            return;
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(ARMAGEDDON_PROJECT_AVAILYN_TARGET)
    public static void resolveArmageddonProjectAvailyn(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.substring(ARMAGEDDON_PROJECT_AVAILYN_TARGET.length());
        if (!hasArmageddonProjectSuperweapon(game, player, "superweaponavailyn")
                || game.getTileByPosition(position) == null) {
            return;
        }
        ButtonHelperAbilities.availynStep2(game, player, event, "availynStep2_" + position);
    }

    @ButtonHandler(ARMAGEDDON_PROJECT_GROM_TARGET)
    public static void resolveArmageddonProjectGrom(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.substring(ARMAGEDDON_PROJECT_GROM_TARGET.length());
        if (!hasArmageddonProjectSuperweapon(game, player, "superweapongrom")
                || game.getTileByPosition(position) == null) {
            return;
        }
        ButtonHelperAbilities.gromPart2(player, game, "gromPart2_" + position, event);
    }

    @ButtonHandler(ARMAGEDDON_PROJECT_MORS_TARGET)
    public static void resolveArmageddonProjectMors(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.substring(ARMAGEDDON_PROJECT_MORS_TARGET.length());
        if (!hasArmageddonProjectSuperweapon(game, player, "superweaponmors")
                || game.getTileByPosition(position) == null) {
            return;
        }
        ButtonHelperAbilities.morsPart2(player, game, "morsPart2_" + position, event);
    }

    public static Set<String> getArmageddonProjectSuperweapons(Game game, Player player) {
        String stored = game.getStoredValue(ARMAGEDDON_PROJECT_SUPERWEAPONS + player.getFaction());
        if (stored.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(Arrays.asList(stored.split(",")));
    }

    public static void setArmageddonProjectSuperweapons(Game game, Player player, Set<String> superweapons) {
        game.setStoredValue(ARMAGEDDON_PROJECT_SUPERWEAPONS + player.getFaction(), String.join(",", superweapons));
    }
}
