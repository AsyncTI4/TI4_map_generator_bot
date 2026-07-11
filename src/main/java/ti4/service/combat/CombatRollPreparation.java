package ti4.service.combat;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron.IronFactionTechsHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersAbilitiesHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersUnitsHandler;
import ti4.game.Planet;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CombatModHelper;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.image.TileHelper;
import ti4.message.MessageHelper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.TileModel;
import ti4.model.UnitModel;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@UtilityClass
class CombatRollPreparation {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static void validateCombatRollLocation(CombatRollPipelineState state) {
        state.combatOnHolder = state.tile.getUnitHolders().get(state.unitHolderName);
        if (state.combatOnHolder == null) {
            MessageHelper.sendMessageToChannel(
                    state.event.getMessageChannel(),
                    "Cannot find the planet " + state.unitHolderName + " on tile " + state.tile.getPosition() + ".");
            state.stop(CombatRollStatus.INVALID_LOCATION);
            return;
        }
        if (state.rollType == CombatRollType.SpaceCannonDefence && !(state.combatOnHolder instanceof Planet)) {
            MessageHelper.sendMessageToChannel(
                    state.event.getMessageChannel(),
                    "Planet needs to be specified to fire SPACE CANNON against ships on tile "
                            + state.tile.getPosition() + ".");
            state.stop(CombatRollStatus.INVALID_LOCATION);
        }
    }

    static void prepareCombatRoll(CombatRollPipelineState state) {
        state.playerUnits = CombatUnitResolver.getUnitsInCombatByHolder(
                state.tile, state.combatOnHolder, state.player, state.event, state.rollType, state.game);
        addSpecialUnitsForRoll(state);
        prepareBombardmentContext(state);
        removeUnitsDisabledByArticlesOfWar(state);
        if (reportAndCheckNoUnits(state)) {
            state.stop(CombatRollStatus.NO_ELIGIBLE_UNITS);
            return;
        }
        resolveCombatRollOpponent(state);
        if (isEmpSpaceCannonBlocked(state)) {
            state.stop(CombatRollStatus.BLOCKED);
            return;
        }
        Map<UnitModel, Integer> opponentUnits = CombatUnitResolver.getUnitsInCombat(
                state.tile, state.combatOnHolder, state.opponent, state.event, state.rollType, state.game);
        state.modifiers = collectRollModifiers(state, opponentUnits);
    }

    static void resolveCombatRollOpponent(CombatRollPipelineState state) {
        if (state.opponent != null) {
            return;
        }
        List<UnitHolder> combatHolders = new ArrayList<>(List.of(state.combatOnHolder));
        if (state.rollType == CombatRollType.SpaceCannonDefence
                || state.rollType == CombatRollType.SpaceCannonOffence) {
            combatHolders.add(state.tile.getUnitHolders().get(Constants.SPACE));
        }
        state.opponent = CombatUnitResolver.getOpponent(state.player, combatHolders, state.game);
        if (state.opponent == null) state.opponent = state.player;
    }

    static boolean isEmpSpaceCannonBlocked(CombatRollPipelineState state) {
        return state.game.getRealPlayers().stream().anyMatch(realPlayer -> realPlayer.hasUnit("netrunners_flagship"))
                && NetrunnersUnitsHandler.resolveEmpSpaceCannonBlock(
                        state.event, state.game, state.player, state.tile, state.rollType);
    }

    static void addSpecialUnitsForRoll(CombatRollPipelineState state) {
        if (state.rollType == CombatRollType.AFB && state.player.hasRelic("metalivoidarmaments")) {
            state.playerUnits.put(
                    new ImmutablePair<>(CombatUnitResolver.getMetaliAFBUnit(state.player), state.combatOnHolder), 1);
        }
        if (state.rollType == CombatRollType.AFB && state.player.hasTech("tf-projectionofpow")) {
            state.playerUnits.put(
                    new ImmutablePair<>(CombatUnitResolver.getProjectionUnit(state.player, true), state.combatOnHolder),
                    1);
        }
        if (state.player.hasAbility("projection_of_power") && isAdjacentToPlayersSpaceDock(state)) {
            state.playerUnits.put(
                    new ImmutablePair<>(
                            CombatUnitResolver.getProjectionUnit(state.player, false), state.combatOnHolder),
                    1);
        }
        if (state.rollType == CombatRollType.combatround && state.player.hasActiveBreakthrough("zelianbt")) {
            addEligiblePlanetCombatUnits(state, false);
        }
        if (state.rollType == CombatRollType.combatround
                && state.player.hasTech("tf-hostileplanetoids")
                && Constants.SPACE.equalsIgnoreCase(state.unitHolderName)) {
            addEligiblePlanetCombatUnits(state, true);
        }
    }

