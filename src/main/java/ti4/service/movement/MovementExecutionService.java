package ti4.service.movement;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import lombok.Data;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.CheckDistanceHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.map.*;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.fow.FOWPlusService;
import ti4.service.fow.GMService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.planet.FlipTileService;
import ti4.map.manage.GameManager;

@UtilityClass
public class MovementExecutionService {

    @Data
    public class MovementExecutionResult {
        private String message;
        private List<Button> buttons = new ArrayList<>();
    }

    public MovementExecutionResult executeMovement(@Nullable ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        MovementExecutionResult result = new MovementExecutionResult();

        if (FOWPlusService.isVoid(game, tile.getPosition())) {
            FOWPlusService.resolveVoidActivation(player, game);
            Button conclude = Buttons.red(player.finChecker() + "doneWithTacticalAction", "Conclude Tactical Action");
            result.setMessage("All units were lost to the void.");
            result.getButtons().add(conclude);
            return result;
        }

        String movementSummary = buildMovementSummaryMessage(game, player);
        boolean unitsWereMoved = moveUnitsIntoActiveSystem(event, game, player, tile);
        tile = game.getTileByPosition(tile.getPosition());
        spendAndPlaceTokenIfNecessary(event, game, player, tile);
        GameManager.save(game, "Movement Execution");

        boolean hasGfsInRange = game.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander")
            || tile.getSpaceUnitHolder().getUnitCount(UnitType.Infantry, player) > 0
            || tile.getSpaceUnitHolder().getUnitCount(UnitType.Mech, player) > 0;

        List<Button> systemButtons;
        String message;
        if (!unitsWereMoved && !hasGfsInRange) {
            message = "Nothing moved. Use buttons to decide if you wish to build (if you can), or finish the activation.";
            systemButtons = getBuildButtons(game, player, tile);
        } else {
            message = unitsWereMoved ? movementSummary : "No units moved, but you can land units you already have in the system.";
            systemButtons = getLandingTroopsButtons(game, player, tile);
            if (unitsWereMoved && event != null) {
                ButtonHelperTacticalAction.resolveAfterMovementEffects(event, game, player, tile, unitsWereMoved);
            }
        }

        int landingButtons = 1;
        if (!game.getStoredValue("possiblyUsedRift").isEmpty()) landingButtons = 2;
        if (systemButtons.size() == landingButtons || game.isL1Hero()) {
            systemButtons = getBuildButtons(game, player, tile);
        }

        result.setMessage(message);
        result.setButtons(systemButtons);

        CommanderUnlockCheckService.checkPlayer(player, "naaz", "empyrean", "ghost");
        CommanderUnlockCheckService.checkPlayer(player, "nivyn", "ghoti", "zelian", "gledge", "mortheus");
        CommanderUnlockCheckService.checkAllPlayersInGame(game, "empyrean");

        return result;
    }

    private static void spendAndPlaceTokenIfNecessary(@Nullable ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        boolean skipPlacingAbilities = game.isNaaluAgent() || game.isL1Hero() || (!game.getStoredValue("hiredGunsInPlay").isEmpty() && player != game.getActivePlayer());
        if (!skipPlacingAbilities && !CommandCounterHelper.hasCC(event, player.getColor(), tile) && game.getStoredValue("vaylerianHeroActive").isEmpty()) {
            if (!game.getStoredValue("absolLux").isEmpty()) {
                player.setTacticalCC(player.getTacticalCC() + 1);
            }
            player.setTacticalCC(player.getTacticalCC() - 1);
            CommandCounterHelper.addCC(event, player, tile);
        }
    }

    private static boolean moveUnitsIntoActiveSystem(@Nullable ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        if (!game.getTacticalActionDisplacement().isEmpty()) {
            Tile flippedTile = FlipTileService.flipTileIfNeeded(event, tile, game);
            if (flippedTile == null) {
                if (event != null) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Failed to flip mallice. Please yell at Jazzxhands");
                }
                return false;
            }
            tile = flippedTile;
        }

