package ti4.contest.replay.core;

import java.util.List;
import java.util.Map;
import ti4.service.combat.CombatRollType;

/**
 * Structured combat roll snapshot used to replay roll messages without depending on live Discord text.
 */
public record CombatRollPayload(
        RollHeader header,
        List<CombatRollNote> notes,
        List<ModifierDisplay> modifiers,
        List<UnitRoll> unitRolls,
        RollTotal total) {

    public CombatRollPayload {
        notes = notes == null ? List.of() : List.copyOf(notes);
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        unitRolls = unitRolls == null ? List.of() : List.copyOf(unitRolls);
    }

    public static CombatRollPayload empty() {
        return new CombatRollPayload(null, List.of(), List.of(), List.of(), null);
    }

    public CombatRollPayload withHeader(RollHeader header) {
        return new CombatRollPayload(header, notes, modifiers, unitRolls, total);
    }

    public enum CombatRollNoteType {
        UNIT_REPAIRED,
        SINGLE_UNIT_ROLL_MOD_APPLIED,
        UNIT_DESTROYED_FROM_ROLL,
        OPPONENT_UNIT_DESTROYED_FROM_ROLL,
        REROLL_AVAILABLE
    }

    public enum CombatRollNotePlacement {
        BEFORE_MODIFIERS,
        BEFORE_UNIT_ROLLS,
        AFTER_UNIT_ROLLS,
        AFTER_TOTAL
    }

    public enum RollSegmentType {
        PRIMARY,
        SUPERCHARGE_SELECTED_UNIT,
        SUPERCHARGE_REST,
        GRAVLEASH_SELECTED_UNIT,
        GRAVLEASH_REST,
        JOL_NAR_COMMANDER_REROLL_MISSES,
        JOL_NAR_COMMANDER_REROLL_HITS,
        IRON_COMMANDER_REROLL_MISSES,
        KALTRIM_COMMANDER_REROLL_ONES,
        MUNITIONS_RESERVES_REROLL
    }

    public enum DieRollSource {
        PRIMARY,
        REROLL_MISS,
        REROLL_HIT,
        REROLL_ONE,
        MUNITIONS_RESERVES,
        DECOY
    }

    public record RollHeader(
            String actorFaction,
            String actorColor,
            String actorEmoji,
            String opponentFaction,
            String opponentColor,
            String tilePosition,
            String tileId,
            String unitHolderName,
            String combatDisplayName,
            CombatRollType rollType,
            Integer combatRound,
            boolean thalnosReroll,
            boolean fowMode) {}

    public record CombatRollNote(
            CombatRollNoteType type,
            CombatRollNotePlacement placement,
            String sourceId,
            String unitId,
            Integer count,
            Map<String, String> details) {
        public CombatRollNote {
            details = details == null ? Map.of() : Map.copyOf(details);
        }
    }

    public record ModifierDisplay(
            String modifierAlias,
            String sourceName,
            int value,
            String type,
            String scopeUnitAsyncId,
            String scopeDisplay,
            Map<String, Integer> effectiveValueByUnitAsyncId) {
        public ModifierDisplay {
            effectiveValueByUnitAsyncId =
                    effectiveValueByUnitAsyncId == null ? Map.of() : Map.copyOf(effectiveValueByUnitAsyncId);
        }
    }

    public record UnitRoll(
            String unitId,
            String asyncId,
            String baseType,
            String unitName,
            String unitDisplayName,
            String unitEmoji,
            int quantity,
            int dicePerUnit,
            int extraDice,
            int printedHitsOn,
            int modifier,
            int effectiveThreshold,
            RollSegmentType segmentType,
            List<DieRoll> dice,
            int hits) {
        public UnitRoll {
            dice = dice == null ? List.of() : List.copyOf(dice);
        }
    }

    public record DieRoll(int result, int threshold, boolean success, DieRollSource source) {}

    public record RollTotal(int diceRolled, int displayedTotalHits, int misses, int maximumHits) {}
}
