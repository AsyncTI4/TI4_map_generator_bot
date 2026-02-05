package ti4.helpers.thundersedge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RandomHelper;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.regex.RegexService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

public class TeHelperUnits {

    /* ---------------------------------------------------------------------------|--------------------------------------------------------------------------- */
    /* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -- Firmament & Obsidian - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */
    /* ---------------------------------------------------------------------------|--------------------------------------------------------------------------- */

    // mech
    // mech
    // fs
    // fs

    /* ---------------------------------------------------------------------------|--------------------------------------------------------------------------- */
    /* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -- Last Bastion - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */
    /* ---------------------------------------------------------------------------|--------------------------------------------------------------------------- */

    // mech
    // fs

    /* ---------------------------------------------------------------------------|--------------------------------------------------------------------------- */
    /* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -- Crimson Rebellion -- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */
    /* ---------------------------------------------------------------------------|--------------------------------------------------------------------------- */
    @ButtonHandler("revenantDeploy_")
    private static void revenantDeploy(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String regex = "revenantDeploy_" + RegexHelper.unitHolderRegex(game, "planet");
        if (!game.getTileByPosition(game.getActiveSystem())
                .getSpaceUnitHolder()
                .getTokenList()
                .contains(Constants.TOKEN_BREACH_ACTIVE)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "The system must have an active Breach in it to deploy a Revenant.");
            return;
        }
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String planet = matcher.group("planet");
            AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "1 mech " + planet);
            String planetRep = Helper.getPlanetRepresentation(planet, game);
            String boringMsg = player.getRepresentation(true, false) + " deployed a Revenant on " + planetRep + ".";
            String flavorMsg =
                    "Out of the cold depths of the active Breach, a Rebellion Revenant has emerged, landing on "
                            + planetRep + ".";

            String msg = RandomHelper.isOneInX(20) ? flavorMsg : boringMsg;
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            ButtonHelper.deleteButtonsWithPartialID(event, "revenantDeploy_");
        });
    }

    public static boolean affectedByQuietus(Game game, Player player, Tile tile) {
        return affectedByQuietus(game, player, tile.getSpaceUnitHolder());
    }

    public static boolean affectedByQuietus(Game game, Player player, UnitHolder uh) {
        // Get the actual space unit holder if able
        if (!"space".equals(uh.getName())) {
            Tile t = game.getTileFromPlanet(uh.getName());
            if (t == null) return false;
            uh = t.getSpaceUnitHolder();
        }

        // The crimson rebellion player exists, and the tile has a breach
        Player crimson = Helper.getPlayerFromUnit(game, "crimson_flagship");
        if (uh == null || crimson == null || !uh.getTokenList().contains(Constants.TOKEN_BREACH_ACTIVE)) {
            return false;
        }

        // The crimson (fs) player has their flagship on a breach as well, and the affected player is not crimson
        for (Tile fs : ButtonHelper.getTilesOfPlayersSpecificUnits(game, crimson, UnitType.Flagship)) {
            if (fs.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_BREACH_ACTIVE)
                    && player != crimson
                    && !crimson.getAllianceMembers().contains(player.getFaction())) {
                return true;
            }
        }

        // The nekro (bt) player has the crimson flagship, and has their flagship on a breach, and the player is not
        // nekro
        Player nekro = Helper.getPlayerFromUnlockedBreakthrough(game, "nekrobt");
        if (nekro != null && player.hasUnit("crimson_flagship")) {
            for (Tile fs : ButtonHelper.getTilesOfPlayersSpecificUnits(game, nekro, UnitType.Flagship)) {
                if (fs.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_BREACH_ACTIVE)
                        && player != nekro
                        && !nekro.getAllianceMembers().contains(player.getFaction())) {
                    return true;
                }
            }
        }

        return false;
    }

    /* ---------------------------------------------------------------------------|--------------------------------------------------------------------------- */
    /* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -- RalNel Consortium -- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */
    /* ---------------------------------------------------------------------------|--------------------------------------------------------------------------- */
    @ButtonHandler("startForerunner_")
    private static void chooseSystemForForerunner(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        HashMap<String, List<String>> forerunnerMap =
                TeHelperAbilities.readMoveMap(game.getStoredValue("forerunnerMovementMap"));

        // Part 1 (no error)
        String regex1 = "startForerunner_" + RegexHelper.posRegex();
        Pattern part1 = Pattern.compile(regex1);
        boolean succ = RegexService.runMatcher(
                part1,
                buttonID,
                matcher -> {
                    Tile tile = game.getTileByPosition(matcher.group("pos"));
                    List<Button> buttons = getForerunnerSystemButtons(game, player, tile, forerunnerMap);
                    String message = player.getRepresentation()
                            + ", please choose the system to move ground forces from using your Alarum, or click \"Done Moving\".";
                    message += TeHelperAbilities.unitSummary(game, player, forerunnerMap);
                    MessageHelper.editMessageWithButtons(event, message, buttons);
                },
                x -> {});
        if (succ) return;

        // Part 2
        String regex2 = regex1 + "_" + RegexHelper.posRegex("source");
        Pattern part2 = Pattern.compile(regex2);
        RegexService.runMatcher(part2, buttonID, matcher -> {
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            Tile source = game.getTileByPosition(matcher.group("source"));
            List<Button> buttons = getForerunnerUnitButtonsForSystem(
                    game, player, tile, source, forerunnerMap.get(matcher.group("source")));
            String message = player.getRepresentation()
                    + ", please choose the ground forces to move with your Alarum, or click \"Done Moving\" to move from a different system.";
            message += TeHelperAbilities.unitSummary(game, player, forerunnerMap);
            MessageHelper.editMessageWithButtons(event, message, buttons);
        });
    }

    @ButtonHandler("moveForerunner_")
    @ButtonHandler("undoForerunner_")
    private static void moveUnitWithForerunner(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        HashMap<String, List<String>> forerunnerMap =
                TeHelperAbilities.readMoveMap(game.getStoredValue("forerunnerMovementMap"));
        String regex = "(undo|move)Forerunner_" + RegexHelper.posRegex(game, "destination") + "_"
                + RegexHelper.posRegex(game, "source") + "_" + RegexHelper.unitTypeRegex() + "_"
                + RegexHelper.unitHolderRegex(game, "planet");

        RegexService.runMatcher(regex, buttonID, matcher -> {
            Tile destination = game.getTileByPosition(matcher.group("destination"));
            String pos = matcher.group("source");
            Tile source = game.getTileByPosition(pos);
            String unitStr = matcher.group("unittype") + " " + matcher.group("planet");

            if (buttonID.startsWith("move")) {
                if (!forerunnerMap.containsKey(pos)) forerunnerMap.put(pos, new ArrayList<>());
                forerunnerMap.get(pos).add(unitStr);
            } else {
                if (forerunnerMap.containsKey(pos)) forerunnerMap.get(pos).remove(unitStr);
                if (forerunnerMap.containsKey(pos) && forerunnerMap.get(pos).isEmpty()) forerunnerMap.remove(pos);
            }
            game.setStoredValue("forerunnerMovementMap", TeHelperAbilities.storeMovementMap(forerunnerMap));

            List<Button> buttons =
                    getForerunnerUnitButtonsForSystem(game, player, destination, source, forerunnerMap.get(pos));
            String message = player.getRepresentation()
                    + ", please choose the ground forces to move with your Alarum, or click \"Done Moving\" to move from a different system.";
            message += TeHelperAbilities.unitSummary(game, player, forerunnerMap);
            MessageHelper.editMessageWithButtons(event, message, buttons);
        });
    }

    @ButtonHandler("finishForerunner_")
    private static void finishedWithForerunner(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex =
                "finishForerunner_" + RegexHelper.posRegex() + "_" + RegexHelper.unitHolderRegex(game, "combatPlanet");

        RegexService.runMatcher(regex, buttonID, matcher -> {
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            HashMap<String, List<String>> forerunnerMap =
                    TeHelperAbilities.readMoveMap(game.getStoredValue("forerunnerMovementMap"));
            for (Map.Entry<String, List<String>> entry : forerunnerMap.entrySet()) {
                List<String> units =
                        entry.getValue().stream().collect(Collectors.groupingBy(s -> s)).entrySet().stream()
                                .map(e -> e.getValue().size() + " " + e.getKey())
                                .toList();
                List<String> unitsTo = entry.getValue().stream()
                        .map(unit -> unit.substring(0, unit.indexOf(' ')) + " " + matcher.group("combatPlanet"))
                        .collect(Collectors.groupingBy(s -> s))
                        .entrySet()
                        .stream()
                        .map(e -> e.getValue().size() + " " + e.getKey())
                        .toList();
                String unitStrFrom = String.join(", ", units);
                String unitStrTo = String.join(", ", unitsTo);

                RemoveUnitService.removeUnits(
                        event, game.getTileByPosition(entry.getKey()), game, player.getColor(), unitStrFrom);
                AddUnitService.addUnits(event, tile, game, player.getColor(), unitStrTo);
            }
            game.removeStoredValue("forerunnerMovementMap");
            String msg = player.getRepresentation()
                    + " moved the following units into the active system with an Alarum:"
                    + TeHelperAbilities.unitSummary(game, player, forerunnerMap);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            ButtonHelper.deleteMessage(event);
        });
    }

    public static List<Button> getForerunnerSystemButtons(
            Game game, Player player, Tile tile, HashMap<String, List<String>> forerunnerMap) {
        // Get the tiles that are valid sources for Forerunner mech
        if (forerunnerMap == null) forerunnerMap = new HashMap<>();
        List<Button> buttons = new ArrayList<>();
        for (String adj : FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false)) {
            Tile t = game.getTileByPosition(adj);
            if (t != null
                    && getForerunnerUnitButtonsForSystem(game, player, tile, t, forerunnerMap.get(adj))
                                    .size()
                            > 1) {
                String id = player.finChecker() + "startForerunner_" + tile.getPosition() + "_" + t.getPosition();
                String label = t.getRepresentationForButtons(game, player);
                buttons.add(Buttons.green(id, label));
            }
        }
        buttons.add(Buttons.red(player.finChecker() + "finishForerunner_" + tile.getPosition(), "Done Moving"));
        return buttons;
    }

    private static List<Button> getForerunnerUnitButtonsForSystem(
            Game game, Player player, Tile destination, Tile source, List<String> movedUnits) {
        // Get buttons to move units from this system
        if (movedUnits == null) movedUnits = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder uh : source.getPlanetUnitHolders()) {
            String uhName = Helper.getPlanetRepresentation(uh.getName(), game);
            for (UnitKey uk : uh.getUnitsByState().keySet()) {
                if (!player.unitBelongsToPlayer(uk)) continue;
                if (player.getUnitFromUnitKey(uk) == null
                        || !player.getUnitFromUnitKey(uk).getIsGroundForce()) continue;

                // moved all of this unit already from this unit holder
                String unitStr = uk.asyncID() + " " + uh.getName();
                if (movedUnits != null
                        && movedUnits.stream().filter(s -> s.equals(unitStr)).count() >= uh.getUnitCount(uk)) continue;

                String id = player.finChecker() + "moveForerunner_" + destination.getPosition() + "_"
                        + source.getPosition() + "_" + uk.asyncID() + "_" + uh.getName();
                String label = uk.getUnitType().humanReadableName() + " from " + uhName;
                buttons.add(Buttons.green(id, label, uk.unitEmoji()));
            }
        }
        // Get buttons to UNDO moving units from this system
        if (movedUnits != null) {
            Set<String> uniqueUnits = new HashSet<>(movedUnits);
            for (String unit : uniqueUnits) {
                String[] data = unit.split(" ");
                UnitType type = Units.findUnitType(data[0]);
                String uhName = Helper.getPlanetRepresentation(data[1], game);
                if (type != null) {
                    String id = player.finChecker() + "undoForerunner_" + destination.getPosition() + "_"
                            + source.getPosition() + "_" + type + "_" + data[1];
                    String label = "Return " + type.humanReadableName() + " to " + uhName;
                    buttons.add(Buttons.red(id, label, type.getUnitTypeEmoji()));
                }
            }
        }
        // Choose another system button
        buttons.add(Buttons.gray(player.finChecker() + "startForerunner_" + destination.getPosition(), "Done Moving"));
        return buttons;
    }

    public static void serveLastDispatchButtons(Game game, Player player, String pos) {
        Tile tile = game.getTileByPosition(pos);
        if (tile == null) return;
        if (!player.hasUnit("ralnel_flagship")) return;

        String prefixID = player.finChecker() + "destroyUnit_" + pos + "_";
        List<Button> destroyable = new ArrayList<>();
        for (UnitKey uk : tile.getSpaceUnitHolder().getUnitsByState().keySet()) {
            if (player.unitBelongsToPlayer(uk)) continue;

            Player p2 = game.getPlayerFromColorOrFaction(uk.getColorID());
            if (p2 == null) continue;

            UnitModel model = p2.getUnitFromUnitKey(uk);
            if (!model.getSustainDamage()) {
                String id = prefixID + p2.getFaction() + "_" + uk.asyncID();
                String label =
                        "Destroy " + p2.getColor() + " " + uk.getUnitType().humanReadableName();
                destroyable.add(Buttons.red(id, label, uk.unitEmoji()));
            }
        }
    }

    /* ---------------------------------------------------------------------------|--------------------------------------------------------------------------- */
    /* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -- OTHER -- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */
    /* ---------------------------------------------------------------------------|--------------------------------------------------------------------------- */
    public static void serveIconoclastDeployAbility(Game game, Player relicDrawer) {
        for (Player player : game.getRealPlayers()) {
            if (player.is(relicDrawer)) continue;
            if (!player.hasUnit("naalu_mech_te") || ButtonHelper.isLawInPlay(game, "articles_war")) continue;

            if (ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) < 4) {
                List<Button> buttons = new ArrayList<>(
                        Helper.getPlanetPlaceUnitButtons(player, game, "mech", "placeOneNDone_skipbuild"));
                buttons.add(Buttons.DONE_DELETE_BUTTONS.withLabel("Decline To Deploy Mech"));

                UnitModel icono = Mapper.getUnit("naalu_mech_te");
                String msg = player.getRepresentationUnfogged() + ", you have " + icono.getNameRepresentation()
                        + ", and somebody just drew a relic.";
                msg += " You may use the buttons to DEPLOY 1 Iconoclast onto a planet you control:";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
            }
        }
    }
}