    static void removeUnitsDisabledByArticlesOfWar(CombatRollPipelineState state) {
        if (!ButtonHelper.isLawInPlay(state.game, "articles_war")) {
            return;
        }
        state.playerUnits = removeDisabledUnit(
                state.playerUnits,
                "naaz_mech_space",
                state.event,
                "Skipping Z-Grav Eidolon (Naaz-Rokha mech) combat rolls due to _Articles of War_.");
        if (state.rollType == CombatRollType.SpaceCannonDefence
                || state.rollType == CombatRollType.SpaceCannonOffence) {
            state.playerUnits = removeDisabledUnit(
                    state.playerUnits,
                    "xxcha_mech",
                    state.event,
                    "Skipping Indomitus (Xxcha mech) SPACE CANNON rolls due to _Articles of War_.");
        }
        if (state.rollType == CombatRollType.bombardment) {
            state.playerUnits = removeDisabledUnit(
                    state.playerUnits,
                    "l1z1x_mech",
                    state.event,
                    "Skipping Annihilator (L1Z1X mech) BOMBARDMENT rolls due to _Articles of War_.");
        }
    }

    static boolean reportAndCheckNoUnits(CombatRollPipelineState state) {
        if (!state.playerUnits.isEmpty()) {
            return false;
        }
        String location = Constants.SPACE.equalsIgnoreCase(state.unitHolderName)
                ? state.unitHolderName
                : Helper.getPlanetRepresentation(state.unitHolderName, state.game);
        MessageHelper.sendMessageToChannel(
                state.event.getMessageChannel(),
                "There are no units in " + location + " on tile " + state.tile.getPosition() + " for player "
                        + state.player.getColor() + " " + state.player.getFactionEmoji() + " for the combat roll type "
                        + state.rollType + "\nPing bothelper if this seems to be in error.");
        return true;
    }

    static void prepareBombardmentContext(CombatRollPipelineState state) {
        state.bombardPlanet = state.game.getStoredValue("bombardmentTarget" + state.player.getFaction());
        if (state.rollType != CombatRollType.bombardment || state.bombardPlanet.isEmpty()) {
            state.bombardPlanet = "";
            return;
        }
        if (state.player.hasUnit("ashen_flagship")) {
            AshenUnitHandler.prepareFlagshipBombardmentContext(state.game, state.player, state.bombardPlanet);
        }
        limitUnitsToBombardmentAssignments(state);
        state.opponent = state.game.getRealPlayersNNeutral().stream()
                .filter(candidate -> candidate.getPlanets().contains(state.bombardPlanet))
                .findFirst()
                .orElse(null);
    }

    static void limitUnitsToBombardmentAssignments(CombatRollPipelineState state) {
        List<BombardmentAssignment> assignedUnits = MAPPER.readValue(
                state.game.getStoredValue("assignedBombardment" + state.player.getFaction()),
                new TypeReference<List<BombardmentAssignment>>() {});
        Map<String, Integer> remainingAssignedByAsyncId = new HashMap<>();
        for (BombardmentAssignment assignedUnit : assignedUnits) {
            if (assignedUnit.planet().equals(state.bombardPlanet) && assignedUnit.sourceId() != null) {
                remainingAssignedByAsyncId.merge(assignedUnit.sourceId(), 1, Integer::sum);
            }
        }
        for (Pair<UnitModel, UnitHolder> unit : new ArrayList<>(state.playerUnits.keySet())) {
            String asyncId = unit.getLeft().getAsyncId();
            int available = remainingAssignedByAsyncId.getOrDefault(asyncId, 0);
            int count = Math.min(available, state.playerUnits.get(unit));
            if (count > 0) {
                remainingAssignedByAsyncId.put(asyncId, available - count);
                state.playerUnits.put(unit, count);
            } else {
                state.playerUnits.remove(unit);
            }
        }
    }

