package ti4.service.combat.v2;

import java.util.List;
import ti4.game.UnitHolder;
import ti4.model.UnitModel;

/** Plain instructions and results for the context-free combat dice engine. */
public final class CombatV2DiceData {
    private CombatV2DiceData() {}

    /**
     * Describes what a resolved modifier instructs the dice engine to change.
     *
     * <p>This enum deliberately describes roll behavior, not where a modifier came from or how long it lasts. Those
     * concerns belong to {@link RollSource} and {@link ModifierDuration}. Keeping them separate prevents temporary
     * modifiers from becoming a special execution path in the engine.
     */
    public enum ModifierEffect {
        /** Changes the target number used to decide whether each die hits. Positive values improve the roll. */
        TO_HIT,

        /** Adds ordinary dice to the initial roll before any rerolls or triggered additional rolls occur. */
        EXTRA_DICE,

        /** Adds a fixed number of hits without rolling dice. */
        FLAT_HITS
    }

    /**
     * Describes how long the game-state source of a modifier remains available.
     *
     * <p>The dice engine does not consume modifiers. It receives an immutable roll plan and applies every modifier in
     * that plan exactly once. After a successful roll, the combat workflow uses this duration to decide whether the
     * backing game-state effect must be removed. Validation failures and rejected rolls therefore consume nothing.
     */
    public enum ModifierDuration {
        /**
         * Remains eligible while its persistent source is active. This includes faction abilities, technologies,
         * units, laws, and conditional rules whose eligibility is recalculated for every roll.
         */
        PERMANENT,

        /** Applies to one successfully resolved roll and is consumed immediately after that roll. */
        ONE_ROLL,

        /** Applies throughout one numbered combat round and expires when that round ends. */
        ONE_COMBAT_ROUND,

        /** Applies for the remainder of the current combat and expires when that combat ends. */
        ONE_COMBAT,

        /** Applies for the remainder of the current tactical action, potentially spanning multiple combats. */
        ONE_TACTICAL_ACTION
    }

    /** A concrete modifier instruction whose game-state eligibility has already been resolved. */
    public sealed interface RollModifier permits ValueModifier, StatModifier, HitRule, RerollRule, AdditionalRollRule {
        default String id() {
            return "";
        }
    }

    /** A resolved numeric change to a unit's target number, initial dice count, or fixed hit count. */
    public record ValueModifier(
            String id, ModifierEffect effect, int value, ModifierDuration duration, String displayName)
            implements RollModifier {}

    /** Identifies a printed unit-roll stat that can be adjusted by a resolved modifier. */
    public enum UnitRollStat {
        /** Number of initial dice rolled for each participating unit of this model. */
        DICE_PER_UNIT,

        /** Printed target number each die must meet before ordinary to-hit modifiers are applied. */
        HITS_ON
    }

    /** Describes how a resolved modifier changes a printed unit-roll stat. */
    public enum StatOperation {
        /** Adds the modifier value to the current effective stat. Ordered additions accumulate. */
        ADD,

        /** Replaces the current effective stat. Later registered modifiers may still change it. */
        SET
    }

    /** A resolved change to one of the unit's printed roll statistics. */
    public record StatModifier(
            String id, UnitRollStat stat, StatOperation operation, int value, ModifierDuration duration)
            implements RollModifier {}

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

    /** Limits bonus-hit rules to the roll sources where their game text actually applies. */
    public enum HitRuleTiming {
        /** Only the dice requested by the unit's base roll plan. */
        INITIAL_ROLL,
        /** Initial dice plus recursively generated dice, before any reroll rules run. */
        BEFORE_REROLLS,
        /** Initial/generated dice plus the Munitions Reserves replacement roll. */
        BEFORE_REROLLS_AND_MUNITIONS,
        /** Every die, including commander and other replacement rerolls. */
        ALL_ROLLS
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

    public enum HitTotalOperation {
        MULTIPLY,
        ADD_IF_HIT
    }

    /** Applies an ordered adjustment after every unit has finished rolling and rerolling. */
    public record HitTotalRule(String id, HitTotalOperation operation, int value) {}

    /** Records how a final-hit rule changed the accumulated hit total. */
    public record HitTotalAdjustment(String id, int before, int after) {}

