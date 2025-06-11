package ti4.service.tactical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import software.amazon.awssdk.utils.StringUtils;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperTacticalAction;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Space;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.SourceEmojis;
import ti4.service.fow.FOWPlusService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.movement.MovementExecutionService;
import ti4.service.movement.MovementExecutionService.MovementExecutionResult;
import ti4.service.planet.FlipTileService;
import ti4.service.regex.RegexService;

@UtilityClass
public class TacticalActionService {

    // shims for now
    public static List<Button> getLandingTroopsButtons(Game game, Player player, Tile tile) {
        return MovementExecutionService.getLandingTroopsButtons(game, player, tile);
    }

    public static List<Button> getLandingUnitsButtons(Game game, Player player, Tile tile) {
        return MovementExecutionService.getLandingUnitsButtons(game, player, tile);
    }

    // NOTE: THIS PROBABLY SHOULD NOT BE IN THE MOVEMENT EXECUTION SERVICE LOL
    public static List<Button> getBuildButtons(ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        return MovementExecutionService.getBuildButtons(game, player, tile);
    }

    public void reverseAllUnitMovement(ButtonInteractionEvent event, Game game, Player player) {
        Set<String> displacedKeys = new HashSet<>(game.getTacticalActionDisplacement().keySet());
        Map<String, Map<UnitKey, List<Integer>>> displaced = game.getTacticalActionDisplacement();

        boolean unkEncountered = false;
        Pattern rx = Pattern.compile(RegexHelper.posRegex(game) + "-" + RegexHelper.unitHolderRegex(game, "uh"));
        for (String uhKey : displacedKeys) {
            // Already cleared
            if (!displaced.containsKey(uhKey)) continue;
            if (uhKey.equals("unk")) {
                unkEncountered = true;
                continue;
            }

            // Need to clear tile
            RegexService.runMatcher(rx, uhKey, matcher -> {
                Tile tile = game.getTileByPosition(matcher.group("pos"));
                reverseTileUnitMovement(event, game, player, tile, "Move", true);
            }, event);
        }

        // Handle output
        MessageHelper.sendMessageToEventChannel(event, player.fogSafeEmoji() + " put all units back where they started.");
        if (unkEncountered)
            MessageHelper.sendMessageToEventChannel(event, player.fogSafeEmoji() + " some units could not be put back... You can put them back manually after movement.");
        TacticalActionOutputService.refreshButtonsAndMessageForChoosingTile(event, game, player);
    }

    public void reverseTileUnitMovement(ButtonInteractionEvent event, Game game, Player player, Tile tile, String moveOrRemove) {
        reverseTileUnitMovement(event, game, player, tile, moveOrRemove, false);
    }

    public void reverseTileUnitMovement(ButtonInteractionEvent event, Game game, Player player, Tile tile, String moveOrRemove, boolean skipOutput) {
        Map<String, Map<UnitKey, List<Integer>>> displaced = game.getTacticalActionDisplacement();
        for (UnitHolder uh : tile.getUnitHolders().values()) {
            String key = tile.getPosition() + "-" + uh.getName();
            if (!displaced.containsKey(key)) continue;

            Map<UnitKey, List<Integer>> unitsToRestore = displaced.remove(key);
            for (Entry<UnitKey, List<Integer>> unit : unitsToRestore.entrySet()) {
                uh.addUnitsWithStates(unit.getKey(), unit.getValue());
            }
        }
        if (skipOutput) return;
        TacticalActionOutputService.refreshButtonsAndMessageForTile(event, game, player, tile, moveOrRemove);
    }