        boolean moved = false;
        UnitHolder activeSystemSpace = tile.getSpaceUnitHolder();
        for (Entry<String, Map<UnitKey, List<Integer>>> e : game.getTacticalActionDisplacement().entrySet()) {
            for (Entry<UnitKey, List<Integer>> unit : e.getValue().entrySet()) {
                if (unit.getValue().stream().collect(Collectors.summingInt(x -> x)) > 0)
                    moved = true;
                activeSystemSpace.addUnitsWithStates(unit.getKey(), unit.getValue());
            }
        }
        game.getTacticalActionDisplacement().clear();
        return moved;
    }

    public static List<Button> getLandingTroopsButtons(Game game, Player player, Tile tile) {
        List<Button> buttons = getLandingUnitsButtons(game, player, tile);
        if (game.isNaaluAgent() || player == game.getActivePlayer()) {
            buttons.add(Buttons.red(player.finChecker() + "doneLanding_" + tile.getPosition(), "Done Landing Troops"));
        } else {
            buttons.add(Buttons.red(player.finChecker() + "deleteButtons", "Done Resolving"));
        }
        return buttons;
    }

    public static List<Button> getBuildButtons(Game game, Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();

        int productionVal = Helper.getProductionValue(player, game, tile, false);
        if (productionVal > 0) {
            String id = player.finChecker() + "tacticalActionBuild_" + tile.getPosition();
            String label = "Build in This System (" + productionVal + " PRODUCTION value)";
            buttons.add(Buttons.green(id, label));
        }
        if (!game.getStoredValue("possiblyUsedRift").isEmpty()) {
            buttons.add(Buttons.green(player.finChecker() + "getRiftButtons_" + tile.getPosition(), "Units Travelled Through Gravity Rift", MiscEmojis.GravityRift));
        }
        if (player.hasUnexhaustedLeader("sardakkagent")) {
            buttons.addAll(ButtonHelperAgents.getSardakkAgentButtons(game));
        }
        if (player.hasUnexhaustedLeader("nomadagentmercer")) {
            buttons.addAll(ButtonHelperAgents.getMercerAgentInitialButtons(game, player));
        }
        if (player.hasAbility("shroud_of_lith") && ButtonHelperFactionSpecific.getKolleccReleaseButtons(player, game).size() > 1) {
            buttons.add(Buttons.blue("shroudOfLithStart", "Use Shroud of Lith", FactionEmojis.kollecc));
        }
        buttons.add(Buttons.red(player.finChecker() + "doneWithTacticalAction", "Conclude Tactical Action"));
        return buttons;
    }

    public static List<Button> getLandingUnitsButtons(Game game, Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        List<Button> unlandUnitButtons = new ArrayList<>();

        UnitHolder space = tile.getSpaceUnitHolder();
        boolean hasMechInReinforcements = ButtonHelperFactionSpecific.vortexButtonAvailable(game, Units.getUnitKey(UnitType.Mech, player.getColor()));

        List<UnitType> committable = new ArrayList<>(List.of(UnitType.Mech, UnitType.Infantry));
        boolean naaluFS = (player.hasUnit("naalu_flagship") || player.hasUnit("sigma_naalu_flagship_2")) && space.getUnitCount(UnitType.Flagship, player) > 0;
        boolean belkoFF = player.hasUnit("belkosea_fighter") || player.hasUnit("belkosea_fighter2");
        if (naaluFS || belkoFF) committable.add(UnitType.Fighter);

        String landPrefix = player.finChecker() + "landUnits_" + tile.getPosition() + "_";
        String unlandPrefix = player.finChecker() + "spaceUnits_" + tile.getPosition() + "_";
        for (Planet planet : tile.getPlanetUnitHolders()) {
            boolean containsDMZ = planet.getTokenList().stream().anyMatch(token -> token.contains(Constants.DMZ_LARGE));
            if (containsDMZ) continue;

            String planetName = planet.getName();
            String planetRep = Helper.getPlanetRepresentation(planet.getName(), game);

            // Get landing and un-landing buttons for each GF / State combo
            for (UnitType gf : committable) {
                for (UnitState state : UnitState.values()) {
                    String stateStr = state != UnitState.none ? " " + state.humanDescr() : "";
                    for (int x = 1; x <= 2; x++) {
                        if (space.getUnitCountForState(gf, player, state) >= x) {
                            String id = landPrefix + x + gf.getValue() + "_" + planetName + "_" + player.getColor();
                            String label = "Land " + x + stateStr + " " + gf.humanReadableName() + " on " + planetRep;
                            buttons.add(Buttons.red(id, label, gf.getUnitTypeEmoji()));
                        }
                        if (planet.getUnitCountForState(gf, player, state) >= x) {
                            String id = unlandPrefix + x + gf.getValue() + "_" + planetName + "_" + player.getColor();
                            String label = "Un-land " + x + stateStr + " " + gf.humanReadableName() + " from " + planetRep;
                            unlandUnitButtons.add(Buttons.gray(id, label, gf.getUnitTypeEmoji()));
                        }
                        for (Player p2 : game.getRealPlayers()) {
                            if (player.getAllianceMembers().contains(p2.getFaction()) && player != p2) {
                                String color = " " + StringUtils.capitalize(p2.getColor()) + " ";
                                if (space.getUnitCountForState(gf, p2, state) >= x) {
                                    String id = landPrefix + x + gf.getValue() + "_" + planetName + "_" + p2.getColor();
                                    String label = "Land " + x + color + stateStr + " " + gf.humanReadableName() + " on " + planetRep;
                                    buttons.add(Buttons.red(id, label, gf.getUnitTypeEmoji()));
                                }
                                if (planet.getUnitCountForState(gf, p2, state) >= x) {
                                    String id = unlandPrefix + x + gf.getValue() + "_" + planetName + "_" + p2.getColor();
                                    String label = "Un-land " + x + color + stateStr + " " + gf.humanReadableName() + " from " + planetRep;
                                    unlandUnitButtons.add(Buttons.gray(id, label, gf.getUnitTypeEmoji()));
                                }
                            }
                        }
                    }
                }
            }

            // Special landing buttons for planet
            if (planet.getUnitCount(UnitType.Infantry, player) > 0 || planet.getUnitCount(UnitType.Mech, player) > 0) {
                if (player.hasUnexhaustedLeader("dihmohnagent")) {
                    String id = "exhaustAgent_dihmohnagent_" + planetName;
                    String label = "Use Dih-Mohn Agent on " + planetRep;
                    buttons.add(Buttons.green(id, label, FactionEmojis.dihmohn));
                }
            }

            boolean tnelis = player.hasUnit("tnelis_mech") && tile.getSpaceUnitHolder().getUnitCount(UnitType.Destroyer, player) > 0;
            if (tnelis && hasMechInReinforcements) {
                String id = "tnelisDeploy_" + planetName;
                String label = "Deploy Mech on " + planetRep;
                buttons.add(Buttons.green(id, label, FactionEmojis.tnelis));
            }

            buttons.addAll(unlandUnitButtons);
            unlandUnitButtons.clear();
        }

        if (game.playerHasLeaderUnlockedOrAlliance(player, "sardakkcommander")) {
            buttons.addAll(ButtonHelperCommanders.getSardakkCommanderButtons(game, player, null));
        }
        if (player.getPromissoryNotes().containsKey("ragh")) {
            buttons.addAll(ButtonHelperFactionSpecific.getRaghsCallButtons(player, game, tile));
        }
        if (!game.getStoredValue("possiblyUsedRift").isEmpty()) {
            buttons.add(Buttons.green(player.finChecker() + "getRiftButtons_" + tile.getPosition(), "Units Travelled Through Gravity Rift", MiscEmojis.GravityRift));
        }
        if (player.hasAbility("combat_drones") && FoWHelper.playerHasFightersInSystem(player, tile)) {
            buttons.add(Buttons.blue(player.finChecker() + "combatDrones", "Use Combat Drones Ability", FactionEmojis.mirveda));
        }
        if (player.hasAbility("shroud_of_lith") && ButtonHelperFactionSpecific.getKolleccReleaseButtons(player, game).size() > 1) {
            buttons.add(Buttons.blue("shroudOfLithStart", "Use Shroud of Lith", FactionEmojis.kollecc));
            buttons.add(Buttons.gray("refreshLandingButtons", "Refresh Landing Buttons", FactionEmojis.kollecc));
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "mirvedacommander")) {
            buttons.add(Buttons.blue(player.finChecker() + "offerMirvedaCommander", "Use Mirveda Commander", FactionEmojis.mirveda));
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "ghostcommander")) {
            buttons.add(Buttons.blue(player.finChecker() + "placeGhostCommanderFF_" + tile.getPosition(), "Place Fighter with Creuss Commander", FactionEmojis.Ghost));
        }
        if (!tile.getPlanetUnitHolders().isEmpty() && game.playerHasLeaderUnlockedOrAlliance(player, "khraskcommander")) {
            buttons.add(Buttons.blue(player.finChecker() + "placeKhraskCommanderInf_" + tile.getPosition(), "Place Infantry with Khrask Commander", FactionEmojis.khrask));
        }
        if (player.hasUnexhaustedLeader("nokaragent") && FoWHelper.playerHasShipsInSystem(player, tile)) {
            buttons.add(Buttons.gray("exhaustAgent_nokaragent_" + player.getFaction(), "Use Nokar Agent to Place 1 Destroyer", FactionEmojis.nokar));
        }
        if (player.hasUnexhaustedLeader("tnelisagent") && FoWHelper.playerHasShipsInSystem(player, tile) && FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game)) {
            buttons.add(Buttons.gray("exhaustAgent_tnelisagent_" + player.getFaction(), "Use Tnelis Agent", FactionEmojis.tnelis));
        }
        if (player.hasUnexhaustedLeader("zelianagent") && tile.getUnitHolders().get("space").getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
            buttons.add(Buttons.gray("exhaustAgent_zelianagent_" + player.getFaction(), "Use Zelian Agent Yourself", FactionEmojis.zelian));
        }
        if (player.hasLeaderUnlocked("muaathero") && !tile.isMecatol() && !tile.isHomeSystem() && ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Warsun).contains(tile)) {
            buttons.add(Buttons.blue(player.finChecker() + "novaSeed_" + tile.getPosition(), "Nova Seed This Tile", FactionEmojis.Muaat));
        }
        if (player.hasLeaderUnlocked("zelianhero") && !tile.isMecatol() && ButtonHelper.getTilesOfUnitsWithBombard(player, game).contains(tile)) {
            buttons.add(Buttons.blue(player.finChecker() + "celestialImpact_" + tile.getPosition(), "Celestial Impact This Tile", FactionEmojis.zelian));
        }
        if (player.hasLeaderUnlocked("sardakkhero") && !tile.getPlanetUnitHolders().isEmpty()) {
            buttons.add(Buttons.blue(player.finChecker() + "purgeSardakkHero", "Use N'orr Hero", FactionEmojis.Sardakk));
        }
        if (player.hasLeaderUnlocked("atokeraherp") && !tile.getPlanetUnitHolders().isEmpty()) {
            buttons.add(Buttons.blue(player.finChecker() + "purgeAtokeraHero", "Use Atokera Hero", FactionEmojis.atokera));
        }
        if (player.hasLeaderUnlocked("rohdhnahero")) {
            buttons.add(Buttons.blue(player.finChecker() + "purgeRohdhnaHero", "Use Roh'Dhna Hero", FactionEmojis.rohdhna));
        }
        if (tile.getUnitHolders().size() > 1 && ButtonHelper.getTilesOfUnitsWithBombard(player, game).contains(tile)) {
            if (tile.getUnitHolders().size() > 2) {
                buttons.add(Buttons.gray("bombardConfirm_combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment, "Roll BOMBARDMENT"));
            } else {
                buttons.add(Buttons.gray("combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment, "Roll BOMBARDMENT"));
            }
        }

        return buttons;
    }

    private static String buildMovementSummaryMessage(Game game, Player player) {
        StringBuilder sb = new StringBuilder("## Tactical Action in system ");
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        sb.append(activeSystem.getRepresentationForButtons(game, player)).append(":\n\n");

        Set<String> positions = positionsMovedFrom(game);
        if (positions.isEmpty() && game.getTacticalActionDisplacement().isEmpty()) {
            return "No units moved.";
        }
        List<String> summaries = summariesPerSystem(game, player, positions, false);
        sb.append(String.join("\n\n", summaries));
        if (sb.length() > 1950)
            return buildCondensedMovementSummaryMessage(game, player);
        return sb.toString();
    }

    private static String buildCondensedMovementSummaryMessage(Game game, Player player) {
        StringBuilder sb = new StringBuilder("## Tactical Action in system ");
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        sb.append(activeSystem.getRepresentationForButtons(game, player)).append(":\n\n");

        Set<String> positions = positionsMovedFrom(game);
        List<String> summaries = summariesPerSystem(game, player, positions, true);
        sb.append(String.join("\n\n", summaries));
        return sb.toString();
    }

    private static List<String> summariesPerSystem(Game game, Player player, Set<String> positions, boolean condensed) {
        List<String> summaries = new ArrayList<>(positions.stream()
            .map(game::getTileByPosition)
            .map(tile -> buildMessageForSingleSystem(game, player, tile, condensed, false))
            .toList());
        String remainder = buildShortSummary(game, positions);
        if (remainder != null) summaries.add(remainder);
        return summaries;
    }

    private static String buildMessageForSingleSystem(Game game, Player player, Tile tile, boolean condensed, boolean inclSummary) {
        String linePrefix = "> " + player.getFactionEmoji();
        int distance = CheckDistanceHelper.getDistanceBetweenTwoTiles(game, player, tile.getPosition(), game.getActiveSystem(), true);
        int riftDistance = CheckDistanceHelper.getDistanceBetweenTwoTiles(game, player, tile.getPosition(), game.getActiveSystem(), false);

        var displaced = game.getTacticalActionDisplacement();
        Set<UnitKey> movingUnitsFromTile = displaced.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(tile.getPosition() + "-"))
            .map(Entry::getValue)
            .filter(Objects::nonNull)
            .flatMap(f -> f.entrySet().stream())
            .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
            .map(Entry::getKey)
            .collect(Collectors.toSet());

        String header = (condensed ? "-# From system " : "From system ");
        StringBuilder summary = new StringBuilder(header).append(tile.getRepresentationForButtons(game, player));
        if (!condensed) {
            summary.append(" (").append(distance).append(" tile").append(distance == 1 ? "" : "s").append(" away)").append("\n");
        } else {
            summary.append(" (").append(distance).append(" away)").append("\n");
        }
        if (movingUnitsFromTile.isEmpty()) {
            if (condensed) return null;
            return summary + "> Nothing";
        }

        List<String> lines = new ArrayList<>();
        for (UnitHolder uh : tile.getUnitHolders().values()) {
            String uhKey = tile.getPosition() + "-" + uh.getName();
            if (!displaced.containsKey(uhKey)) continue;

            Map<UnitKey, List<Integer>> unitMap = displaced.get(uhKey);
            if (unitMap == null) {
                displaced.remove(uhKey);
                continue;
            }

            for (UnitKey key : new HashSet<>(unitMap.keySet())) {
                if (unitMap.get(key) == null) {
                    unitMap.remove(key);
                    continue;
                }

                List<Integer> states = unitMap.get(key);
                if (condensed) {
                    int amt = states.stream().collect(Collectors.summingInt(i -> i));
                    String unitStr = key.unitEmoji().emojiString().repeat(amt);
                    if (amt > 2) unitStr = amt + "x " + key.unitEmoji();
                    lines.add(unitStr);
                    continue;
                }
                String color = "";
                if (!key.getColor().equalsIgnoreCase(player.getColor())) {
                    color = " " + key.getColor() + " ";
                }
                for (UnitState state : UnitState.values()) {
                    int amt = states.get(state.ordinal());
                    if (amt == 0) continue;

                    String stateStr = (state == UnitState.none) ? "" : " " + state.humanDescr();
                    String unitMoveStr = linePrefix + " moved " + amt + color + stateStr + " " + key.unitEmoji();

                    String unitHolderStr = (uh instanceof Planet p) ? " from the planet " + p.getRepresentation(game) : "";
                    unitMoveStr += unitHolderStr;

                    String distanceStr = validateMoveValue(game, player, tile, key, movingUnitsFromTile, distance, riftDistance);
                    unitMoveStr += distanceStr;
                    lines.add(unitMoveStr);
                }
            }
        }
        if (condensed) {
            summary.append(String.join(", ", lines));
            return summary.toString();
        }
        summary.append(String.join("\n", lines));
        String extraSummary = buildShortSummary(game, Set.of(tile.getPosition()));
        if (extraSummary != null && inclSummary) summary.append("\n").append(extraSummary);
        return summary.toString();
    }

    private static Set<String> positionsMovedFrom(Game game) {
        return game.getTacticalActionDisplacement().keySet().stream()
            .map(uhKey -> uhKey.split("-")[0])
            .filter(pos -> game.getTileByPosition(pos) != null)
            .collect(Collectors.toSet());
    }

    private static String buildShortSummary(Game game, Set<String> excludeTiles) {
        StringBuilder sb = new StringBuilder("-# Units from elsewhere: ");
        Map<UnitKey, Integer> quantities = new HashMap<>();
        for (var entry : game.getTacticalActionDisplacement().entrySet()) {
            String pos = entry.getKey().split("-")[0];
            if (excludeTiles.contains(pos)) continue;
            for (var unitEntry : entry.getValue().entrySet()) {
                int amt = unitEntry.getValue().stream().collect(Collectors.summingInt(a -> a));
                UnitKey key = unitEntry.getKey();
                quantities.put(key, quantities.getOrDefault(key, 0) + amt);
            }
        }
        List<String> units = new ArrayList<>();
        for (UnitKey key : quantities.keySet()) {
            int amt = quantities.get(key);
            String unitStr = key.unitEmoji().emojiString().repeat(amt);
            if (amt > 2) unitStr = amt + "x " + key.unitEmoji();
            units.add(unitStr);
        }
        if (units.isEmpty()) return null;
        sb.append(String.join(", ", units));
        return sb.toString();
    }

    private static String validateMoveValue(Game game, Player player, Tile tile, UnitKey unit, Set<UnitKey> allMovingUnits, int distance, int riftDistance) {
        int moveValue = getUnitMoveValue(game, player, tile, unit, false);
        if (moveValue == 0) return "";

        String output = "";
        if (distance > moveValue && distance < 90) {
            output += " (distance exceeds move value (" + distance + " > " + moveValue + ")";
            int maxBonus = 0;
            if (player.hasTech("gd")) {
                maxBonus++;
                output += ", used _Gravity Drive_)";
            } else {
                output += ", __does not have _Gravity Drive___)";
            }
            if (player.getTechs().contains("dsgledb")) {
                maxBonus++;
                output += " (has _Lightning Drives_ for +1 movement if not transporting)";
            }
            if (riftDistance < distance) {
                // maxBonus += distance - riftDistance; // Don't automatically count rifts, allow the GM to verify
                output += " (gravity rifts along a path could add +" + (distance - riftDistance) + " movement if used)";
                game.setStoredValue("possiblyUsedRift", "yes");
            }
            if ((distance > (moveValue + maxBonus)) && game.isFowMode()) {
                GMService.logPlayerActivity(game, player, output);
            }
        }
        if (riftDistance < distance) {
            game.setStoredValue("possiblyUsedRift", "yes");
        }
        if (player.hasAbility("celestial_guides")) {
            game.setStoredValue("possiblyUsedRift", "");
        }
        return output;
    }

    private static int getUnitMoveValue(Game game, Player player, Tile tile, UnitKey unit, boolean skipBonus) {
        UnitModel model = player.getUnitFromUnitKey(unit);

        boolean movingFromHome = tile == player.getHomeSystemTile();
        boolean tileHasWormhole = FoWHelper.doesTileHaveAlphaOrBeta(game, tile.getPosition());

        // Calculate base move value (pretty easy)
        int baseMoveValue = model.getMoveValue();
        if (baseMoveValue == 0) return 0;
        if (tile.isNebula() && !player.hasAbility("voidborn") && !player.hasTech("absol_amd")) {
            baseMoveValue = 1;
        }
        if (skipBonus) return baseMoveValue;

        // Calculate bonus move value
        int bonusMoveValue = 0;
        if (player.hasTech("as") && FoWHelper.isTileAdjacentToAnAnomaly(game, game.getActiveSystem(), player)) {
            bonusMoveValue++;
        }
        if (player.hasAbility("slipstream") && (tileHasWormhole || movingFromHome)) {
            bonusMoveValue++;
        }
        if (!game.getStoredValue("crucibleBoost").isEmpty()) {
            bonusMoveValue += 1;
        }
        if (!game.getStoredValue("flankspeedBoost").isEmpty()) {
            bonusMoveValue += 1;
        }
        if (!game.getStoredValue("baldrickGDboost").isEmpty()) {
            bonusMoveValue += 1;
        }

        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        for (UnitHolder uhPlanet : activeSystem.getPlanetUnitHolders()) {
            if (player.getPlanets().contains(uhPlanet.getName())) {
                continue;
            }
            for (String attachment : uhPlanet.getTokenList()) {
                if (attachment.contains("sigma_weirdway")) {
                    bonusMoveValue -= 1;
                    break;
                }
            }
        }

        return baseMoveValue + bonusMoveValue;
    }
}