    public record HitRule(HitMatch match, int result, int bonusHits, HitRuleTiming timing) implements RollModifier {
        public HitRule(HitMatch match, int result, int bonusHits) {
            this(match, result, bonusHits, HitRuleTiming.INITIAL_ROLL);
        }
    }

    public record RerollRule(RollSource source, RerollSelector selector, int maxDice, boolean replaceSelectedDice)
            implements RollModifier {}

    public record AdditionalRollRule(
            RollSource source,
            AdditionalDiceBasis basis,
            HitMatch match,
            int result,
            int dicePerTrigger,
            boolean repeat)
            implements RollModifier {}

    public record UnitRollPlan(
            UnitModel unit,
            UnitHolder holder,
            int quantity,
            int baseDicePerUnit,
            int baseHitsOn,
            RollSource initialSource,
            List<RollModifier> modifiers) {
        public UnitRollPlan {
            modifiers = List.copyOf(modifiers);
        }

        public int diceCount() {
            return quantity * dicePerUnit() + extraDice();
        }

        public int threshold() {
            return Math.max(1, hitsOn() - modifier());
        }

        public int dicePerUnit() {
            return effectiveStat(UnitRollStat.DICE_PER_UNIT, baseDicePerUnit);
        }

        public int hitsOn() {
            return Math.max(1, effectiveStat(UnitRollStat.HITS_ON, baseHitsOn));
        }

        public int extraDice() {
            return Math.max(0, modifierValue(ModifierEffect.EXTRA_DICE));
        }

        public int modifier() {
            return modifierValue(ModifierEffect.TO_HIT);
        }

        public int flatHits() {
            return modifierValue(ModifierEffect.FLAT_HITS);
        }

        private int modifierValue(ModifierEffect effect) {
            return modifiers.stream()
                    .filter(ValueModifier.class::isInstance)
                    .map(ValueModifier.class::cast)
                    .filter(modifier -> modifier.effect() == effect)
                    .mapToInt(ValueModifier::value)
                    .sum();
        }

        private int effectiveStat(UnitRollStat stat, int baseValue) {
            int value = baseValue;
            for (RollModifier modifier : modifiers) {
                if (!(modifier instanceof StatModifier statModifier) || statModifier.stat() != stat) continue;
                value = switch (statModifier.operation()) {
                    case ADD -> value + statModifier.value();
                    case SET -> statModifier.value();
                };
            }
            return value;
        }

        public List<HitRule> hitRules() {
            return modifiers.stream()
                    .filter(HitRule.class::isInstance)
                    .map(HitRule.class::cast)
                    .toList();
        }

        public List<AdditionalRollRule> additionalRolls() {
            return modifiers.stream()
                    .filter(AdditionalRollRule.class::isInstance)
                    .map(AdditionalRollRule.class::cast)
                    .toList();
        }

        public List<RerollRule> rerolls() {
            return modifiers.stream()
                    .filter(RerollRule.class::isInstance)
                    .map(RerollRule.class::cast)
                    .toList();
        }
    }

    public record RollPlan(List<UnitRollPlan> units, int flatHits, List<HitTotalRule> hitTotalRules) {
        public RollPlan {
            units = List.copyOf(units);
            hitTotalRules = List.copyOf(hitTotalRules);
        }
    }

    public record DieResult(int result, int threshold, boolean success) {}

    public record RollSegment(RollSource source, List<DieResult> dice, int hits) {
        public RollSegment {
            dice = List.copyOf(dice);
        }
    }

    public record UnitRollResult(
            UnitRollPlan plan,
            List<RollSegment> segments,
            int hits,
            int initialMisses,
            int misses,
            int maximumHits,
            boolean openEnded) {
        public UnitRollResult {
            segments = List.copyOf(segments);
        }
    }

    public record RollResult(
            List<UnitRollResult> units,
            int rawHits,
            int totalHits,
            int totalMisses,
            int maximumHits,
            boolean whiff,
            boolean slam,
            boolean surprisingWhiff,
            boolean surprisingSlam,
            List<HitTotalAdjustment> hitAdjustments) {
        public RollResult {
            units = List.copyOf(units);
            hitAdjustments = List.copyOf(hitAdjustments);
        }

        public RollResult withTotalHits(int hits) {
            return new RollResult(
                    units,
                    rawHits,
                    hits,
                    totalMisses,
                    maximumHits,
                    whiff,
                    slam,
                    surprisingWhiff,
                    surprisingSlam,
                    hitAdjustments);
        }
    }
}
