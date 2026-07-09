package ti4.service.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNote;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNotePlacement;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNoteType;
import ti4.contest.replay.core.CombatRollPayload.DieRollSource;
import ti4.model.UnitModel;
import ti4.service.combat.CombatV2DiceData.DieResult;
import ti4.service.combat.CombatV2DiceData.RollResult;
import ti4.service.combat.CombatV2DiceData.RollSegment;
import ti4.service.combat.CombatV2DiceData.RollSource;
import ti4.service.combat.CombatV2DiceData.UnitRollPlan;
import ti4.service.combat.CombatV2DiceData.UnitRollResult;
import ti4.service.combat.CombatV2RollData.Context;
import ti4.service.combat.CombatV2RollData.Round;

/** Builds the structured replay and Discord-rendering payload for a completed roll. */
@UtilityClass
class CombatV2RollPayloads {

    static CombatRollPayload create(Context context, Round round, RollResult rolled) {
        CombatRollPayload.RollHeader header = new CombatRollPayload.RollHeader(
                context.getFaction(),
                context.getColor(),
                context.getFactionEmoji(),
                context.opponentFaction(),
                context.opponentColor(),
                context.getTilePosition(),
                context.getTileId(),
                context.holderName(),
                round.displayName(),
                context.rollType(),
                round.rollingSideRound() > 0 ? round.rollingSideRound() : null,
                "true".equalsIgnoreCase(context.storedValue("thalnosPlusOne")),
                context.isFowMode());
        return new CombatRollPayload(
                header,
                combatNotes(context, rolled),
                modifierDisplays(context),
                unitRolls(rolled),
                new CombatRollPayload.RollTotal(
                        diceRolled(rolled), rolled.totalHits(), rolled.totalMisses(), rolled.maximumHits()));
    }

    private static List<CombatRollNote> combatNotes(Context context, RollResult rolled) {
        boolean thalnosAvailable = context.rollType() == CombatRollType.combatround
                && context.player().hasRelic("thalnos")
                && rolled.totalMisses() > 0
                && !"true".equalsIgnoreCase(context.storedValue("thalnosPlusOne"));
        if (!thalnosAvailable) return List.of();
        return List.of(new CombatRollNote(
                CombatRollNoteType.REROLL_AVAILABLE,
                CombatRollNotePlacement.AFTER_TOTAL,
                "thalnos",
                null,
                rolled.totalMisses(),
                Map.of("actorEmoji", context.getFactionEmoji())));
    }

    private static List<CombatRollPayload.ModifierDisplay> modifierDisplays(Context context) {
        List<CombatRollPayload.ModifierDisplay> displays = new ArrayList<>();
        for (var applied : context.appliedModifiers()) {
            var modifier = applied.rule();
            String scope = modifier.getScope();
            String scopeDisplay = scopeDisplay(context, scope);
            displays.add(new CombatRollPayload.ModifierDisplay(
                    modifier.getAlias(),
                    applied.displayName(),
                    modifier.getValue(),
                    modifier.getType(),
                    scope,
                    scopeDisplay,
                    Map.of()));
        }
        return displays;
    }

    private static String scopeDisplay(Context context, String scope) {
        if (scope == null || scope.isBlank()) return "all";
        for (UnitModel unit : context.rollingUnitModels()) {
            if (unit.getAsyncId().equals(scope)) return unit.getUnitEmoji().toString();
        }
        return scope;
    }

    private static List<CombatRollPayload.UnitRoll> unitRolls(RollResult rolled) {
        List<CombatRollPayload.UnitRoll> unitRolls = new ArrayList<>();
        for (UnitRollResult unitResult : rolled.units()) {
            UnitRollPlan plan = unitResult.plan();
            for (RollSegment segment : unitResult.segments()) {
                unitRolls.add(unitRoll(plan, segment));
            }
        }
        return unitRolls;
    }

    private static CombatRollPayload.UnitRoll unitRoll(UnitRollPlan plan, RollSegment segment) {
        List<CombatRollPayload.DieRoll> dice = segment.dice().stream()
                .map(die -> dieRoll(die, segment.source()))
                .toList();
        return new CombatRollPayload.UnitRoll(
                plan.unitId(),
                plan.asyncId(),
                plan.baseType(),
                plan.name(),
                plan.displayName(),
                plan.emoji(),
                plan.quantity(),
                plan.dicePerUnit(),
                segment.source() == RollSource.PRIMARY ? plan.extraDice() : 0,
                plan.hitsOn(),
                plan.modifier(),
                plan.threshold(),
                segment.source(),
                dice,
                segment.hits());
    }

    private static CombatRollPayload.DieRoll dieRoll(DieResult die, RollSource source) {
        return new CombatRollPayload.DieRoll(die.result(), die.threshold(), die.success(), dieSource(source));
    }

    private static DieRollSource dieSource(RollSource source) {
        return switch (source) {
            case JOL_NAR_COMMANDER_HITS -> DieRollSource.REROLL_HIT;
            case JOL_NAR_COMMANDER_MISSES, IRON_COMMANDER_MISSES -> DieRollSource.REROLL_MISS;
            case KALTRIM_COMMANDER_ONES -> DieRollSource.REROLL_ONE;
            case MUNITIONS_RESERVES -> DieRollSource.MUNITIONS_RESERVES;
            default -> DieRollSource.PRIMARY;
        };
    }

    private static int diceRolled(RollResult rolled) {
        return rolled.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .mapToInt(segment -> segment.dice().size())
                .sum();
    }
}
