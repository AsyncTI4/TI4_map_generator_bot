package ti4.service.combat;

import static org.apache.commons.lang3.StringUtils.*;
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
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.tuple.Pair;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNotePlacement;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNoteType;
import ti4.contest.replay.core.CombatRollPayload.DieRollSource;
import ti4.contest.replay.core.CombatRollPayload.UnitRollType;
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
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.UnitModel;
import ti4.service.breakthrough.ValefarZService;
import ti4.service.emoji.MiscEmojis;
import ti4.service.unit.HacanFlagshipService;

@UtilityClass
public class UnitRollExecution {
    public static CombatRollResult rollForUnitsWithResult(CombatRollPipelineState combat) {
        UnitRollPipelineState state = new UnitRollPipelineState(combat);
        prepareRollModifiers(state);
        repairUnitsAtStartOfCombatRound(state);
        prepareSingleUnitRollBoost(state);
        mergeDivergingUnitModels(state);
        for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : state.playerUnits.entrySet()) {
            UnitRollState unit = prepareUnitRoll(state, entry);
            if (unit == null) continue;
            rollUnitGroups(unit);
        }
        recordRollStatistics(state);
        applyHitMultipliers(state);
        appendHitResults(state);
        appendX89HitMessage(state);
        offerHacanFlagshipRerolls(state);
        appendThalnosRerollOffer(state);
        appendAdditionalHitMessages(state);
        appendDelayedRollNotesAndExtraMessages(state);
        clearMunitionsReserves(state);
        return buildCombatRollResult(state);
    }

    private static UnitRollState prepareUnitRoll(
            UnitRollPipelineState state, Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry) {
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
                unit.pipeline.mods,
                unit.pipeline.player,
                unit.pipeline.opponent,
                unit.pipeline.game,
                unit.pipeline.playerUnitsList,
                unit.pipeline.rollType,
                unit.pipeline.activeSystem,
                unit.perUnitHolder);
    }

    private static void calculateUnitExtraRolls(UnitRollState unit) {
        unit.availableExtraRolls = unit.pipeline.extraRolls.stream()
                .filter(modifier -> !unit.pipeline.consumedBestMods.contains(
                        modifier.getModifier().getAlias()))
                .collect(Collectors.toList());
        unit.extraRollsForUnit = CombatModHelper.getCombinedModifierForUnit(
                unit.unitModel,
                unit.preparedUnitCount,
                unit.availableExtraRolls,
                unit.pipeline.player,
                unit.pipeline.opponent,
                unit.pipeline.game,
                unit.pipeline.playerUnitsList,
                unit.pipeline.rollType,
                unit.pipeline.activeSystem,
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
                                    unit.pipeline.playerUnitsList,
                                    unit.pipeline.rollType,
                                    unit.pipeline.game,
                                    unit.pipeline.player))) {
                unit.pipeline.consumedBestMods.add(modifier.getModifier().getAlias());
            }
        }
    }

    private static void applyCombatRoundProfile(UnitRollState unit) {
        if (unit.pipeline.rollType != CombatRollType.combatround) return;
        CombatStatsService.CombatRoundProfile profile = CombatStatsService.getCombatRoundProfile(
                true, unit.unitModel, unit.pipeline.player, unit.pipeline.activeSystem, unit.pipeline.opponent, false);
        unit.toHit = profile.hitsOn();
        unit.numRollsPerUnit = profile.diceCount();
    }

    private static void normalizeThalnosUnitDice(UnitRollState unit) {
        if (!unit.pipeline.isThalnosReroll || (unit.numRollsPerUnit < 2 && unit.extraRollsForUnit < 1)) return;
        unit.extraRollsCount = true;
        unit.numRollsPerUnit = 1;
        unit.extraRollsForUnit = 0;
    }

    private static void applyExperimentalBattlestationLimit(UnitRollState unit) {
        if (unit.pipeline.rollType != CombatRollType.SpaceCannonOffence
                || unit.numRollsPerUnit != 3
                || !"spacedock".equalsIgnoreCase(unit.unitModel.getBaseType())) return;
        unit.preparedUnitCount = 1;
        unit.pipeline.game.setStoredValue("EBSFaction", "");
    }

    private static void applyTnelisAgentLimit(UnitRollState unit) {
        if (unit.pipeline.rollType != CombatRollType.bombardment
                || unit.numRollsPerUnit < 2
                || !"destroyer".equalsIgnoreCase(unit.unitModel.getBaseType())) return;
        unit.preparedUnitCount = 1;
        unit.pipeline.game.setStoredValue("TnelisAgentFaction", "");
    }

    private static boolean isDuplicateMetaliVoidUnit(UnitRollState unit) {
        return isMetaliVoidUnit(unit) && unit.pipeline.metaliVoidCounted;
    }

    private static void applyMetaliVoidLimit(UnitRollState unit) {
        if (!isMetaliVoidUnit(unit)) return;
        unit.preparedUnitCount = 1;
        unit.pipeline.metaliVoidCounted = true;
    }

    private static boolean isMetaliVoidUnit(UnitRollState unit) {
        return unit.pipeline.rollType == CombatRollType.AFB
                && unit.unitModel.getAfbDieCount() == 0
                && unit.unitModel.getAfbDieCount(unit.pipeline.player) == 3;
    }

    private static List<UnitRollGroup> buildUnitRollGroups(UnitRollState unit) {
        int totalDice = (unit.preparedUnitCount * unit.numRollsPerUnit) + unit.extraRollsForUnit;
        UnitRollGroup allUnits = new UnitRollGroup(
                unit.preparedUnitCount, unit.preparedModifierToHit, totalDice, unit.extraRollsForUnit);
        if (unit.pipeline.rollType != CombatRollType.combatround || unit.pipeline.isThalnosReroll) {
            return List.of(allUnits);
        }
        boolean hasBoost = unit.pipeline.player.hasTech("tf-supercharge")
                || (unit.pipeline.player.hasUnlockedBreakthrough("letnevbt")
                        && "space".equalsIgnoreCase(unit.pipeline.unitHolder.getName()));
        String key = "highestValueSingleUnit" + unit.pipeline.player.getFaction();
        if (!hasBoost || !unit.pipeline.game.getStoredValue(key).equalsIgnoreCase(unit.unitModel.getAsyncId())) {
            return List.of(allUnits);
        }
        int selectedExtraRolls = Math.min(1, unit.extraRollsForUnit);
        int remainingExtraRolls = unit.extraRollsForUnit - selectedExtraRolls;
        UnitRollGroup selectedUnit = new UnitRollGroup(
                1,
                unit.preparedModifierToHit + unit.pipeline.letnevBTBoost,
                unit.numRollsPerUnit + selectedExtraRolls,
                selectedExtraRolls);
        UnitRollGroup remainingUnits = new UnitRollGroup(
                unit.preparedUnitCount - 1,
                unit.preparedModifierToHit,
                ((unit.preparedUnitCount - 1) * unit.numRollsPerUnit) + remainingExtraRolls,
                remainingExtraRolls);
        unit.pipeline.game.removeStoredValue(key);
        return remainingUnits.diceCount() > 0 ? List.of(selectedUnit, remainingUnits) : List.of(selectedUnit);
    }

    private static void rollUnitGroups(UnitRollState unit) {
        for (UnitRollGroup group : unit.rollGroups) {
            prepareUnitRollGroup(unit, group);
            resolveJolNarFlagshipExtraHits(unit);
            resolveTeklarEliteExtraHits(unit);
            resolveZephyrionCommanderExtraHits(unit);
            resolveDragonFreedBombardment(unit);
            resolveSigmaJolNarFlagshipDice(unit);
            resolveValorExtraHits(unit);
            resolveVadenFlagshipTradeGood(unit);
            resolveUzeanWardogAbility(unit);
            recordPrimaryRollTotals(unit);
            resolveThalnosMisses(unit);
            publishPrimaryUnitRoll(unit);
            activateJusticerGraviton(unit);
            resolveJolNarCommanderRerolls(unit);
            resolveIronCommanderRerolls(unit);
            offerGledgePdsExploration(unit);
            resolveInitialKaltrimCommanderRerolls(unit);
            List<Die> munitionsDice = resolveMunitionsReservesReroll(unit);
            resolvePostMunitionsKaltrimCommanderRerolls(unit, munitionsDice);
            resolveStrikeWingAlphaInfantryKills(unit);
            rewardMercenaryCaptains(unit);
            accumulateNearMisses(unit);
        }
    }

    private static void prepareUnitRollGroup(UnitRollState unit, UnitRollGroup group) {
        unit.numOfUnit = group.unitCount();
        unit.modifierToHit = group.modifierToHit();
        unit.displayedExtraRolls = group.extraRolls();
        unit.recordPrimaryRoll(DiceHelper.rollDice(unit.toHit - unit.modifierToHit, group.diceCount()));
    }

    private static void recordPrimaryRollTotals(UnitRollState unit) {
        unit.commitPrimaryRollTotals();
    }

    private static void publishPrimaryUnitRoll(UnitRollState unit) {
        String holderLabel = unit.pipeline.divergingModels.contains(unit.unitModel.getId())
                        && unit.perUnitHolder instanceof Planet planet
                ? "on **" + Helper.getPlanetRepresentationNoResInf(planet.getName(), unit.pipeline.game) + "**"
                : "";
        unit.pipeline.resultBuilder.append(unit.renderAndRecordRoll(
                unit.displayedExtraRolls,
                UnitRollType.PRIMARY,
                unit.activeDice,
                unit.hitRolls,
                DieRollSource.PRIMARY,
                holderLabel));
    }

    private static void accumulateNearMisses(UnitRollState unit) {
        unit.pipeline.nearMisses +=
                (int) IterableUtils.countMatches(unit.primaryDiceHistory, Die::eligibleForHeartPlus);
        unit.pipeline.nearMisses += (int) IterableUtils.countMatches(unit.rerollDiceHistory, Die::eligibleForHeartPlus);
    }

    private static void prepareSingleUnitRollBoost(UnitRollPipelineState state) {
        int boost = 0;
        String highestValueSingleUnitKey = "highestValueSingleUnit" + state.player.getFaction();
        String storedHighestValueUnit = state.game.getStoredValue(highestValueSingleUnitKey);
        boolean unitUndecided = storedHighestValueUnit.isEmpty()
                || state.playerUnits.keySet().stream()
                        .noneMatch(k -> k.getLeft().getAsyncId().equalsIgnoreCase(storedHighestValueUnit));
        if (!storedHighestValueUnit.isEmpty() && unitUndecided) {
            // A manual Gravleash/Supercharge choice (chooseGravleash_) that isn't part of this combat
            // round - wrong tile, or the chosen unit has since died/retreated - would otherwise block
            // auto-pick forever, since this flag never becomes true again once set.
            state.game.removeStoredValue(highestValueSingleUnitKey);
        }
        if (state.rollType == CombatRollType.combatround
                && (state.player.hasTech("tf-supercharge")
                        || (state.player.hasUnlockedBreakthrough("letnevbt")
                                && "space".equalsIgnoreCase(state.unitHolder.getName())))) {
            int max = 0;
            for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : state.playerUnits.entrySet()) {
                UnitModel unitModel = entry.getKey().getLeft();
                UnitHolder perUnitHolder = entry.getKey().getRight();
                int numOfUnit = entry.getValue();
                int extraRollsForUnit = CombatModHelper.getCombinedModifierForUnit(
                        unitModel,
                        numOfUnit,
                        state.extraRolls,
                        state.player,
                        state.opponent,
                        state.game,
                        state.playerUnitsList,
                        CombatRollType.combatround,
                        state.activeSystem,
                        perUnitHolder);
                unitModel.getCombatDieCountForAbility(CombatRollType.combatround, state.player);
                int numRollsPerUnit;
                CombatStatsService.CombatRoundProfile combatRoundProfile = CombatStatsService.getCombatRoundProfile(
                        true, unitModel, state.player, state.activeSystem, state.opponent, false);
                numRollsPerUnit = combatRoundProfile.diceCount();
                if (numRollsPerUnit + Math.min(1, extraRollsForUnit) > max && unitUndecided) {
                    max = numRollsPerUnit + Math.min(1, extraRollsForUnit);
                    state.game.setStoredValue(
                            "highestValueSingleUnit" + state.player.getFaction(), unitModel.getAsyncId());
                }
                if (state.player.hasUnlockedBreakthrough("letnevbt") && unitModel.getIsShip()) {
                    boost++;
                }
            }
            if (state.player.hasTech("tf-supercharge")) {
                state.resultBuilder.append("Applied +2 to the rolls of 1 unit with _Supercharge_.\n");
                state.payloadBuilder.addNote(new CombatRollPayload.CombatRollNote(
                        CombatRollNoteType.SINGLE_UNIT_ROLL_MOD_APPLIED,
                        CombatRollNotePlacement.BEFORE_UNIT_ROLLS,
                        "tf-supercharge",
                        state.game.getStoredValue("highestValueSingleUnit" + state.player.getFaction()),
                        1,
                        Map.of("modifier", "2")));
                boost = 2;
            } else {
                state.resultBuilder
                        .append("Applied +")
                        .append(boost)
                        .append(" to the rolls of 1 unit with _Gravleash Maneuvers_.\n");
                state.payloadBuilder.addNote(new CombatRollPayload.CombatRollNote(
                        CombatRollNoteType.SINGLE_UNIT_ROLL_MOD_APPLIED,
                        CombatRollNotePlacement.BEFORE_UNIT_ROLLS,
                        "letnevbt",
                        state.game.getStoredValue("highestValueSingleUnit" + state.player.getFaction()),
                        1,
                        Map.of("modifier", Integer.toString(boost))));
            }
        }

        state.letnevBTBoost = boost;
    }

    private static void recordRollStatistics(UnitRollPipelineState state) {
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

    private static void applyHitMultipliers(UnitRollPipelineState state) {
        if (state.usesX89c4) multiplyHits(state, 2);
        if (state.game.isConventionsOfWarAbandonedMode() && state.rollType == CombatRollType.bombardment) {
            multiplyHits(state, 3);
        }
        state.useDoubleBoomEmoji = state.usesX89c4;
        if (state.player.hasStoredValue("RazeFaction") && state.rollType == CombatRollType.bombardment) {
            state.useDoubleBoomEmoji = true;
            multiplyHits(state, 2);
        }
        if (state.totalHits < 1) state.useDoubleBoomEmoji = false;
        if (state.totalHits > 0 && state.rollType == CombatRollType.bombardment && state.player.hasTech("dszelir")) {
            state.totalHits++;
            state.maximumHits++;
        }
        if (state.totalHits > 0
                && state.rollType != CombatRollType.combatround
                && state.player.hasTech("tf-shardsaturation")) {
            state.totalHits++;
            state.maximumHits++;
        }
    }

    private static void multiplyHits(UnitRollPipelineState state, int multiplier) {
        state.totalHits *= multiplier;
        state.maximumHits *= multiplier;
    }

    private static void appendHitResults(UnitRollPipelineState state) {
        state.resultBuilder.append(CombatMessageHelper.displayHitResults(state.totalHits, state.useDoubleBoomEmoji));
    }

    private static void appendX89HitMessage(UnitRollPipelineState state) {
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

    private static void offerHacanFlagshipRerolls(UnitRollPipelineState state) {
        if ((!state.hacanFlagship && !state.tkHacanWarsun) || state.nearMisses < 1 || state.isThalnosReroll) return;
        HacanFlagshipService.startHacanFlagshipNormal(
                state.event, state.game, state.player, state.activeSystem, state.nearMisses);
    }

    private static void appendThalnosRerollOffer(UnitRollPipelineState state) {
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
        state.payloadBuilder.addNote(new CombatRollPayload.CombatRollNote(
                CombatRollNoteType.REROLL_AVAILABLE,
                CombatRollNotePlacement.AFTER_TOTAL,
                "thalnos",
                null,
                state.totalMisses,
                Map.of("actorEmoji", state.player.getFactionEmoji())));
    }

    private static void appendAdditionalHitMessages(UnitRollPipelineState state) {
        if (state.totalHits > 0 && state.rollType == CombatRollType.bombardment && state.player.hasTech("dszelir")) {
            state.resultBuilder
                    .append("\n")
                    .append(state.player.getFactionEmoji())
                    .append(" You have _Shard Volley_ and thus produced an additional hit to the ones rolled above.");
        }
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

    private static void appendDelayedRollNotesAndExtraMessages(UnitRollPipelineState state) {
        state.delayedAfterTotalNotes.forEach(state.payloadBuilder::addNote);
        if (!state.extra.isEmpty()) state.resultBuilder.append("\n\n").append(state.extra);
    }

    private static void clearMunitionsReserves(UnitRollPipelineState state) {
        if (state.game.getStoredValue("munitionsReserves").equalsIgnoreCase(state.player.getFaction())
                && state.rollType == CombatRollType.combatround) state.game.setStoredValue("munitionsReserves", "");
    }

    private static CombatRollResult buildCombatRollResult(UnitRollPipelineState state) {
        CombatRollPayload payload = state.payloadBuilder.build(state.totalHits, state.totalMisses, state.maximumHits);
        return new CombatRollResult(
                CombatRollStatus.COMPLETED,
                state.resultBuilder.toString(),
                state.totalHits,
                state.whiff,
                state.slam,
                payload);
    }

    private static void prepareRollModifiers(UnitRollPipelineState state) {
        PreparedModifiers prepared = prepareAndDisplayModifiers(state);
        state.mods = prepared.rollModifiers();
        state.resultBuilder.append(prepared.display());
    }

    private static void repairUnitsAtStartOfCombatRound(UnitRollPipelineState state) {
        String repairs = buildStartOfCombatRoundRepairs(state);
        state.resultBuilder.insert(0, repairs);
    }

    private static void mergeDivergingUnitModels(UnitRollPipelineState state) {
        UnitMergeResult merged = mergeAndDetectDivergence(state);
        state.playerUnits = merged.units();
        state.divergingModels = merged.divergingModels();
    }

    private static PreparedModifiers prepareAndDisplayModifiers(UnitRollPipelineState state) {
        Set<NamedCombatModifierModel> rollModifierSet = new HashSet<>(state.autoMods);
        rollModifierSet.addAll(state.tempMods);
        List<NamedCombatModifierModel> rollModifiers = new ArrayList<>(rollModifierSet);

        Set<NamedCombatModifierModel> displayedModifierSet = new HashSet<>(rollModifiers);
        displayedModifierSet.addAll(state.extraRolls);
        List<NamedCombatModifierModel> displayedModifiers = new ArrayList<>(displayedModifierSet);
        Map<UnitModel, Integer> playerUnitsFlat = new HashMap<>();
        state.playerUnits.forEach((unit, count) -> playerUnitsFlat.merge(unit.getLeft(), count, Integer::sum));
        String display =
                CombatMessageHelper.displayModifiers("With modifiers: \n", playerUnitsFlat, displayedModifiers);
        state.payloadBuilder.addModifierDisplays(
                displayedModifiers,
                playerUnitsFlat,
                state.player,
                state.opponent,
                state.game,
                state.rollType,
                state.activeSystem,
                state.unitHolder);
        return new PreparedModifiers(rollModifiers, display);
    }

    private static String buildStartOfCombatRoundRepairs(UnitRollPipelineState state) {
        String repairs = "";
        if (state.rollType == CombatRollType.combatround
                && ButtonHelper.doesPlayerHaveFSHere("letnev_flagship", state.player, state.activeSystem)
                && Constants.SPACE.equalsIgnoreCase(state.unitHolder.getName())
                && state.unitHolder.getDamagedUnitCount(UnitType.Flagship, state.player.getColorID()) > 0) {
            repairs = "Repaired the Arc Secundus at start of this combat round with its ability.\n" + repairs;
            addUnitRepairedNote(state.payloadBuilder, "letnev_flagship");
            state.activeSystem.removeUnitDamage(
                    state.unitHolder.getName(),
                    Mapper.getUnitKey(AliasHandler.resolveUnit("fs"), state.player.getColorID()),
                    1);
        }
        if (state.rollType == CombatRollType.combatround
                && state.player.ownsUnit("naaz_voltron")
                && Constants.SPACE.equalsIgnoreCase(state.unitHolder.getName())
                && state.unitHolder.getDamagedUnitCount(UnitType.Mech, state.player.getColorID()) > 0) {
            repairs = "The Eidolon Maximum self-repaired at the start of this combat round.\n" + repairs;
            addUnitRepairedNote(state.payloadBuilder, "naaz_voltron");
            state.activeSystem.removeUnitDamage(
                    state.unitHolder.getName(),
                    Mapper.getUnitKey(AliasHandler.resolveUnit("mf"), state.player.getColorID()),
                    1);
        }
        return repairs;
    }

    private static void addUnitRepairedNote(RollPayloadBuilder payloadBuilder, String unitId) {
        payloadBuilder.addNote(new CombatRollPayload.CombatRollNote(
                CombatRollNoteType.UNIT_REPAIRED,
                CombatRollNotePlacement.BEFORE_MODIFIERS,
                unitId,
                unitId,
                1,
                Map.of("timing", "START_OF_COMBAT_ROUND")));
    }

    /** Builds a button with ID {@code FFCC_hacanFlagshipThalnos_<unittype>_X} where {@code X} is the number of units that can score a hit given a +1 */

    /** Builds a button with ID {@code FFCC_tkHacanWsThalnos_<unittype>_X,X,X,X,X,X,X,X,X,X} where _Xᵢ_ is the number of units that can rolled a result of _i_*/
    static CombatRollPayload.RollHeader buildRollHeader(CombatRollPipelineState state, String combatSummary) {
        String combatDisplayName = substringBetween(combatSummary, "rolls for ", " " + MiscEmojis.RollDice + " :");
        if (combatDisplayName == null) {
            combatDisplayName = substringBetween(combatSummary, "rolls for ", " :");
        }
        Integer combatRound = null;
        if (state.rollType == CombatRollType.combatround) {
            String combatName = "combatRoundTracker" + state.player.getFaction() + state.tile.getPosition()
                    + state.combatOnHolder.getName();
            if (!state.game.getStoredValue(combatName).isBlank()) {
                combatRound = Integer.parseInt(state.game.getStoredValue(combatName));
            }
        }
        boolean thalnosReroll = "true".equalsIgnoreCase(state.game.getStoredValue("thalnosPlusOne"));
        return new CombatRollPayload.RollHeader(
                state.player.getFaction(),
                state.player.getColor(),
                state.player.getFactionEmoji(),
                state.opponent == null ? null : state.opponent.getFaction(),
                state.opponent == null ? null : state.opponent.getColor(),
                state.tile.getPosition(),
                state.tile.getTileID(),
                state.combatOnHolder.getName(),
                combatDisplayName,
                state.rollType,
                combatRound,
                thalnosReroll,
                state.game.isFowMode());
    }

    static class UnitRollState {
        // Unit-lifetime fields are populated once by prepareUnitRoll.
        final UnitRollPipelineState pipeline;
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

        // Group-lifetime fields are reset by prepareUnitRollGroup before every separately rolled unit group.
        int displayedExtraRolls;
        final List<Die> primaryDiceHistory = new ArrayList<>();
        List<Die> activeDice;
        int hitRolls;
        int numOfUnit;
        int modifierToHit;
        int numRolls;
        int multiplier;
        final List<Die> rerollDiceHistory = new ArrayList<>();
        int numMisses;
        int groupMaximumHits;
        double groupAllHitsProbability;

        UnitRollState(UnitRollPipelineState pipeline, Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry) {
            this.pipeline = pipeline;
            this.unitModel = entry.getKey().getLeft();
            this.perUnitHolder = entry.getKey().getRight();
            this.activeDice = List.of();
            this.hitRolls = 0;
            this.toHit = unitModel.getCombatDieHitsOnForAbility(pipeline.rollType, pipeline.player);
            this.preparedUnitCount = entry.getValue();
            this.preparedModifierToHit = 0;
            this.numOfUnit = 0;
            this.modifierToHit = 0;
            this.numRolls = 0;
            this.multiplier = 1;
            this.numRollsPerUnit = unitModel.getCombatDieCountForAbility(pipeline.rollType, pipeline.player);
            this.extraRollsForUnit = 0;
            this.extraRollsCount = false;
            this.rollGroups = List.of();
        }

        void recordPrimaryRoll(List<Die> dice) {
            primaryDiceHistory.clear();
            primaryDiceHistory.addAll(dice);
            activeDice = new ArrayList<>(dice);
            rerollDiceHistory.clear();
            numRolls = dice.size();
            multiplier = pipeline.usesX89c4 ? 2 : 1;
            hitRolls = DiceHelper.countSuccesses(activeDice);
            numMisses = 0;
            pipeline.player.setExpectedHitsTimes10(
                    pipeline.player.getExpectedHitsTimes10() + (numRolls * (11 - toHit + modifierToHit)));
            groupAllHitsProbability = Math.pow((11 - toHit + modifierToHit) / 10.0, numRolls);
            pipeline.chanceOfAllMiss *= Math.pow((toHit - modifierToHit - 1) / 10.0, numRolls);
            groupMaximumHits = numRolls;
        }

        void commitPrimaryRollTotals() {
            pipeline.maximumHits += groupMaximumHits;
            pipeline.chanceOfAllHits *= groupAllHitsProbability;
            numMisses = numRolls - DiceHelper.countSuccesses(activeDice);
            pipeline.totalMisses += numMisses;
            pipeline.totalHits += hitRolls;
        }

        void addHits(int hits) {
            hitRolls += hits;
        }

        void addMaximumHits(int hits) {
            groupMaximumHits += hits;
        }

        void addResolvedHits(int hits) {
            pipeline.totalHits += hits;
        }

        void recordAdditionalDice(List<Die> dice) {
            pipeline.player.setExpectedHitsTimes10(
                    pipeline.player.getExpectedHitsTimes10() + (dice.size() * (11 - toHit + modifierToHit)));
            groupAllHitsProbability *= Math.pow((11 - toHit + modifierToHit) / 10.0, dice.size());
            pipeline.chanceOfAllMiss *= Math.pow((toHit - modifierToHit - 1) / 10.0, dice.size());
            groupMaximumHits += dice.size();
            numRolls += dice.size();
            activeDice.addAll(dice);
            primaryDiceHistory.addAll(dice);
        }

        RerollResult rollMisses(int diceCount) {
            RerollResult result = rollReplacementDice(diceCount);
            pipeline.chanceOfAllHits *= Math.pow((11 - toHit + modifierToHit) / 10.0, diceCount);
            pipeline.chanceOfAllMiss *= Math.pow((toHit - modifierToHit - 1) / 10.0, diceCount);
            return result;
        }

        RerollResult rollReplacementDice(int diceCount) {
            List<Die> dice = DiceHelper.rollDice(toHit - modifierToHit, diceCount);
            rerollDiceHistory.addAll(dice);
            pipeline.player.setExpectedHitsTimes10(
                    pipeline.player.getExpectedHitsTimes10() + (diceCount * (11 - toHit + modifierToHit)));
            return new RerollResult(dice, DiceHelper.countSuccesses(dice));
        }

        void destroyMissedUnits() {
            UnitRollAbilities.destroyThalnosMissedUnits(this);
        }

        String renderAndRecordRoll(
                int displayedExtraRolls, UnitRollType payloadRollType, List<Die> dice, int hits, DieRollSource source) {
            return renderAndRecordRoll(displayedExtraRolls, payloadRollType, dice, hits, source, "");
        }

        String renderAndRecordRoll(
                int displayedExtraRolls,
                UnitRollType payloadRollType,
                List<Die> dice,
                int hits,
                DieRollSource source,
                String holderLabel) {
            pipeline.payloadBuilder.addUnitRoll(this, displayedExtraRolls, payloadRollType, dice, hits, source);
            return CombatMessageHelper.displayUnitRoll(
                    unitModel,
                    toHit,
                    modifierToHit,
                    numOfUnit,
                    numRollsPerUnit,
                    displayedExtraRolls,
                    dice,
                    hits,
                    holderLabel);
        }
    }

    record UnitRollGroup(int unitCount, int modifierToHit, int diceCount, int extraRolls) {}

    record RerollResult(List<Die> dice, int hits) {}

    static class UnitRollPipelineState {
        Map<Pair<UnitModel, UnitHolder>, Integer> playerUnits;
        final List<NamedCombatModifierModel> extraRolls;
        final List<NamedCombatModifierModel> autoMods;
        final List<NamedCombatModifierModel> tempMods;
        final Player player;
        final Player opponent;
        final Game game;
        final CombatRollType rollType;
        final GenericInteractionCreateEvent event;
        final Tile activeSystem;
        final UnitHolder unitHolder;
        List<NamedCombatModifierModel> mods;
        final List<UnitModel> playerUnitsList;
        Set<String> divergingModels = Set.of();
        final Set<String> consumedBestMods = new HashSet<>();
        final RollPayloadBuilder payloadBuilder = new RollPayloadBuilder();
        final List<CombatRollPayload.CombatRollNote> delayedAfterTotalNotes = new ArrayList<>();
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

        UnitRollPipelineState(CombatRollPipelineState combat) {
            playerUnits = combat.playerUnits;
            extraRolls = combat.modifiers.extraRolls();
            autoMods = combat.modifiers.combatModifiers();
            tempMods = combat.modifiers.temporaryModifiers();
            player = combat.player;
            opponent = combat.opponent;
            game = combat.game;
            rollType = combat.rollType;
            event = combat.event;
            activeSystem = combat.tile;
            unitHolder = combat.combatOnHolder;
            playerUnitsList = playerUnits.keySet().stream().map(Pair::getLeft).collect(Collectors.toList());
            List<UnitType> unitTypes =
                    playerUnitsList.stream().map(UnitModel::getUnitType).toList();
            hacanFlagship = playerUnitsList.stream().anyMatch(unit -> "hacan_flagship".equals(unit.getId()))
                    || (unitTypes.contains(UnitType.Flagship)
                            && ValefarZService.hasCopiedFlagshipAbility(game, player, "hacan_flagship"));
            tkHacanWarsun = player.hasUnit("tk-fallofkenara") && unitTypes.contains(UnitType.Warsun);
            isThalnosReroll = "true".equalsIgnoreCase(game.getStoredValue("thalnosPlusOne"));
            space = activeSystem.getUnitHolders().get(Constants.SPACE);
            usesX89c4 = player.hasTech("x89c4")
                    && (rollType == CombatRollType.combatround || rollType == CombatRollType.bombardment)
                    && (!Constants.SPACE.equalsIgnoreCase(unitHolder.getName())
                            || rollType == CombatRollType.bombardment);
        }
    }

    static UnitMergeResult mergeAndDetectDivergence(UnitRollPipelineState state) {

        IdentityHashMap<Pair<UnitModel, UnitHolder>, Integer> countByIdentity = new IdentityHashMap<>();
        state.playerUnits.forEach(countByIdentity::put);
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
                Pair<UnitModel, UnitHolder> k = keys.get(0);
                merged.put(k, countByIdentity.get(k));
                continue;
            }
            IdentityHashMap<Pair<UnitModel, UnitHolder>, UnitMergeProfile> profiles = new IdentityHashMap<>();
            for (Pair<UnitModel, UnitHolder> key : keys) {
                UnitModel m = key.getLeft();
                UnitHolder h = key.getRight();
                int toHit = m.getCombatDieHitsOnForAbility(state.rollType, state.player);
                if (state.rollType == CombatRollType.combatround) {
                    toHit = CombatStatsService.getCombatRoundProfile(
                                    true, m, state.player, state.activeSystem, state.opponent, false)
                            .hitsOn();
                }
                int mod = CombatModHelper.getCombinedModifierForUnit(
                        m,
                        countByIdentity.get(key),
                        state.mods,
                        state.player,
                        state.opponent,
                        state.game,
                        state.playerUnitsList,
                        state.rollType,
                        state.activeSystem,
                        h);
                int extraDicePerUnit = CombatModHelper.getCombinedModifierForUnit(
                        m,
                        1,
                        state.extraRolls,
                        state.player,
                        state.opponent,
                        state.game,
                        state.playerUnitsList,
                        state.rollType,
                        state.activeSystem,
                        h);
                int dicePerUnit = m.getCombatDieCountForAbility(state.rollType, state.player);
                profiles.put(key, new UnitMergeProfile(toHit - mod, dicePerUnit, extraDicePerUnit));
            }
            Set<UnitMergeProfile> distinctProfiles = new HashSet<>(profiles.values());
            if (distinctProfiles.size() > 1) {
                divergingModels.add(modelEntry.getKey());
                keys.sort(Comparator.comparing(profiles::get));
                for (Pair<UnitModel, UnitHolder> k : keys) merged.put(k, countByIdentity.get(k));
            } else {
                int totalCount = keys.stream().mapToInt(countByIdentity::get).sum();
                merged.put(keys.get(0), totalCount);
            }
        }
        return new UnitMergeResult(merged, divergingModels);
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

    static class RollPayloadBuilder {
        final List<CombatRollPayload.CombatRollNote> notes = new ArrayList<>();
        final List<CombatRollPayload.ModifierDisplay> modifiers = new ArrayList<>();
        final List<CombatRollPayload.UnitRoll> unitRolls = new ArrayList<>();
        int diceRolled;

        void addNote(CombatRollPayload.CombatRollNote note) {
            if (note != null) {
                notes.add(note);
            }
        }

        void addModifierDisplays(
                List<NamedCombatModifierModel> namedModifiers,
                Map<UnitModel, Integer> units,
                Player player,
                Player opponent,
                Game game,
                CombatRollType rollType,
                Tile activeSystem,
                UnitHolder unitHolder) {
            if (namedModifiers.isEmpty()) return;

            List<UnitModel> playerUnits = new ArrayList<>(units.keySet());
            for (NamedCombatModifierModel namedModifier : namedModifiers) {
                CombatModifierModel modifier = namedModifier.getModifier();
                Map<String, Integer> effectiveValues = new HashMap<>();
                for (Map.Entry<UnitModel, Integer> unitEntry : units.entrySet()) {
                    UnitModel unit = unitEntry.getKey();
                    int effectiveValue = CombatModHelper.getCombinedModifierForUnit(
                            unit,
                            unitEntry.getValue(),
                            List.of(namedModifier),
                            player,
                            opponent,
                            game,
                            playerUnits,
                            rollType,
                            activeSystem,
                            unitHolder);
                    if (effectiveValue != 0) {
                        effectiveValues.put(unit.getAsyncId(), effectiveValue);
                    }
                }
                modifiers.add(new CombatRollPayload.ModifierDisplay(
                        modifier.getAlias(),
                        namedModifier.getName(),
                        modifier.getValue(),
                        modifier.getType(),
                        modifier.getScope(),
                        resolveScopeDisplay(modifier, units),
                        effectiveValues));
            }
        }

        void addUnitRoll(
                UnitRollState unit,
                int extraRolls,
                UnitRollType payloadRollType,
                List<DiceHelper.Die> dice,
                int hits,
                DieRollSource source) {
            diceRolled += dice.size();
            unitRolls.add(new CombatRollPayload.UnitRoll(
                    unit.unitModel.getId(),
                    unit.unitModel.getAsyncId(),
                    unit.unitModel.getBaseType(),
                    unit.unitModel.getName(),
                    getDisplayedUnitName(unit.unitModel),
                    unit.unitModel.getUnitEmoji().toString(),
                    unit.numOfUnit,
                    unit.numRollsPerUnit,
                    extraRolls,
                    unit.toHit,
                    unit.modifierToHit,
                    unit.toHit - unit.modifierToHit,
                    payloadRollType,
                    toDieRolls(dice, source),
                    hits));
        }

        CombatRollPayload build(int displayedTotalHits, int misses, int maximumHits) {
            return new CombatRollPayload(
                    null,
                    notes,
                    modifiers,
                    unitRolls,
                    new CombatRollPayload.RollTotal(diceRolled, displayedTotalHits, misses, maximumHits));
        }

        List<CombatRollPayload.DieRoll> toDieRolls(List<DiceHelper.Die> dice, DieRollSource source) {
            if (dice.isEmpty()) return List.of();
            return dice.stream()
                    .map(die ->
                            new CombatRollPayload.DieRoll(die.getResult(), die.getThreshold(), die.isSuccess(), source))
                    .toList();
        }

        String getDisplayedUnitName(UnitModel unitModel) {
            if (unitModel.getUpgradesFromUnitId().isPresent()
                    || unitModel.getFaction().isPresent()) {
                return unitModel.getName();
            }
            return "";
        }

        String resolveScopeDisplay(CombatModifierModel modifier, Map<UnitModel, Integer> units) {
            String unitScope = modifier.getScope();
            if (isBlank(unitScope)) return "all";
            return units.keySet().stream()
                    .filter(unit -> unit.getAsyncId().equals(unitScope))
                    .findFirst()
                    .map(unit -> unit.getUnitEmoji().toString())
                    .orElse(unitScope);
        }
    }
}