    public void moveAllFromTile(ButtonInteractionEvent event, Game game, Player player, Tile tile, String moveOrRemove) {
        Map<String, Map<UnitKey, List<Integer>>> displaced = game.getTacticalActionDisplacement();

        List<UnitType> movableFromPlanets = new ArrayList<>(List.of(UnitType.Infantry, UnitType.Mech));
        for (UnitHolder uh : tile.getUnitHolders().values()) {
            String uhKey = tile.getPosition() + "-" + uh.getName();

            Map<UnitKey, List<Integer>> movement = displaced.getOrDefault(uhKey, new HashMap<>());
            for (UnitKey unitKey : new HashSet<>(uh.getUnitsByState().keySet())) {
                boolean movableFromPlanet = movableFromPlanets.contains(unitKey.getUnitType());
                if (!player.unitBelongsToPlayer(unitKey)) {
                    boolean belongsToUnlockedAlly = false;
                    UnitType uT = unitKey.getUnitType();
                    if (uT == UnitType.Infantry || uT == UnitType.Fighter || uT == UnitType.Mech) {
                        for (Player p2 : game.getRealPlayers()) {
                            if (p2.unitBelongsToPlayer(unitKey) && player.getAllianceMembers().contains(p2.getFaction()) && !tile.hasPlayerCC(p2)) {
                                belongsToUnlockedAlly = true;
                            }
                        }
                    }
                    if (!belongsToUnlockedAlly) {
                        continue;
                    }
                }
                if (uh instanceof Planet && !movableFromPlanet) continue;

                List<Integer> states = uh.removeUnit(unitKey, uh.getUnitCount(unitKey));
                movement.put(unitKey, states);
            }
            displaced.put(uhKey, movement);
        }

        TacticalActionOutputService.refreshButtonsAndMessageForTile(event, game, player, tile, moveOrRemove);
    }

    public void moveAllShipsFromTile(ButtonInteractionEvent event, Game game, Player player, Tile tile, String moveOrRemove) {
        Map<String, Map<UnitKey, List<Integer>>> displaced = game.getTacticalActionDisplacement();

        UnitHolder uh = tile.getSpaceUnitHolder();
        String uhKey = tile.getPosition() + "-" + uh.getName();

        Map<UnitKey, List<Integer>> movement = displaced.getOrDefault(uhKey, new HashMap<>());
        for (UnitKey unitKey : new HashSet<>(uh.getUnitsByState().keySet())) {
            if (!player.unitBelongsToPlayer(unitKey)) {
                boolean belongsToUnlockedAlly = false;
                UnitType uT = unitKey.getUnitType();
                if (uT == UnitType.Infantry || uT == UnitType.Fighter || uT == UnitType.Mech) {
                    for (Player p2 : game.getRealPlayers()) {
                        if (p2.unitBelongsToPlayer(unitKey) && player.getAllianceMembers().contains(p2.getFaction()) && !tile.hasPlayerCC(p2)) {
                            belongsToUnlockedAlly = true;
                        }
                    }
                }
                if (!belongsToUnlockedAlly) {
                    continue;
                }
            }

            List<Integer> states = uh.removeUnit(unitKey, uh.getUnitCount(unitKey));
            movement.put(unitKey, states);
        }
        displaced.put(uhKey, movement);

        TacticalActionOutputService.refreshButtonsAndMessageForTile(event, game, player, tile, moveOrRemove);
    }

    public void moveSingleUnit(ButtonInteractionEvent event, Game game, Player player, Tile tile, String planetName, UnitType type, int amt, UnitState state, String moveOrRemove, String color) {
        UnitHolder uh = planetName == null ? tile.getSpaceUnitHolder() : tile.getUnitHolderFromPlanet(planetName);
        if (uh == null) return;
        String uhKey = tile.getPosition() + "-" + uh.getName();

        // setup a fake unitholder to take advantage of that code
        Map<UnitKey, List<Integer>> displaced = game.getTacticalActionDisplacement().getOrDefault(uhKey, new HashMap<>());
        UnitHolder fakeUh = new Space("fake", null);
        String pColor = player.getColor();
        if (color != null && !color.isEmpty()) {
            pColor = color;
        }
        UnitKey unitKey = Units.getUnitKey(type, pColor);
        if (displaced.containsKey(unitKey))
            fakeUh.addUnitsWithStates(unitKey, displaced.get(unitKey));

        // Update object states
        List<Integer> statesMoved = uh.removeUnit(unitKey, amt, state);
        fakeUh.addUnitsWithStates(unitKey, statesMoved);
        displaced.put(unitKey, fakeUh.getUnitsByState().get(unitKey));
        game.getTacticalActionDisplacement().put(uhKey, displaced);

        // refresh buttons
        TacticalActionOutputService.refreshButtonsAndMessageForTile(event, game, player, tile, moveOrRemove);
    }

