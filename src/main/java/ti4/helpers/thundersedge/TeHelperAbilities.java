package ti4.helpers.thundersedge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.service.emoji.UnitEmojis;
import ti4.service.regex.RegexService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

public class TeHelperAbilities {

    /* ---------------------------------------------------------------------------|--------------------------------------------------------------------------- */
    /* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -- Last Bastion - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */
    /* ---------------------------------------------------------------------------|--------------------------------------------------------------------------- */
    @ButtonHandler("liberate_")
    private static void liberatePlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "liberate_" + RegexHelper.unitHolderRegex(game, "planet");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String planet = matcher.group("planet");
            String msg = player.getRepresentation() + " used Liberate to ";

            Tile tile = game.getTileFromPlanet(planet);
            Planet p = tile != null ? tile.getUnitHolderFromPlanet(planet) : null;
            PlanetModel model = Mapper.getPlanet(planet);
            String planetName = model != null ? model.getName() : planet;

            if (p != null) {
                if (p.getUnitCount(UnitType.Infantry, player.getColor()) >= p.getResources()) {
                    player.refreshPlanet(planet);
                    msg += "ready " + planetName;
                } else {
                    p.addUnit(Mapper.getUnitKey("gf", player.getColor()), 1);
                    msg += "add 1 infantry to " + planetName;
                }
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                ButtonHelper.deleteMessage(event);
            } else {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(), "Could not find planet. Yell at jazz and resolve manually");
            }
        });
    }

    /* ---------------------------------------------------------------------------|--------------------------------------------------------------------------- */
    /* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -- Crimson Rebellion -- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */
    /* ---------------------------------------------------------------------------|--------------------------------------------------------------------------- */
    @ButtonHandler("statusRemoveBreach_")
    private static void removeBreach(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "statusRemoveBreach_" + RegexHelper.posRegex(game);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            tile.getSpaceUnitHolder().removeToken(Constants.TOKEN_BREACH_ACTIVE);
            String msg = player.getRepresentation(false, false) + " removed active breach from "
                    + tile.getRepresentationForButtons(game, player);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            ButtonHelper.deleteTheOneButton(event);
        });
    }

    /* ---------------------------------------------------------------------------|--------------------------------------------------------------------------- */
    /* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -- RalNel Consortium -- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */
    /* ---------------------------------------------------------------------------|--------------------------------------------------------------------------- */
    // Helpers for all of RalNel's broken movement abilities
    public static String unitSummary(Game game, Player player, HashMap<String, List<String>> moveMap) {
        if (moveMap.isEmpty()) return "\n> No units are being moved yet.";
        StringBuilder sb = new StringBuilder();
        for (Entry<String, List<String>> system : moveMap.entrySet()) {
            Tile systemFrom = game.getTileByPosition(system.getKey());
            sb.append("\n> Moving units from ")
                    .append(systemFrom.getRepresentationForButtons())
                    .append(":");
            for (String unit : system.getValue()) {
                String[] data = unit.split(" ");
                UnitType type = Units.findUnitType(data[0]);
                String uhName = data[1].equals("space") ? "" : " from " + Helper.getPlanetRepresentation(data[1], game);
                sb.append("\n> - ").append(type.humanReadableName()).append(uhName);
            }
        }
        return sb.toString();
    }

    public static HashMap<String, List<String>> readMoveMap(String val) {
        HashMap<String, List<String>> moveMap = new HashMap<>();
        if (val == null || val.isBlank()) return moveMap;
        List<String> systemInfos = Arrays.asList(val.split("\\|"));
        for (String system : systemInfos) {
            String[] data = system.split(";");
            String pos = data[0];
            List<String> units = new ArrayList<>(Arrays.asList(data[1].split(",")));
            moveMap.put(pos, units);
        }
        return moveMap;
    }

    public static String storeMovementMap(HashMap<String, List<String>> moveMap) {
        StringBuilder sb = new StringBuilder();
        for (Entry<String, List<String>> system : moveMap.entrySet()) {
            if (!sb.isEmpty()) sb.append("|");
            sb.append(system.getKey()).append(";");
            sb.append(String.join(",", system.getValue()));
        }
        return sb.toString();
    }

    @ButtonHandler("miniLanding_")
    private static void handleMiniLanding(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "miniLanding_" + RegexHelper.posRegex() + "_" + RegexHelper.intRegex("count")
                + RegexHelper.unitTypeRegex() + "_" + RegexHelper.unitHolderRegex(game, "planet");
        Matcher matcher = Pattern.compile(regex).matcher(buttonID);
        if (matcher.matches()) {
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            UnitKey uk = Units.getUnitKey(matcher.group("unittype"), player.getColorID());
            String planet = matcher.group("planet");
            int count = Integer.parseInt(matcher.group("count"));

            String unitName = uk.getUnitType().humanReadableName();
            String planetName = Helper.getPlanetRepresentation(planet, game);
            String message =
                    player.getRepresentation(false, false) + " landed " + count + " " + unitName + " on " + planetName;
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);

            UnitHolder space = tile.getSpaceUnitHolder();
            Planet p = tile.getUnitHolderFromPlanet(planet);
            if (p != null) {
                List<Integer> states = space.removeUnit(uk, count);
                p.addUnitsWithStates(uk, states);
            }
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Error parsing buttonID `" + buttonID + "`");
        }

        // Update Buttons
        MessageHelper.editMessageButtons(event, miniLandingButtons(game, player));
    }

    public static List<Button> miniLandingButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        if (activeSystem != null) {
            int dmgDocks =
                    activeSystem.getSpaceUnitHolder().getDamagedUnitCount(UnitType.Spacedock, player.getColorID());
            int docks = activeSystem.getSpaceUnitHolder().getUnitCount(UnitType.Spacedock, player) - dmgDocks;
            int dmgPds = activeSystem.getSpaceUnitHolder().getDamagedUnitCount(UnitType.Pds, player.getColorID());
            int pds = activeSystem.getSpaceUnitHolder().getUnitCount(UnitType.Pds, player) - dmgPds;

            for (Planet planet : activeSystem.getPlanetUnitHolders()) {
                for (int x = 1; x <= Math.min(2, pds); x++) {
                    String id = player.finChecker() + "miniLanding_" + activeSystem.getPosition() + "_" + x + "pd_"
                            + planet.getName();
                    String label = "Land " + x + " PDS on " + Helper.getPlanetRepresentation(planet.getName(), game);
                    buttons.add(Buttons.red(id, label, UnitEmojis.pds));
                }
                for (int x = 1; x <= Math.min(2, dmgPds); x++) {
                    String id = player.finChecker() + "miniLanding_" + activeSystem.getPosition() + "_" + x + "pd_"
                            + planet.getName();
                    String label =
                            "Land " + x + " damaged PDS on " + Helper.getPlanetRepresentation(planet.getName(), game);
                    buttons.add(Buttons.red(id, label, UnitEmojis.pds));
                }
                for (int x = 1; x <= Math.min(1, docks); x++) {
                    String id = player.finChecker() + "miniLanding_" + activeSystem.getPosition() + "_" + x + "sd_"
                            + planet.getName();
                    String label =
                            "Land " + x + " Space Dock on " + Helper.getPlanetRepresentation(planet.getName(), game);
                    buttons.add(Buttons.red(id, label, UnitEmojis.spacedock));
                }
                for (int x = 1; x <= Math.min(1, dmgDocks); x++) {
                    String id = player.finChecker() + "miniLanding_" + activeSystem.getPosition() + "_" + x + "sd_"
                            + planet.getName();
                    String label = "Land " + x + " damaged Space Dock on "
                            + Helper.getPlanetRepresentation(planet.getName(), game);
                    buttons.add(Buttons.red(id, label, UnitEmojis.spacedock));
                }
            }
        }
        if (buttons.size() > 0) buttons.add(Buttons.DONE_DELETE_BUTTONS);
        return buttons;
    }

    @ButtonHandler("startSurvival_")
    private static void chooseSystemForSurvival(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        HashMap<String, List<String>> survivalMap = readMoveMap(game.getStoredValue("survivalInstinctMap"));
        Matcher matcher;
        String regex1 = "startSurvival_" + RegexHelper.posRegex(); // start_activeSystem
        String regex2 = regex1 + "_" + RegexHelper.posRegex("source"); // start_activeSystem_sourceSystem

        if ((matcher = Pattern.compile(regex1).matcher(buttonID)).matches()) {
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            List<Button> buttons = getSurvivalInstinctSystemButtons(game, player, tile, survivalMap);
            String message = player.getRepresentation() + " Choose a system to use Survival Instinct, or click DONE:";
            message += unitSummary(game, player, survivalMap);
            MessageHelper.editMessageWithButtons(event, message, buttons);

        } else if ((matcher = Pattern.compile(regex2).matcher(buttonID)).matches()) {
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            Tile source = game.getTileByPosition(matcher.group("source"));
            List<Button> buttons = getSurvivalInstinctUnitButtonsForSystem(
                    game, player, tile, source, survivalMap.get(matcher.group("source")));
            String message = player.getRepresentation()
                    + " Choose units to move from the system using Survival Instinct, or click DONE to move from a different system:";
            message += unitSummary(game, player, survivalMap);
            MessageHelper.editMessageWithButtons(event, message, buttons);

        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Error " + Constants.jazzPing());
        }
    }

    @ButtonHandler("moveSurvival_")
    @ButtonHandler("undoSurvival_")
    private static void moveUnitWithSurvival(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        HashMap<String, List<String>> survivalMap = readMoveMap(game.getStoredValue("survivalInstinctMap"));
        String regex = "(undo|move)Survival_" + RegexHelper.posRegex(game, "destination") + "_"
                + RegexHelper.posRegex(game, "source") + "_" + RegexHelper.unitTypeRegex() + "_"
                + RegexHelper.unitHolderRegex(game, "planet");
        Matcher matcher = Pattern.compile(regex).matcher(buttonID);
        if (matcher.matches()) {
            Tile destination = game.getTileByPosition(matcher.group("destination"));
            String pos = matcher.group("source");
            Tile source = game.getTileByPosition(pos);
            String unitStr = matcher.group("unittype") + " " + matcher.group("planet");

            if (buttonID.startsWith("move")) {
                if (!survivalMap.containsKey(pos)) survivalMap.put(pos, new ArrayList<>());
                survivalMap.get(pos).add(unitStr);
            } else {
                if (survivalMap.containsKey(pos)) survivalMap.get(pos).remove(unitStr);
                if (survivalMap.containsKey(pos) && survivalMap.get(pos).isEmpty()) survivalMap.remove(pos);
            }
            game.setStoredValue("survivalInstinctMap", storeMovementMap(survivalMap));

            List<Button> buttons =
                    getSurvivalInstinctUnitButtonsForSystem(game, player, destination, source, survivalMap.get(pos));
            String message = player.getRepresentation()
                    + " Choose units to move from the system using Survival Instinct, or click DONE to move from a different system:";
            message += unitSummary(game, player, survivalMap);
            MessageHelper.editMessageWithButtons(event, message, buttons);
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Error " + Constants.jazzPing());
        }
    }

    @ButtonHandler("finishSurvival_")
    private static void finishedWithSurvival(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "finishSurvival_" + RegexHelper.posRegex();
        Matcher matcher = Pattern.compile(regex).matcher(buttonID);
        if (matcher.matches()) {
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            HashMap<String, List<String>> survivalMap = readMoveMap(game.getStoredValue("survivalInstinctMap"));
            for (String source : survivalMap.keySet()) {
                List<String> units =
                        survivalMap.get(source).stream().collect(Collectors.groupingBy(s -> s)).entrySet().stream()
                                .map(e -> e.getValue().size() + " " + e.getKey())
                                .toList();
                List<String> unitsTo = survivalMap.get(source).stream()
                        .map(unit -> unit.substring(0, unit.indexOf(' ')))
                        .collect(Collectors.groupingBy(s -> s))
                        .entrySet()
                        .stream()
                        .map(e -> e.getValue().size() + " " + e.getKey())
                        .toList();
                String unitStrFrom = String.join(", ", units);
                String unitStrTo = String.join(", ", unitsTo);
                RemoveUnitService.removeUnits(event, tile, game, player.getColor(), unitStrFrom);
                AddUnitService.addUnits(event, tile, game, player.getColor(), unitStrTo);
            }
            game.removeStoredValue("survivalInstinctMap");
            String msg = player.getRepresentation()
                    + " Moved the following units into the active system with Survival Instinct:"
                    + unitSummary(game, player, survivalMap);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            ButtonHelper.deleteMessage(event);
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Error " + Constants.jazzPing());
        }
    }

    public static List<Button> getSurvivalInstinctSystemButtons(
            Game game, Player player, Tile tile, HashMap<String, List<String>> survivalMap) {
        // Get the tiles that are valid sources for Survival Instinct
        if (survivalMap == null) survivalMap = new HashMap<>();
        List<Button> buttons = new ArrayList<>();
        for (String adj : FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false)) {
            Tile t = game.getTileByPosition(adj);
            if (t != null && !t.hasPlayerCC(player)) {
                if (FoWHelper.playerHasShipsInSystem(player, t) || survivalMap.containsKey(adj)) {
                    String id = player.finChecker() + "startSurvival_" + tile.getPosition() + "_" + t.getPosition();
                    String label = t.getRepresentationForButtons(game, player);
                    buttons.add(Buttons.green(id, label));
                }
            }
        }
        buttons.add(Buttons.red(player.finChecker() + "finishSurvival_" + tile.getPosition(), "Done moving"));
        return buttons;
    }

    private static List<Button> getSurvivalInstinctUnitButtonsForSystem(
            Game game, Player player, Tile destination, Tile source, List<String> movedUnits) {
        // Get buttons to move units from this system
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder uh : source.getUnitHolders().values()) {
            String uhName = uh.getName().equals("space") ? "Space" : Helper.getPlanetRepresentation(uh.getName(), game);
            for (UnitKey uk : uh.getUnits().keySet()) {
                if (!player.unitBelongsToPlayer(uk)) continue;

                // franken compat
                if (List.of(UnitType.Pds, UnitType.Spacedock).contains(uk.getUnitType())
                        && !player.hasAbility("miniaturization")) continue;
                if (List.of(UnitType.PlenaryOrbital).contains(uk.getUnitType())) continue;

                // moved all of this unit already from this unit holder
                String unitStr = uk.asyncID() + " " + uh.getName();
                if (movedUnits != null
                        && movedUnits.stream().filter(s -> s.equals(unitStr)).count()
                                >= uh.getUnits().get(uk)) continue;

                // otherwise, add the button
                String id = player.finChecker() + "moveSurvival_" + destination.getPosition() + "_"
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
                String uhName = data[1].equals("space") ? "Space" : Helper.getPlanetRepresentation(data[1], game);
                if (type != null) {
                    String id = player.finChecker() + "undoSurvival_" + destination.getPosition() + "_"
                            + source.getPosition() + "_" + type.toString() + "_" + data[1];
                    String label = "Return " + type.humanReadableName() + " to " + uhName;
                    buttons.add(Buttons.red(id, label, type.getUnitTypeEmoji()));
                }
            }
        }
        // Choose another system button
        buttons.add(Buttons.gray(
                player.finChecker() + "startSurvival_" + destination.getPosition(), "Done with this system"));
        return buttons;
    }
}
