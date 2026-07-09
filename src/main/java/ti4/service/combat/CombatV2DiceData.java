package ti4.service.combat;

import java.util.List;

/** Plain instructions and results for the context-free combat dice engine. */
public final class CombatV2DiceData {
    private CombatV2DiceData() {}

    public enum RerollSelector {
        MISSES,
        HITS,
        ONES
    }

    public enum HitMatch {
        SUCCESS,
        EXACT_RESULT,
        AT_LEAST_RESULT
    }

    public enum AdditionalDiceBasis {
        MATCHING_DICE,
        HITS
    }

    public enum RollSource {
        PRIMARY,
        SUPERCHARGE_SELECTED_UNIT,
        SUPERCHARGE_REST,
        GRAVLEASH_SELECTED_UNIT,
        GRAVLEASH_REST,
        SIGMA_JOL_NAR_FLAGSHIP,
        JOL_NAR_COMMANDER_HITS,
        JOL_NAR_COMMANDER_MISSES,
        IRON_COMMANDER_MISSES,
        KALTRIM_COMMANDER_ONES,
        MUNITIONS_RESERVES
    }

    public record HitRule(HitMatch match, int result, int bonusHits) {}

    public record RerollRule(RollSource source, RerollSelector selector, int maxDice, boolean replaceSelectedDice) {}

    public record AdditionalRollRule(
            RollSource source,
            AdditionalDiceBasis basis,
            HitMatch match,
            int result,
            int dicePerTrigger,
            boolean repeat) {}

    public record UnitRollPlan(
            String unitId,
            String asyncId,
            String baseType,
            String name,
            String displayName,
            String emoji,
            int quantity,
            int dicePerUnit,
            int extraDice,
            int hitsOn,
            int modifier,
            int flatHits,
            List<HitRule> hitRules,
            List<AdditionalRollRule> additionalRolls,
            List<RerollRule> rerolls) {
        public UnitRollPlan {
            hitRules = List.copyOf(hitRules);
            additionalRolls = List.copyOf(additionalRolls);
            rerolls = List.copyOf(rerolls);
        }

        public int diceCount() {
            return quantity * dicePerUnit + extraDice;
        }

        public int threshold() {
            return Math.max(1, hitsOn - modifier);
        }
    }

    public record RollPlan(List<UnitRollPlan> units, int flatHits) {
        public RollPlan {
            units = List.copyOf(units);
        }
    }

    public record DieResult(int result, int threshold, boolean success) {}

    public record RollSegment(RollSource source, List<DieResult> dice, int hits) {
        public RollSegment {
            dice = List.copyOf(dice);
        }
    }

    public record UnitRollResult(
            UnitRollPlan plan, List<RollSegment> segments, int hits, int misses, int maximumHits, boolean openEnded) {
        public UnitRollResult {
            segments = List.copyOf(segments);
        }
    }

    public record RollResult(
            List<UnitRollResult> units, int totalHits, int totalMisses, int maximumHits, boolean whiff, boolean slam) {
        public RollResult {
            units = List.copyOf(units);
        }
    }
}
