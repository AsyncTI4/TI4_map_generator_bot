package ti4.service.combat;

import static ti4.service.combat.UnitRollAbilities.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.tuple.Pair;
import ti4.contest.replay.core.CombatRollPayload.DieRollSource;
import ti4.contest.replay.core.CombatRollPayload.UnitRollType;
import ti4.contest.replay.core.CombatRollPayloadBuilder;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CombatMessageHelper;
import ti4.helpers.CombatModHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Helper;
import ti4.helpers.StringHelper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.UnitModel;
import ti4.service.breakthrough.ValefarZService;
import ti4.service.unit.HacanFlagshipService;

@UtilityClass
public class UnitRollExecution {
    public static CombatRollResult rollForUnitsWithResult(CombatContext combat) {
        CombatRollState state = new CombatRollState(combat);
        prepareRollModifiers(state);
        repairLetnevFlagshipAtStartOfCombatRound(state);
        repairEidolonMaximumAtStartOfCombatRound(state);
        clearInvalidSingleUnitBoostSelection(state);
        selectHighestValueSingleUnitForBoost(state);
        applyGravleashManeuversBoost(state);
        applySuperchargeBoost(state);
        mergeDivergingUnitModels(state);
        for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : state.workingUnits.entrySet()) {
            UnitRollState unit = prepareUnitRoll(state, entry);
            if (unit == null) continue;
            rollUnitGroups(unit);
        }
        recordRollStatistics(state);
        applyX89HitMultiplier(state);
        applyAbandonedConventionsOfWarMultiplier(state);
        applyRazeHitMultiplier(state);
        applyShardVolleyHit(state);
        applyShardSaturationHit(state);
        disableDoubleBoomEmojiOnWhiff(state);
        appendHitResults(state);
        appendX89HitMessage(state);
        offerHacanFlagshipRerolls(state);
        appendThalnosRerollOffer(state);
        appendShardVolleyMessage(state);
        appendShardSaturationMessage(state);
        appendDelayedRollNotesAndExtraMessages(state);
        clearMunitionsReserves(state);
        return buildCombatRollResult(state);
    }

    private static UnitRollState prepareUnitRoll(
            CombatRollState state, Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry) {
        UnitRollState unit = new UnitRollState(state, entry);
        calculateUnitCombatModifier(unit);
        calculateUnitExtraRolls(unit);
        consumeBestExtraRollModifiers(unit);
        applyCombatRoundProfile(unit);
        normalizeThalnosUnitDice(unit);
        applyExperimentalBattlestationLimit(unit);
        applyTnelisAgentLimit(unit);
        if (isDuplicateMetaliVoidUnit(unit)) return null;
        applyMetaliVoidLimit(unit);
        unit.rollGroups = buildUnitRollGroups(unit);
        return unit;
    }

    private static void calculateUnitCombatModifier(UnitRollState unit) {
        unit.preparedModifierToHit = CombatModHelper.getCombinedModifierForUnit(
                unit.unitModel,
                unit.preparedUnitCount,
                unit.context.mods,
                unit.context.player,
                unit.context.opponent,
                unit.context.game,
                unit.context.unitModels,
                unit.context.rollType,
                unit.context.tile,
                unit.perUnitHolder);
    }

    private static void calculateUnitExtraRolls(UnitRollState unit) {
        unit.availableExtraRolls = unit.context.extraRolls.stream()
                .filter(modifier -> !unit.context.consumedBestMods.contains(
                        modifier.getModifier().getAlias()))
                .collect(Collectors.toList());
        unit.extraRollsForUnit = CombatModHelper.getCombinedModifierForUnit(
                unit.unitModel,
                unit.preparedUnitCount,
                unit.availableExtraRolls,
                unit.context.player,
                unit.context.opponent,
                unit.context.game,
                unit.context.unitModels,
                unit.context.rollType,
                unit.context.tile,
                unit.perUnitHolder);
    }

    private static void consumeBestExtraRollModifiers(UnitRollState unit) {
        if (unit.extraRollsForUnit < 1) return;
        for (NamedCombatModifierModel modifier : unit.availableExtraRolls) {
            String scope = modifier.getModifier().getScope();
            boolean bestUnitScope = "_best_".equals(scope)
                    || "_bestCap_".equals(scope)
                    || (scope != null && scope.contains("_mostdice_"));
            if (bestUnitScope
                    && Boolean.TRUE.equals(modifier.getModifier()
                            .isInScopeForUnit(
                                    unit.unitModel,
                                    unit.context.unitModels,
                                    unit.context.rollType,
                                    unit.context.game,
                                    unit.context.player))) {
                unit.context.consumedBestMods.add(modifier.getModifier().getAlias());
            }
        }
    }

    private static void applyCombatRoundProfile(UnitRollState unit) {
        if (unit.context.rollType != CombatRollType.combatround) return;
        CombatStatsService.CombatRoundProfile profile = CombatStatsService.getCombatRoundProfile(
                true, unit.unitModel, unit.context.player, unit.context.tile, unit.context.opponent, false);
        unit.toHit = profile.hitsOn();
        unit.numRollsPerUnit = profile.diceCount();
    }

    private static void normalizeThalnosUnitDice(UnitRollState unit) {
        if (!unit.context.isThalnosReroll || (unit.numRollsPerUnit < 2 && unit.extraRollsForUnit < 1)) return;
        unit.extraRollsCount = true;
        unit.numRollsPerUnit = 1;
        unit.extraRollsForUnit = 0;
    }

    private static void applyExperimentalBattlestationLimit(UnitRollState unit) {
        if (unit.context.rollType != CombatRollType.SpaceCannonOffence
                || unit.numRollsPerUnit != 3
                || !"spacedock".equalsIgnoreCase(unit.unitModel.getBaseType())) return;
        unit.preparedUnitCount = 1;
        unit.context.game.setStoredValue("EBSFaction", "");
    }

    private static void applyTnelisAgentLimit(UnitRollState unit) {
        if (unit.context.rollType != CombatRollType.bombardment
                || unit.numRollsPerUnit < 2
                || !"destroyer".equalsIgnoreCase(unit.unitModel.getBaseType())) return;
        unit.preparedUnitCount = 1;
        unit.context.game.setStoredValue("TnelisAgentFaction", "");
    }

    private static boolean isDuplicateMetaliVoidUnit(UnitRollState unit) {
        return isMetaliVoidUnit(unit) && unit.context.metaliVoidCounted;
    }

    private static void applyMetaliVoidLimit(UnitRollState unit) {
        if (!isMetaliVoidUnit(unit)) return;
        unit.preparedUnitCount = 1;
        unit.context.metaliVoidCounted = true;
    }

    private static boolean isMetaliVoidUnit(UnitRollState unit) {
        return unit.context.rollType == CombatRollType.AFB
                && unit.unitModel.getAfbDieCount() == 0
                && unit.unitModel.getAfbDieCount(unit.context.player) == 3;
    }

    private static List<UnitRollGroup> buildUnitRollGroups(UnitRollState unit) {
        int totalDice = (unit.preparedUnitCount * unit.numRollsPerUnit) + unit.extraRollsForUnit;
        UnitRollGroup allUnits = new UnitRollGroup(
                unit.preparedUnitCount, unit.preparedModifierToHit, totalDice, unit.extraRollsForUnit);
        if (unit.context.rollType != CombatRollType.combatround || unit.context.isThalnosReroll) {
            return List.of(allUnits);
        }
        boolean hasBoost = unit.context.player.hasTech("tf-supercharge")
                || (unit.context.player.hasUnlockedBreakthrough("letnevbt")
                        && "space".equalsIgnoreCase(unit.context.combatOnHolder.getName()));
        String key = "highestValueSingleUnit" + unit.context.player.getFaction();
        if (!hasBoost || !unit.context.game.getStoredValue(key).equalsIgnoreCase(unit.unitModel.getAsyncId())) {
            return List.of(allUnits);
        }
        int selectedExtraRolls = Math.min(1, unit.extraRollsForUnit);
        int remainingExtraRolls = unit.extraRollsForUnit - selectedExtraRolls;
        UnitRollGroup selectedUnit = new UnitRollGroup(
                1,
                unit.preparedModifierToHit + unit.context.letnevBTBoost,
                unit.numRollsPerUnit + selectedExtraRolls,
                selectedExtraRolls);
        UnitRollGroup remainingUnits = new UnitRollGroup(
                unit.preparedUnitCount - 1,
                unit.preparedModifierToHit,
                ((unit.preparedUnitCount - 1) * unit.numRollsPerUnit) + remainingExtraRolls,
                remainingExtraRolls);
        unit.context.game.removeStoredValue(key);
        return remainingUnits.diceCount() > 0 ? List.of(selectedUnit, remainingUnits) : List.of(selectedUnit);
    }

    private static void rollUnitGroups(UnitRollState unit) {
        for (UnitRollGroup group : unit.rollGroups) {
            UnitGroupRollState roll = prepareUnitRollGroup(unit, group);
            resolveJolNarFlagshipExtraHits(unit, roll);
            resolveTeklarEliteExtraHits(unit, roll);
            resolveZephyrionCommanderExtraHits(unit, roll);
            resolveDragonFreedBombardment(unit, roll);
            resolveSigmaJolNarFlagshipDice(unit, roll);
            resolveSystemValorExtraHits(unit, roll);
            resolvePersonalValorExtraHits(unit, roll);
            resolveVadenFlagshipTradeGood(unit, roll);
            resolveUzeanWardogAbility(unit, roll);
            recordPrimaryRollTotals(unit, roll);
            resolveHacanFlagshipThalnosMisses(unit, roll);
            resolveFallOfKenaraThalnosMisses(unit, roll);
            trackThalnosDestroyTypes(unit);
            resolveGenericThalnosMisses(unit, roll);
            publishPrimaryUnitRoll(unit, roll);
            activateJusticerGraviton(unit, roll);
            resolveJolNarCommanderRerolls(unit, roll);
            resolveIronCommanderRerolls(unit, roll);
            offerGledgePds2Exploration(unit);
            offerGledgePdsExploration(unit, roll);
            resolveInitialKaltrimCommanderRerolls(unit, roll);
            List<Die> munitionsDice = resolveMunitionsReservesReroll(unit, roll);
            resolvePostMunitionsKaltrimCommanderRerolls(unit, roll, munitionsDice);
            resolveStrikeWingAlphaInfantryKills(unit, roll);
            rewardMercenaryCaptains(unit, roll);
            accumulateNearMisses(unit, roll);
        }
    }

    private static UnitGroupRollState prepareUnitRollGroup(UnitRollState unit, UnitRollGroup group) {
        UnitGroupRollState roll = new UnitGroupRollState();
        roll.unitCount = group.unitCount();
        roll.modifierToHit = group.modifierToHit();
        roll.displayedExtraRolls = group.extraRolls();
        roll.recordPrimaryRoll(unit, DiceHelper.rollDice(unit.toHit - roll.modifierToHit, group.diceCount()));
        return roll;
    }

    private static void recordPrimaryRollTotals(UnitRollState unit, UnitGroupRollState roll) {
        roll.commitPrimaryRollTotals(unit);
    }

    private static void publishPrimaryUnitRoll(UnitRollState unit, UnitGroupRollState roll) {
        String holderLabel = unit.context.divergingModels.contains(unit.unitModel.getId())
                        && unit.perUnitHolder instanceof Planet planet
                ? "on **" + Helper.getPlanetRepresentationNoResInf(planet.getName(), unit.context.game) + "**"
                : "";
        unit.context.resultBuilder.append(roll.renderAndRecordRoll(
                unit,
                roll.displayedExtraRolls,
                UnitRollType.PRIMARY,
                roll.activeDice,
                roll.hitRolls,
                DieRollSource.PRIMARY,
                holderLabel));
    }

    private static void accumulateNearMisses(UnitRollState unit, UnitGroupRollState roll) {
        unit.context.nearMisses += (int) IterableUtils.countMatches(roll.primaryDiceHistory, Die::eligibleForHeartPlus);
        unit.context.nearMisses += (int) IterableUtils.countMatches(roll.rerollDiceHistory, Die::eligibleForHeartPlus);
    }

    private static void clearInvalidSingleUnitBoostSelection(CombatRollState state) {
        String key = highestValueSingleUnitKey(state);
        String selectedUnit = state.game.getStoredValue(key);
        if (selectedUnit.isEmpty()) return;
        boolean selectedUnitIsPresent = state.workingUnits.keySet().stream()
                .anyMatch(unit -> unit.getLeft().getAsyncId().equalsIgnoreCase(selectedUnit));
        if (!selectedUnitIsPresent) state.game.removeStoredValue(key);
    }

    private static void selectHighestValueSingleUnitForBoost(CombatRollState state) {
        if (!hasSingleUnitRollBoost(state)
                || !state.game.getStoredValue(highestValueSingleUnitKey(state)).isEmpty()) {
            return;
        }
        int highestDiceCount = 0;
        for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : state.workingUnits.entrySet()) {
            UnitModel unitModel = entry.getKey().getLeft();
            int extraRollsForUnit = CombatModHelper.getCombinedModifierForUnit(
                    unitModel,
                    entry.getValue(),
                    state.extraRolls,
                    state.player,
                    state.opponent,
                    state.game,
                    state.unitModels,
                    CombatRollType.combatround,
                    state.tile,
                    entry.getKey().getRight());
            int diceCount = CombatStatsService.getCombatRoundProfile(
                            true, unitModel, state.player, state.tile, state.opponent, false)
                    .diceCount();
            int boostedDiceCount = diceCount + Math.min(1, extraRollsForUnit);
            if (boostedDiceCount <= highestDiceCount) continue;
            highestDiceCount = boostedDiceCount;
            state.game.setStoredValue(highestValueSingleUnitKey(state), unitModel.getAsyncId());
        }
    }

    private static boolean hasSingleUnitRollBoost(CombatRollState state) {
        return state.rollType == CombatRollType.combatround
                && (state.player.hasTech("tf-supercharge")
                        || (state.player.hasUnlockedBreakthrough("letnevbt")
                                && Constants.SPACE.equalsIgnoreCase(state.combatOnHolder.getName())));
    }

    private static void applyGravleashManeuversBoost(CombatRollState state) {
        if (state.rollType != CombatRollType.combatround
                || state.player.hasTech("tf-supercharge")
                || !state.player.hasUnlockedBreakthrough("letnevbt")
                || !Constants.SPACE.equalsIgnoreCase(state.combatOnHolder.getName())) return;
        int boost = (int) state.workingUnits.keySet().stream()
                .map(Pair::getLeft)
                .filter(UnitModel::getIsShip)
                .count();
        state.letnevBTBoost = boost;
        state.resultBuilder
                .append("Applied +")
                .append(boost)
                .append(" to the rolls of 1 unit with _Gravleash Maneuvers_.\n");
        addSingleUnitRollBoostNote(state, "letnevbt", boost);
    }

    private static void applySuperchargeBoost(CombatRollState state) {
        if (state.rollType != CombatRollType.combatround || !state.player.hasTech("tf-supercharge")) return;
        state.letnevBTBoost = 2;
        state.resultBuilder.append("Applied +2 to the rolls of 1 unit with _Supercharge_.\n");
        addSingleUnitRollBoostNote(state, "tf-supercharge", 2);
    }

    private static void addSingleUnitRollBoostNote(CombatRollState state, String sourceId, int modifier) {
        state.payloadBuilder.recordSingleUnitModifier(
                sourceId, state.game.getStoredValue(highestValueSingleUnitKey(state)), modifier);
    }

    private static String highestValueSingleUnitKey(CombatRollState state) {
        return "highestValueSingleUnit" + state.player.getFaction();
    }

    private static void recordRollStatistics(CombatRollState state) {
        state.player.setActualHits(state.player.getActualHits() + state.totalHits);
        if (state.chanceOfAllHits <= 2.0 && state.totalHits == state.maximumHits) {
            state.game.setStoredValue("surprisingDiceRoll", "hits");
        } else if (state.chanceOfAllMiss <= 2.0 && state.totalHits == 0) {
            state.game.setStoredValue("surprisingDiceRoll", "miss");
        } else {
            state.game.setStoredValue("surprisingDiceRoll", "none");
        }
        state.whiff = state.maximumHits > 0 && state.totalHits == 0;
        state.slam = state.maximumHits > 0 && state.totalHits == state.maximumHits;
    }

    private static void applyX89HitMultiplier(CombatRollState state) {
        if (!state.usesX89c4) return;
        multiplyHits(state, 2);
        state.useDoubleBoomEmoji = true;
    }

    private static void applyAbandonedConventionsOfWarMultiplier(CombatRollState state) {
        if (!state.game.isConventionsOfWarAbandonedMode() || state.rollType != CombatRollType.bombardment) return;
        multiplyHits(state, 3);
    }

    private static void applyRazeHitMultiplier(CombatRollState state) {
        if (!state.player.hasStoredValue("RazeFaction") || state.rollType != CombatRollType.bombardment) return;
        multiplyHits(state, 2);
        state.useDoubleBoomEmoji = true;
    }

    private static void applyShardVolleyHit(CombatRollState state) {
        if (state.totalHits < 1 || state.rollType != CombatRollType.bombardment || !state.player.hasTech("dszelir")) {
            return;
        }
        state.totalHits++;
        state.maximumHits++;
    }

    private static void applyShardSaturationHit(CombatRollState state) {
        if (state.totalHits < 1
                || state.rollType == CombatRollType.combatround
                || !state.player.hasTech("tf-shardsaturation")) return;
        state.totalHits++;
        state.maximumHits++;
    }

    private static void disableDoubleBoomEmojiOnWhiff(CombatRollState state) {
        if (state.totalHits < 1) state.useDoubleBoomEmoji = false;
    }

    private static void multiplyHits(CombatRollState state, int multiplier) {
        state.totalHits *= multiplier;
        state.maximumHits *= multiplier;
    }

    private static void appendHitResults(CombatRollState state) {
        state.resultBuilder.append(CombatMessageHelper.displayHitResults(state.totalHits, state.useDoubleBoomEmoji));
    }

    private static void appendX89HitMessage(CombatRollState state) {
        if (state.totalHits < 1 || !state.usesX89c4) return;
        state.resultBuilder
                .append("\n")
                .append(state.player.getFactionEmoji())
                .append(" produced ")
                .append(StringHelper.pluralize(state.totalHits / 2, "additional hit"))
                .append(" using ")
                .append(Mapper.getTech("x89c4").getNameRepresentation())
                .append(".");
    }

    private static void offerHacanFlagshipRerolls(CombatRollState state) {
        if ((!state.hacanFlagship && !state.tkHacanWarsun) || state.nearMisses < 1 || state.isThalnosReroll) return;
        HacanFlagshipService.startHacanFlagshipNormal(
                state.event, state.game, state.player, state.tile, state.nearMisses);
    }

    private static void appendThalnosRerollOffer(CombatRollState state) {
        if (!state.player.hasRelic("thalnos")
                || state.rollType != CombatRollType.combatround
                || state.totalMisses < 1
                || state.isThalnosReroll) return;
        state.resultBuilder
                .append("\n")
                .append(state.player.getFactionEmoji())
                .append(" You have _The Crown of Thalnos_ and may reroll ")
                .append(state.totalMisses == 1 ? "the miss" : "misses")
                .append(", adding +1, at the risk of your ")
                .append(state.totalMisses == 1 ? "troop's life" : "troops' lives")
                .append(".");
        state.payloadBuilder.recordRerollAvailable(state.totalMisses, state.player.getFactionEmoji());
    }

    private static void appendShardVolleyMessage(CombatRollState state) {
        if (state.totalHits > 0 && state.rollType == CombatRollType.bombardment && state.player.hasTech("dszelir")) {
            state.resultBuilder
                    .append("\n")
                    .append(state.player.getFactionEmoji())
                    .append(" You have _Shard Volley_ and thus produced an additional hit to the ones rolled above.");
        }
    }

    private static void appendShardSaturationMessage(CombatRollState state) {
        if (state.totalHits > 0
                && state.rollType != CombatRollType.combatround
                && state.player.hasTech("tf-shardsaturation")) {
            state.resultBuilder
                    .append("\n")
                    .append(state.player.getFactionEmoji())
                    .append(
                            " You have _Shard Saturation_ and thus produced an additional hit to the ones rolled above.");
        }
    }

    private static void appendDelayedRollNotesAndExtraMessages(CombatRollState state) {
        state.payloadBuilder.flushDelayedNotes();
        if (!state.extra.isEmpty()) state.resultBuilder.append("\n\n").append(state.extra);
    }

    private static void clearMunitionsReserves(CombatRollState state) {
        if (state.game.getStoredValue("munitionsReserves").equalsIgnoreCase(state.player.getFaction())
                && state.rollType == CombatRollType.combatround) state.game.setStoredValue("munitionsReserves", "");
    }

    private static CombatRollResult buildCombatRollResult(CombatRollState state) {
        var payload = state.payloadBuilder.build(state.totalHits, state.totalMisses, state.maximumHits);
        return new CombatRollResult(
                CombatRollStatus.COMPLETED,
                state.resultBuilder.toString(),
                state.totalHits,
                state.whiff,
                state.slam,
                payload);
    }

    private static void prepareRollModifiers(CombatRollState state) {
        PreparedModifiers prepared = prepareAndDisplayModifiers(state);
        state.mods = prepared.rollModifiers();
        state.resultBuilder.append(prepared.display());
    }

    private static void mergeDivergingUnitModels(CombatRollState state) {
        UnitMergeResult merged = mergeAndDetectDivergence(state);
        state.workingUnits = merged.units();
        state.divergingModels = merged.divergingModels();
    }

    private static PreparedModifiers prepareAndDisplayModifiers(CombatRollState state) {
        Set<NamedCombatModifierModel> rollModifierSet = new HashSet<>(state.combatModifiers);
        rollModifierSet.addAll(state.temporaryModifiers);
        List<NamedCombatModifierModel> rollModifiers = new ArrayList<>(rollModifierSet);

        Set<NamedCombatModifierModel> displayedModifierSet = new HashSet<>(rollModifiers);
        displayedModifierSet.addAll(state.extraRolls);
        List<NamedCombatModifierModel> displayedModifiers = new ArrayList<>(displayedModifierSet);
        Map<UnitModel, Integer> playerUnitsFlat = new HashMap<>();
        state.workingUnits.forEach((unit, count) -> playerUnitsFlat.merge(unit.getLeft(), count, Integer::sum));
        String display =
                CombatMessageHelper.displayModifiers("With modifiers: \n", playerUnitsFlat, displayedModifiers);
        state.payloadBuilder.addModifierDisplays(
                displayedModifiers,
                playerUnitsFlat,
                state.player,
                state.opponent,
                state.game,
                state.rollType,
                state.tile,
                state.combatOnHolder);
        return new PreparedModifiers(rollModifiers, display);
    }

    private static void repairLetnevFlagshipAtStartOfCombatRound(CombatRollState state) {
        if (state.rollType != CombatRollType.combatround
                || !ButtonHelper.doesPlayerHaveFSHere("letnev_flagship", state.player, state.tile)
                || !Constants.SPACE.equalsIgnoreCase(state.combatOnHolder.getName())
                || state.combatOnHolder.getDamagedUnitCount(UnitType.Flagship, state.player.getColorID()) < 1) return;
        state.resultBuilder.insert(0, "Repaired the Arc Secundus at start of this combat round with its ability.\n");
        state.payloadBuilder.recordUnitRepair("letnev_flagship");
        state.tile.removeUnitDamage(
                state.combatOnHolder.getName(),
                Mapper.getUnitKey(AliasHandler.resolveUnit("fs"), state.player.getColorID()),
                1);
    }

    private static void repairEidolonMaximumAtStartOfCombatRound(CombatRollState state) {
        if (state.rollType != CombatRollType.combatround
                || !state.player.ownsUnit("naaz_voltron")
                || !Constants.SPACE.equalsIgnoreCase(state.combatOnHolder.getName())
                || state.combatOnHolder.getDamagedUnitCount(UnitType.Mech, state.player.getColorID()) < 1) return;
        state.resultBuilder.insert(0, "The Eidolon Maximum self-repaired at the start of this combat round.\n");
        state.payloadBuilder.recordUnitRepair("naaz_voltron");
        state.tile.removeUnitDamage(
                state.combatOnHolder.getName(),
                Mapper.getUnitKey(AliasHandler.resolveUnit("mf"), state.player.getColorID()),
                1);
    }

    static UnitMergeResult mergeAndDetectDivergence(CombatRollState state) {
        IdentityHashMap<Pair<UnitModel, UnitHolder>, Integer> countByIdentity = new IdentityHashMap<>();
        state.workingUnits.forEach(countByIdentity::put);
        Map<String, List<Pair<UnitModel, UnitHolder>>> modelKeys = new LinkedHashMap<>();
        for (Pair<UnitModel, UnitHolder> key : countByIdentity.keySet()) {
            modelKeys
                    .computeIfAbsent(key.getLeft().getId(), k -> new ArrayList<>())
                    .add(key);
        }
        Set<String> divergingModels = new HashSet<>();
        Map<Pair<UnitModel, UnitHolder>, Integer> merged = new LinkedHashMap<>();
        for (Map.Entry<String, List<Pair<UnitModel, UnitHolder>>> modelEntry : modelKeys.entrySet()) {
            List<Pair<UnitModel, UnitHolder>> keys = modelEntry.getValue();
            if (keys.size() == 1) {
                Pair<UnitModel, UnitHolder> key = keys.get(0);
                merged.put(key, countByIdentity.get(key));
                continue;
            }
            IdentityHashMap<Pair<UnitModel, UnitHolder>, UnitMergeProfile> profiles = new IdentityHashMap<>();
            for (Pair<UnitModel, UnitHolder> key : keys) {
                UnitModel model = key.getLeft();
                UnitHolder holder = key.getRight();
                int toHit = model.getCombatDieHitsOnForAbility(state.rollType, state.player);
                if (state.rollType == CombatRollType.combatround) {
                    toHit = CombatStatsService.getCombatRoundProfile(
                                    true, model, state.player, state.tile, state.opponent, false)
                            .hitsOn();
                }
                int modifier = CombatModHelper.getCombinedModifierForUnit(
                        model,
                        countByIdentity.get(key),
                        state.mods,
                        state.player,
                        state.opponent,
                        state.game,
                        state.unitModels,
                        state.rollType,
                        state.tile,
                        holder);
                int extraDicePerUnit = CombatModHelper.getCombinedModifierForUnit(
                        model,
                        1,
                        state.extraRolls,
                        state.player,
                        state.opponent,
                        state.game,
                        state.unitModels,
                        state.rollType,
                        state.tile,
                        holder);
                int dicePerUnit = model.getCombatDieCountForAbility(state.rollType, state.player);
                profiles.put(key, new UnitMergeProfile(toHit - modifier, dicePerUnit, extraDicePerUnit));
            }
            Set<UnitMergeProfile> distinctProfiles = new HashSet<>(profiles.values());
            if (distinctProfiles.size() > 1) {
                divergingModels.add(modelEntry.getKey());
                keys.sort(Comparator.comparing(profiles::get));
                for (Pair<UnitModel, UnitHolder> key : keys) merged.put(key, countByIdentity.get(key));
            } else {
                int totalCount = keys.stream().mapToInt(countByIdentity::get).sum();
                merged.put(keys.get(0), totalCount);
            }
        }
        return new UnitMergeResult(merged, divergingModels);
    }

    static class UnitRollState {
        final CombatRollState context;
        final UnitModel unitModel;
        final UnitHolder perUnitHolder;
        int toHit;
        int preparedUnitCount;
        int preparedModifierToHit;
        int numRollsPerUnit;
        int extraRollsForUnit;
        boolean extraRollsCount;
        List<UnitRollGroup> rollGroups;
        List<NamedCombatModifierModel> availableExtraRolls = List.of();

        UnitRollState(CombatRollState context, Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry) {
            this.context = context;
            this.unitModel = entry.getKey().getLeft();
            this.perUnitHolder = entry.getKey().getRight();
            this.toHit = unitModel.getCombatDieHitsOnForAbility(context.rollType, context.player);
            this.preparedUnitCount = entry.getValue();
            this.preparedModifierToHit = 0;
            this.numRollsPerUnit = unitModel.getCombatDieCountForAbility(context.rollType, context.player);
            this.extraRollsForUnit = 0;
            this.extraRollsCount = false;
            this.rollGroups = List.of();
        }
    }

    static class UnitGroupRollState {
        int displayedExtraRolls;
        private final List<Die> primaryDiceHistory = new ArrayList<>();
        private List<Die> activeDice = List.of();
        private int hitRolls;
        int unitCount;
        int modifierToHit;
        private int diceCount;
        int multiplier = 1;
        private final List<Die> rerollDiceHistory = new ArrayList<>();
        private int numMisses;
        private int maximumHits;
        private double allHitsProbability;

        void recordPrimaryRoll(UnitRollState unit, List<Die> dice) {
            primaryDiceHistory.clear();
            primaryDiceHistory.addAll(dice);
            activeDice = new ArrayList<>(dice);
            rerollDiceHistory.clear();
            diceCount = dice.size();
            multiplier = unit.context.usesX89c4 ? 2 : 1;
            hitRolls = DiceHelper.countSuccesses(activeDice);
            numMisses = 0;
            unit.context.player.setExpectedHitsTimes10(
                    unit.context.player.getExpectedHitsTimes10() + (diceCount * (11 - unit.toHit + modifierToHit)));
            allHitsProbability = Math.pow((11 - unit.toHit + modifierToHit) / 10.0, diceCount);
            unit.context.chanceOfAllMiss *= Math.pow((unit.toHit - modifierToHit - 1) / 10.0, diceCount);
            maximumHits = diceCount;
        }

        void commitPrimaryRollTotals(UnitRollState unit) {
            unit.context.maximumHits += maximumHits;
            unit.context.chanceOfAllHits *= allHitsProbability;
            numMisses = diceCount - DiceHelper.countSuccesses(activeDice);
            unit.context.totalMisses += numMisses;
            unit.context.totalHits += hitRolls;
        }

        void recordBonusHitOutcome(int hits, int hitCapacity, double allHitsProbabilityFactor) {
            hitRolls += hits;
            maximumHits += hitCapacity;
            allHitsProbability *= allHitsProbabilityFactor;
        }

        void recordAdditionalDice(UnitRollState unit, List<Die> dice, int resolvedHits) {
            unit.context.player.setExpectedHitsTimes10(
                    unit.context.player.getExpectedHitsTimes10() + (dice.size() * (11 - unit.toHit + modifierToHit)));
            allHitsProbability *= Math.pow((11 - unit.toHit + modifierToHit) / 10.0, dice.size());
            unit.context.chanceOfAllMiss *= Math.pow((unit.toHit - modifierToHit - 1) / 10.0, dice.size());
            maximumHits += dice.size();
            diceCount += dice.size();
            activeDice.addAll(dice);
            primaryDiceHistory.addAll(dice);
            hitRolls += resolvedHits;
        }

        RerollResult rollMisses(UnitRollState unit, int diceCount) {
            RerollResult result = rollReplacementDice(unit, diceCount);
            unit.context.chanceOfAllHits *= Math.pow((11 - unit.toHit + modifierToHit) / 10.0, diceCount);
            unit.context.chanceOfAllMiss *= Math.pow((unit.toHit - modifierToHit - 1) / 10.0, diceCount);
            return result;
        }

        RerollResult rollReplacementDice(UnitRollState unit, int diceCount) {
            List<Die> dice = DiceHelper.rollDice(unit.toHit - modifierToHit, diceCount);
            rerollDiceHistory.addAll(dice);
            unit.context.player.setExpectedHitsTimes10(
                    unit.context.player.getExpectedHitsTimes10() + (diceCount * (11 - unit.toHit + modifierToHit)));
            return new RerollResult(dice, DiceHelper.countSuccesses(dice));
        }

        void replaceMissesWith(UnitRollState unit, RerollResult reroll, int resolvedHits) {
            List<Die> replacedMisses = activeDice.stream()
                    .filter(Predicate.not(Die::isSuccess))
                    .limit(reroll.dice().size())
                    .toList();
            replaceDiceWith(unit, replacedMisses, reroll, resolvedHits);
        }

        void replaceDiceWith(UnitRollState unit, List<Die> replacedDice, RerollResult reroll, int resolvedHits) {
            if (replacedDice.size() != reroll.dice().size() || !activeDice.containsAll(replacedDice)) {
                throw new IllegalArgumentException("Replacement dice must correspond one-for-one with active dice");
            }
            int replacedHits = DiceHelper.countSuccesses(replacedDice);
            int replacedMisses = replacedDice.size() - replacedHits;
            int rerollMisses = reroll.dice().size() - reroll.hits();

            activeDice.removeAll(replacedDice);
            activeDice.addAll(reroll.dice());
            hitRolls += resolvedHits - replacedHits;
            numMisses += rerollMisses - replacedMisses;
            unit.context.totalHits += resolvedHits - replacedHits;
            unit.context.totalMisses += rerollMisses - replacedMisses;
        }

        void replaceHitsWith(UnitRollState unit, RerollResult reroll, int resolvedHits) {
            int successfulDice = DiceHelper.countSuccesses(activeDice);
            if (successfulDice != reroll.dice().size()) {
                throw new IllegalArgumentException("Hit rerolls must replace every successful active die");
            }
            int replacedHits = hitRolls;
            activeDice.removeIf(Die::isSuccess);
            activeDice.addAll(reroll.dice());
            hitRolls = resolvedHits;
            numMisses += reroll.dice().size() - reroll.hits();
            unit.context.totalHits += resolvedHits - replacedHits;
            unit.context.totalMisses += reroll.dice().size() - reroll.hits();
        }

        List<Die> activeDice() {
            return List.copyOf(activeDice);
        }

        List<Die> primaryDiceHistory() {
            return List.copyOf(primaryDiceHistory);
        }

        List<Die> rerollDiceHistory() {
            return List.copyOf(rerollDiceHistory);
        }

        int currentHits() {
            return hitRolls;
        }

        int currentMisses() {
            return numMisses;
        }

        int currentDiceCount() {
            return diceCount;
        }

        int currentMaximumHits() {
            return maximumHits;
        }

        double currentAllHitsProbability() {
            return allHitsProbability;
        }

        void excludeMissesFromDestruction(int misses) {
            numMisses = Math.max(0, numMisses - misses);
        }

        void ignoreMissesForDestruction() {
            numMisses = 0;
        }

        void destroyMissedUnits(UnitRollState unit) {
            UnitRollAbilities.destroyThalnosMissedUnits(unit, this);
        }

        String renderAndRecordRoll(
                UnitRollState unit,
                int displayedExtraRolls,
                UnitRollType payloadRollType,
                List<Die> dice,
                int hits,
                DieRollSource source) {
            return renderAndRecordRoll(unit, displayedExtraRolls, payloadRollType, dice, hits, source, "");
        }

        String renderAndRecordRoll(
                UnitRollState unit,
                int displayedExtraRolls,
                UnitRollType payloadRollType,
                List<Die> dice,
                int hits,
                DieRollSource source,
                String holderLabel) {
            unit.context.payloadBuilder.addUnitRoll(
                    unit.unitModel,
                    unit.toHit,
                    modifierToHit,
                    unitCount,
                    unit.numRollsPerUnit,
                    displayedExtraRolls,
                    payloadRollType,
                    dice,
                    hits,
                    source);
            return CombatMessageHelper.displayUnitRoll(
                    unit.unitModel,
                    unit.toHit,
                    modifierToHit,
                    unitCount,
                    unit.numRollsPerUnit,
                    displayedExtraRolls,
                    dice,
                    hits,
                    holderLabel);
        }
    }

    record UnitRollGroup(int unitCount, int modifierToHit, int diceCount, int extraRolls) {}

    record RerollResult(List<Die> dice, int hits) {}

    static class CombatRollState {
        Map<Pair<UnitModel, UnitHolder>, Integer> workingUnits;
        final List<NamedCombatModifierModel> extraRolls;
        final List<NamedCombatModifierModel> combatModifiers;
        final List<NamedCombatModifierModel> temporaryModifiers;
        final Player player;
        final Player opponent;
        final Game game;
        final CombatRollType rollType;
        final GenericInteractionCreateEvent event;
        final Tile tile;
        final UnitHolder combatOnHolder;
        List<NamedCombatModifierModel> mods;
        final List<UnitModel> unitModels;
        Set<String> divergingModels = Set.of();
        final Set<String> consumedBestMods = new HashSet<>();
        final CombatRollPayloadBuilder payloadBuilder = new CombatRollPayloadBuilder();
        StringBuilder resultBuilder = new StringBuilder();
        int letnevBTBoost;
        final boolean hacanFlagship;
        final boolean tkHacanWarsun;
        final List<Button> hacanFsButtons = new ArrayList<>();
        final List<UnitType> hacanFsThalnosDestroyTypes = new ArrayList<>();
        final boolean isThalnosReroll;
        final UnitHolder space;
        final StringBuilder extra = new StringBuilder();
        final boolean usesX89c4;
        int totalHits;
        int totalMisses;
        int maximumHits;
        int nearMisses;
        double chanceOfAllHits = Math.nextDown(100.0);
        double chanceOfAllMiss = Math.nextDown(100.0);
        boolean whiff;
        boolean slam;
        boolean useDoubleBoomEmoji;
        boolean metaliVoidCounted;

        CombatRollState(CombatContext combat) {
            workingUnits = combat.playerUnits;
            extraRolls = combat.modifiers.extraRolls();
            combatModifiers = combat.modifiers.combatModifiers();
            temporaryModifiers = combat.modifiers.temporaryModifiers();
            player = combat.player;
            opponent = combat.opponent;
            game = combat.game;
            rollType = combat.rollType;
            event = combat.event;
            tile = combat.tile;
            combatOnHolder = combat.combatOnHolder;
            unitModels = workingUnits.keySet().stream().map(Pair::getLeft).collect(Collectors.toList());
            List<UnitType> unitTypes =
                    unitModels.stream().map(UnitModel::getUnitType).toList();
            hacanFlagship = unitModels.stream().anyMatch(unit -> "hacan_flagship".equals(unit.getId()))
                    || (unitTypes.contains(UnitType.Flagship)
                            && ValefarZService.hasCopiedFlagshipAbility(game, player, "hacan_flagship"));
            tkHacanWarsun = player.hasUnit("tk-fallofkenara") && unitTypes.contains(UnitType.Warsun);
            isThalnosReroll = "true".equalsIgnoreCase(game.getStoredValue("thalnosPlusOne"));
            space = tile.getUnitHolders().get(Constants.SPACE);
            usesX89c4 = player.hasTech("x89c4")
                    && (rollType == CombatRollType.combatround || rollType == CombatRollType.bombardment)
                    && (!Constants.SPACE.equalsIgnoreCase(combatOnHolder.getName())
                            || rollType == CombatRollType.bombardment);
        }
    }

    record PreparedModifiers(List<NamedCombatModifierModel> rollModifiers, String display) {}

    private record UnitMergeProfile(int effectiveThreshold, int dicePerUnit, int extraDicePerUnit)
            implements Comparable<UnitMergeProfile> {
        @Override
        public int compareTo(UnitMergeProfile other) {
            int thresholdComparison = Integer.compare(effectiveThreshold, other.effectiveThreshold);
            if (thresholdComparison != 0) return thresholdComparison;
            int diceComparison = Integer.compare(dicePerUnit, other.dicePerUnit);
            if (diceComparison != 0) return diceComparison;
            return Integer.compare(extraDicePerUnit, other.extraDicePerUnit);
        }
    }
}
