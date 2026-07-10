package ti4.service.combat.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNote;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNotePlacement;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNoteType;
import ti4.contest.replay.core.CombatRollPayload.DieRollSource;
import ti4.game.Planet;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.v2.CombatV2DiceData.DieResult;
import ti4.service.combat.v2.CombatV2DiceData.RollResult;
import ti4.service.combat.v2.CombatV2DiceData.RollSegment;
import ti4.service.combat.v2.CombatV2DiceData.RollSource;
import ti4.service.combat.v2.CombatV2DiceData.UnitRollPlan;
import ti4.service.combat.v2.CombatV2DiceData.UnitRollResult;
import ti4.service.combat.v2.CombatV2RollData.Context;
import ti4.service.combat.v2.CombatV2RollData.Round;

/** Builds the structured replay and Discord-rendering payload for a completed roll. */
@UtilityClass
class CombatV2RollPayloads {

    static CombatRollPayload create(
            Context context, Round round, RollResult rolled, List<CombatRollNote> preRollNotes) {
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
                combatNotes(context, rolled, preRollNotes),
                modifierDisplays(context, rolled),
                unitRolls(context, rolled),
                new CombatRollPayload.RollTotal(
                        diceRolled(rolled), rolled.totalHits(), rolled.totalMisses(), rolled.maximumHits()));
    }

    private static List<CombatRollNote> combatNotes(
            Context context, RollResult rolled, List<CombatRollNote> preRollNotes) {
        List<CombatRollNote> notes = new ArrayList<>(preRollNotes);
        rolled.units().stream()
                .filter(unit -> unit.plan().initialSource() == RollSource.SUPERCHARGE_SELECTED_UNIT
                        || unit.plan().initialSource() == RollSource.GRAVLEASH_SELECTED_UNIT)
                .findFirst()
                .ifPresent(unit -> {
                    boolean supercharge = unit.plan().initialSource() == RollSource.SUPERCHARGE_SELECTED_UNIT;
                    notes.add(new CombatRollNote(
                            CombatRollNoteType.SINGLE_UNIT_ROLL_MOD_APPLIED,
                            CombatRollNotePlacement.BEFORE_UNIT_ROLLS,
                            supercharge ? "tf-supercharge" : "letnevbt",
                            unit.plan().unit().getAsyncId(),
                            1,
                            Map.of("modifier", Integer.toString(unit.plan().modifier()))));
                });
        boolean thalnosAvailable = context.rollType() == CombatRollType.combatround
                && context.player().hasRelic("thalnos")
                && rolled.totalMisses() > 0
                && !"true".equalsIgnoreCase(context.storedValue("thalnosPlusOne"));
        if (thalnosAvailable) {
            notes.add(new CombatRollNote(
                    CombatRollNoteType.REROLL_AVAILABLE,
                    CombatRollNotePlacement.AFTER_TOTAL,
                    "thalnos",
                    null,
                    rolled.totalMisses(),
                    Map.of("actorEmoji", context.getFactionEmoji())));
        }
        boolean thalnosActive = "true".equalsIgnoreCase(context.storedValue("thalnosPlusOne"));
        boolean hacanProtected = context.playerHasUnit("hacan_flagship") || context.playerHasUnit("tk-fallofkenara");
        if (thalnosActive && !hacanProtected) {
            for (UnitRollResult unit : rolled.units()) {
                boolean untracked = unit.plan().modifiers().stream()
                        .anyMatch(modifier -> "thalnos_untracked_extra_dice".equals(modifier.id()));
                if (unit.initialMisses() < 1 || untracked) continue;
                notes.add(new CombatRollNote(
                        CombatRollNoteType.UNIT_DESTROYED_FROM_ROLL,
                        CombatRollNotePlacement.AFTER_TOTAL,
                        "thalnos",
                        unit.plan().unit().getId(),
                        unit.initialMisses(),
                        Map.of(
                                "actorEmoji", context.getFactionEmoji(),
                                "unitName", unit.plan().unit().getName())));
            }
        }
        int strikeWingRolls = (int) rolled.units().stream()
                .filter(unit ->
                        "argent_destroyer2".equalsIgnoreCase(unit.plan().unit().getId())
                                || "tf-swa".equalsIgnoreCase(unit.plan().unit().getId()))
                .flatMap(unit -> unit.segments().stream())
                .flatMap(segment -> segment.dice().stream())
                .filter(die -> die.result() > 8)
                .count();
        int strikeWingKills = Math.min(
                strikeWingRolls,
                context.tile()
                        .getSpaceUnitHolder()
                        .getUnitCount(UnitType.Infantry, context.opponent().getColor()));
        if (context.rollType() == CombatRollType.AFB && context.player() != context.opponent() && strikeWingKills > 0) {
            String source = rolled.units().stream()
                    .map(unit -> unit.plan().unit())
                    .filter(unit -> "argent_destroyer2".equalsIgnoreCase(unit.getId())
                            || "tf-swa".equalsIgnoreCase(unit.getId()))
                    .map(UnitModel::getId)
                    .findFirst()
                    .orElse("argent_destroyer2");
            notes.add(new CombatRollNote(
                    CombatRollNoteType.OPPONENT_UNIT_DESTROYED_FROM_ROLL,
                    CombatRollNotePlacement.AFTER_UNIT_ROLLS,
                    source,
                    "infantry",
                    strikeWingKills,
                    Map.of("opponent", context.opponent().getRepresentation(false, true))));
        }
        return List.copyOf(notes);
    }

    private static List<CombatRollPayload.ModifierDisplay> modifierDisplays(Context context, RollResult rolled) {
        List<CombatRollPayload.ModifierDisplay> displays = new ArrayList<>();
        for (var modifier : context.resolvedModifiers()) {
            if (modifier.ruleId().startsWith("v2_")) continue;
            var target = CombatV2Modifiers.target(modifier, context);
            displays.add(new CombatRollPayload.ModifierDisplay(
                    modifier.ruleId(),
                    modifier.displayName(),
                    resolvedValue(rolled, modifier.ruleId()),
                    modifierType(rolled, modifier.ruleId()),
                    target.scope(),
                    target.display(),
                    Map.of()));
        }
        return displays;
    }

    private static int resolvedValue(RollResult rolled, String id) {
        return rolled.units().stream()
                .flatMap(unit -> unit.plan().modifiers().stream())
                .filter(modifier -> id.equals(modifier.id()))
                .mapToInt(modifier -> switch (modifier) {
                    case CombatV2DiceData.ValueModifier value -> value.value();
                    case CombatV2DiceData.StatModifier stat -> stat.value();
                    default -> 0;
                })
                .findFirst()
                .orElse(0);
    }

    private static String modifierType(RollResult rolled, String id) {
        return rolled.units().stream()
                .flatMap(unit -> unit.plan().modifiers().stream())
                .filter(modifier -> id.equals(modifier.id()))
                .map(modifier -> switch (modifier) {
                    case CombatV2DiceData.ValueModifier value ->
                        value.effect() == CombatV2DiceData.ModifierEffect.EXTRA_DICE
                                ? Constants.COMBAT_EXTRA_ROLLS
                                : Constants.COMBAT_MODIFIERS;
                    case CombatV2DiceData.StatModifier stat ->
                        stat.stat() == CombatV2DiceData.UnitRollStat.DICE_PER_UNIT
                                ? Constants.COMBAT_EXTRA_ROLLS
                                : Constants.COMBAT_MODIFIERS;
                    default -> Constants.COMBAT_MODIFIERS;
                })
                .findFirst()
                .orElse(Constants.COMBAT_MODIFIERS);
    }

    private static List<CombatRollPayload.UnitRoll> unitRolls(Context context, RollResult rolled) {
        List<CombatRollPayload.UnitRoll> unitRolls = new ArrayList<>();
        for (UnitRollResult unitResult : rolled.units()) {
            UnitRollPlan plan = unitResult.plan();
            for (RollSegment segment : unitResult.segments()) {
                unitRolls.add(unitRoll(context, rolled, plan, segment));
            }
        }
        return unitRolls;
    }

    private static CombatRollPayload.UnitRoll unitRoll(
            Context context, RollResult rolled, UnitRollPlan plan, RollSegment segment) {
        UnitModel unit = plan.unit();
        List<CombatRollPayload.DieRoll> dice = segment.dice().stream()
                .map(die -> dieRoll(die, segment.source()))
                .toList();
        String displayName =
                unit.getUpgradesFromUnitId().isPresent() || unit.getFaction().isPresent() ? unit.getName() : "";
        boolean divergesByHolder = rolled.units().stream()
                        .filter(result -> result.plan().unit().getId().equals(unit.getId()))
                        .mapToInt(result -> result.plan().threshold())
                        .distinct()
                        .count()
                > 1;
        if (divergesByHolder && plan.holder() instanceof Planet planet) {
            displayName += " on **" + Helper.getPlanetRepresentationNoResInf(planet.getName(), context.game()) + "**";
        }
        return new CombatRollPayload.UnitRoll(
                unit.getId(),
                unit.getAsyncId(),
                unit.getBaseType(),
                unit.getName(),
                displayName,
                unit.getUnitEmoji().toString(),
                plan.quantity(),
                plan.dicePerUnit(),
                isInitialRoll(segment.source()) ? plan.extraDice() : 0,
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

    private static boolean isInitialRoll(RollSource source) {
        return switch (source) {
            case PRIMARY, SUPERCHARGE_SELECTED_UNIT, SUPERCHARGE_REST, GRAVLEASH_SELECTED_UNIT, GRAVLEASH_REST -> true;
            default -> false;
        };
    }

    private static int diceRolled(RollResult rolled) {
        return rolled.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .mapToInt(segment -> segment.dice().size())
                .sum();
    }
}