    public void reverseSingleUnit(ButtonInteractionEvent event, Game game, Player player, Tile tile, String planetName, UnitType type, int amt, UnitState state, String moveOrRemove, String color) {
        UnitHolder uh = planetName == null ? tile.getSpaceUnitHolder() : tile.getUnitHolderFromPlanet(planetName);
        if (uh == null) return;
        String uhKey = tile.getPosition() + "-" + uh.getName();

        // Get the data
        Map<UnitKey, List<Integer>> displaced = game.getTacticalActionDisplacement().getOrDefault(uhKey, new HashMap<>());
        String pColor = player.getColor();
        if (color != null && !color.isEmpty()) {
            pColor = color;
        }
        UnitKey unitKey = Units.getUnitKey(type, pColor);
        if (!displaced.containsKey(unitKey)) return;

        // setup a fake unitholder to take advantage of that code
        UnitHolder fakeUh = new Space("fake", null);
        fakeUh.addUnitsWithStates(unitKey, displaced.get(unitKey));

        // Put the unit back
        List<Integer> statesReverted = fakeUh.removeUnit(unitKey, amt, state);
        uh.addUnitsWithStates(unitKey, statesReverted);

        // Update the map
        List<Integer> newStates = fakeUh.getUnitsByState().get(unitKey);
        if (newStates == null) {
            displaced.remove(unitKey);
        } else {
            displaced.put(unitKey, newStates);
        }
        game.getTacticalActionDisplacement().put(uhKey, displaced);
        if (displaced.isEmpty()) {
            game.getTacticalActionDisplacement().remove(uhKey);
        }

        // refresh buttons
        TacticalActionOutputService.refreshButtonsAndMessageForTile(event, game, player, tile, moveOrRemove);
    }