    static Map<Pair<UnitModel, UnitHolder>, Integer> removeDisabledUnit(
            Map<Pair<UnitModel, UnitHolder>, Integer> units,
            String alias,
            GenericInteractionCreateEvent event,
            String message) {
        if (units.keySet().stream()
                .noneMatch(pair -> alias.equals(pair.getLeft().getAlias()))) {
            return units;
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        return units.entrySet().stream()
                .filter(entry -> !alias.equals(entry.getKey().getLeft().getAlias()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    static CombatRollModifiers collectRollModifiers(
            CombatRollPipelineState state, Map<UnitModel, Integer> opponentUnits) {
        TileModel tileModel = TileHelper.getTileById(state.tile.getTileID());
        Map<UnitModel, Integer> playerUnitsFlat = CombatUnitResolver.flattenUnitMap(state.playerUnits);
        List<NamedCombatModifierModel> combatModifiers = CombatModHelper.getModifiers(
                state.player,
                state.opponent,
                playerUnitsFlat,
                opponentUnits,
                tileModel,
                state.game,
                state.rollType,
                state.combatOnHolder,
                Constants.COMBAT_MODIFIERS);
        List<NamedCombatModifierModel> extraRolls = CombatModHelper.getModifiers(
                state.player,
                state.opponent,
                playerUnitsFlat,
                opponentUnits,
                tileModel,
                state.game,
                state.rollType,
                state.combatOnHolder,
                Constants.COMBAT_EXTRA_ROLLS);
        removeUnassignedBombardmentExtraRolls(state, extraRolls);

        CombatTempModHelper.ensureValidTempMods(state.player, tileModel, state.combatOnHolder);
        CombatTempModHelper.initializeNewTempMods(state.player, tileModel, state.combatOnHolder);
        List<NamedCombatModifierModel> temporaryModifiers =
                new ArrayList<>(CombatTempModHelper.buildCurrentRoundTempNamedModifiers(
                        state.player, tileModel, state.combatOnHolder, false, state.rollType));
        temporaryModifiers.addAll(CombatTempModHelper.buildCurrentRoundTempNamedModifiers(
                state.opponent, tileModel, state.combatOnHolder, true, state.rollType));
        if (state.game.getRealPlayers().stream().anyMatch(realPlayer -> realPlayer.hasAbility("control_network"))) {
            temporaryModifiers.addAll(NetrunnersAbilitiesHandler.getPendingControlNetworkSpaceCannonModifier(
                    state.game, state.player, state.tile, state.combatOnHolder, state.rollType));
        }
        if (state.player.hasTech("beironats")) {
            extraRolls.addAll(IronFactionTechsHandler.getAdvancedTargetingSystemsExtraRollModifier(
                    state.game, state.player, state.opponent, state.tile, state.combatOnHolder, state.rollType));
        }
        return new CombatRollModifiers(combatModifiers, extraRolls, temporaryModifiers);
    }

    static void removeUnassignedBombardmentExtraRolls(
            CombatRollPipelineState state, List<NamedCombatModifierModel> extraRolls) {
        String storedAssignments = state.game.getStoredValue("assignedBombardment" + state.player.getFaction());
        if (storedAssignments.isEmpty() || state.rollType != CombatRollType.bombardment) {
            return;
        }
        List<BombardmentAssignment> assignments =
                MAPPER.readValue(storedAssignments, new TypeReference<List<BombardmentAssignment>>() {});
        extraRolls.removeIf(modifier -> isUnassignedBombardmentExtraRoll(modifier, assignments, state.bombardPlanet));
    }

    static boolean isUnassignedBombardmentExtraRoll(
            NamedCombatModifierModel modifier, List<BombardmentAssignment> assignments, String bombardPlanet) {
        String alias = modifier.getModifier().getAlias();
        if (alias == null) {
            return false;
        }
        List<BombardmentAssignment> planetAssignments = assignments.stream()
                .filter(a -> a.planet().equals(bombardPlanet))
                .toList();
        return switch (alias.toLowerCase()) {
            case "plus1_roll_plasmascoring" ->
                planetAssignments.stream().noneMatch(a -> "plasmascoring".equals(a.sourceId()));
            case "plus1_roll_argent_commander_bombard" ->
                planetAssignments.stream().noneMatch(a -> "argentcommander".equals(a.sourceId()));
            case "roll_1_for_galvanize_bombard" ->
                planetAssignments.stream().noneMatch(BombardmentAssignment::galvanized);
            default -> false;
        };
    }

    static boolean isAdjacentToPlayersSpaceDock(CombatRollPipelineState state) {
        for (Tile spaceDockTile :
                ButtonHelper.getTilesOfPlayersSpecificUnits(state.game, state.player, UnitType.Spacedock)) {
            if (FoWHelper.getAdjacentTiles(state.game, spaceDockTile.getPosition(), state.player, false, true)
                    .contains(state.tile.getPosition())) {
                return true;
            }
        }
        return false;
    }

    static void addEligiblePlanetCombatUnits(CombatRollPipelineState state, boolean spaceOnly) {
        for (UnitHolder planet : state.tile.getPlanetUnitHolders()) {
            boolean eligibleHolder = spaceOnly
                    ? Constants.SPACE.equalsIgnoreCase(state.unitHolderName)
                    : Constants.SPACE.equalsIgnoreCase(state.unitHolderName)
                            || planet.getName().equalsIgnoreCase(state.unitHolderName);
            if (state.player.getPlanetsAllianceMode().contains(planet.getName()) && eligibleHolder) {
                int resources = Helper.getPlanetResources(planet.getName(), state.game);
                state.playerUnits.put(
                        new ImmutablePair<>(
                                CombatUnitResolver.getZelianPlanetUnit(
                                        state.player, Helper.getPlanetName(planet.getName()), 10 - resources),
                                state.combatOnHolder),
                        1);
            }
        }
    }

    // This roll was made from fow private channel and not from a combat thread

}