    public void finishMovement(ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        MovementExecutionResult result = MovementExecutionService.executeMovement(event, game, player, tile);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), result.getMessage(), result.getButtons());

        List<Player> playersWithPds2 = ButtonHelper.tileHasPDS2Cover(player, game, tile.getPosition());
        if (!game.isL1Hero() && !playersWithPds2.isEmpty()) {
            ButtonHelperTacticalAction.tacticalActionSpaceCannonOffenceStep(game, player, playersWithPds2, tile);
        }
        StartCombatService.combatCheck(game, event, tile);
        ButtonHelper.deleteAllButtons(event);
    }

    public static List<Button> getTilesToMoveFrom(Player player, Game game, GenericInteractionCreateEvent event) {
        Tile active = game.getTileByPosition(game.getActiveSystem());
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Tile> tileEntry : new HashMap<>(game.getTileMap()).entrySet()) {
            boolean movedFrom = game.getTacticalActionDisplacement().keySet().stream().anyMatch(s -> s.startsWith(tileEntry.getValue().getPosition() + "-"));
            boolean hasUnits = FoWHelper.playerHasUnitsInSystem(player, tileEntry.getValue());
            for (Player p2 : game.getRealPlayers()) {
                if (player.getAllianceMembers().contains(p2.getFaction())) {
                    if (FoWHelper.playerHasUnitsInSystem(p2, tileEntry.getValue()) && !CommandCounterHelper.hasCC(event, p2.getColor(), tileEntry.getValue())) {
                        hasUnits = true;
                    }
                }
            }
            if ((movedFrom || hasUnits)
                && (!CommandCounterHelper.hasCC(event, player.getColor(), tileEntry.getValue())
                    || ButtonHelper.nomadHeroAndDomOrbCheck(player, game))) {
                Tile tile = tileEntry.getValue();
                buttons.add(Buttons.green(player.finChecker() + "tacticalMoveFrom_" + tileEntry.getKey(),
                    tile.getRepresentationForButtons(game, player), tile.getTileEmoji(player)));
            }
        }

        if (player.hasUnexhaustedLeader("saaragent")) {
            buttons.add(Buttons.gray("exhaustAgent_saaragent", "Use Saar Agent", FactionEmojis.Saar));
        }
        if (player.hasUnexhaustedLeader("belkoseaagent")) {
            buttons.add(Buttons.gray("exhaustAgent_belkoseaagent", "Use Belkosea Agent", FactionEmojis.belkosea));
        }
        if (player.hasUnexhaustedLeader("qhetagent") && active != null && !active.isHomeSystem(game) && FoWHelper.otherPlayersHaveShipsInSystem(player, active, game)) {
            buttons.add(Buttons.gray("exhaustAgent_qhetagent", "Use Qhet Agent", FactionEmojis.qhet));
        }
        if (player.hasRelic("dominusorb")) {
            buttons.add(Buttons.gray("dominusOrb", "Purge Dominus Orb", ExploreEmojis.Relic));
        }
        if (player.hasRelicReady("absol_luxarchtreatise")) {
            buttons.add(Buttons.gray("exhaustRelic_absol_luxarchtreatise", "Exhaust Luxarch Treatise", ExploreEmojis.Relic));
        }
        if (player.hasUnexhaustedLeader("ghostagent") && FoWHelper.doesTileHaveWHs(game, game.getActiveSystem())) {
            buttons.add(Buttons.gray("exhaustAgent_ghostagent", "Use Creuss Agent", FactionEmojis.Ghost));
        }
        if (player.hasTech("as") && FoWHelper.isTileAdjacentToAnAnomaly(game, game.getActiveSystem(), player)) {
            buttons.add(Buttons.gray("declareUse_Aetherstream", "Declare Aetherstream", FactionEmojis.Empyrean));
        }
        if (player.hasTech("dstoldb")) {
            buttons.add(Buttons.gray("declareUse_Emergency Modifications", "Emergency Modifications", FactionEmojis.toldar));
        }
        if (player.hasTech("dspharb")) {
            buttons.add(Buttons.gray("declareUse_Reality Field Impactor", "Declare Reality Field Impactor", FactionEmojis.pharadn));
        }
        if (player.hasTech("baldrick_gd")) {
            buttons.add(Buttons.gray("exhaustTech_baldrick_gd", "Exhaust Gravity Drive", SourceEmojis.IgnisAurora));
        }
        if (player.hasTechReady("dsuydab")) {
            buttons.add(Buttons.gray("exhaustTech_dsuydab", "Exhaust Navigation Relays", FactionEmojis.uydai));
        }
        if (player.hasTech("baldrick_lwd")) {
            buttons.add(Buttons.gray("exhaustTech_baldrick_lwd", "Exhaust Light/Wave Deflector", SourceEmojis.IgnisAurora));
        }
        if (player.getTechs().contains("dsgledb")) {
            buttons.add(Buttons.green(player.finChecker() + "declareUse_Lightning", "Declare Lightning Drives", FactionEmojis.gledge));
        }
        if (player.getTechs().contains("dsvadeb") && !player.getExhaustedTechs().contains("dsvadeb")) {
            buttons.add(Buttons.green(player.finChecker() + "exhaustTech_dsvadeb", "Exhaust Midas Turbine", FactionEmojis.vaden));
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "vayleriancommander")) {
            buttons.add(Buttons.gray("declareUse_Vaylerian Commander", "Use Vaylerian Commander", FactionEmojis.vaylerian));
        }
        if (player.hasLeaderUnlocked("vaylerianhero")) {
            buttons.add(Buttons.blue(player.finChecker() + "purgeVaylerianHero", "Use Vaylerian Hero", FactionEmojis.vaylerian));
        }
        if (active != null && !active.isHomeSystem(game) && player.hasLeaderUnlocked("uydaihero")) {
            buttons.add(Buttons.blue(player.finChecker() + "purgeUydaiHero", "Use Uydai Hero", FactionEmojis.vaylerian));
        }
        if (player.ownsUnit("ghost_mech") && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech") > 0) {
            buttons.add(Buttons.gray("creussMechStep1_", "Use Creuss Mech", FactionEmojis.Ghost));
        }
        if ((player.ownsUnit("nivyn_mech") && ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech).contains(active)) || player.ownsUnit("nivyn_mech2")) {
            buttons.add(Buttons.gray("nivynMechStep1_", "Use Nivyn Mech", FactionEmojis.nivyn));
        }
        if (active != null && player.hasTech("dslihb") && !active.isHomeSystem(game)) {
            buttons.add(Buttons.gray("exhaustTech_dslihb", "Exhaust Wraith Engine", FactionEmojis.lizho));
        }
        if (player.getPlanets().contains("eko") && !player.getExhaustedPlanetsAbilities().contains("eko")) {
            buttons.add(Buttons.gray(player.finChecker() + "planetAbilityExhaust_" + "eko", "Use Eko's Ability To Ignore Anomalies"));
        }

        buttons.add(Buttons.red(player.finChecker() + "concludeMove_" + game.getActiveSystem(), "Done moving"));
        buttons.add(Buttons.blue(player.finChecker() + "ChooseDifferentDestination", "Activate a different system"));
        buttons.add(Buttons.red(player.finChecker() + "resetTacticalMovement", "Reset all unit movement"));
        return buttons;
    }
}
