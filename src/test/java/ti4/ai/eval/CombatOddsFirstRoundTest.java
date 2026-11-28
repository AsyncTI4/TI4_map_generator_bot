package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.CombatOdds.Force;
import ti4.ai.eval.CombatOdds.Outcome;

// One unit each, no sustain. A unit hitting on 8 hits 30% of the time, on 7 40%, on 6 50%.
class CombatOddsFirstRoundTest {

    private static final double EXACT = 1e-6;
    private static final Combatant HITS_ON_8 = new Combatant(8, 1, false, 1);
    private static final Combatant HITS_ON_7 = new Combatant(7, 1, false, 1);
    private static final Combatant HITS_ON_6 = new Combatant(6, 1, false, 1);
    private static final Combatant HITS_ON_6_SUSTAINING = new Combatant(6, 1, true, 2);

    private static Force of(Combatant... units) {
        return Force.of(List.of(units));
    }

    // Level at 30% each: only one side hitting decides it, so the attacker wins 0.21 / 0.51 of the time.
    @Test
    void withoutAFirstRoundBoostItIsTheOrdinaryFight() {
        Force attacker = of(HITS_ON_8);
        Force defender = of(HITS_ON_8);

        Outcome plain = CombatOdds.resolve(attacker, defender);
        Outcome same = CombatOdds.resolveFirstRound(attacker, defender, attacker, defender);

        assertThat(plain.attackerWins()).isCloseTo(0.21 / 0.51, within(EXACT));
        assertThat(same.attackerWins()).isCloseTo(plain.attackerWins(), within(EXACT));
        assertThat(same.defenderWins()).isCloseTo(plain.defenderWins(), within(EXACT));
    }

    // First round, the attacker hits 40% and the defender 30%:
    //   attacker only 0.4 * 0.7 = 0.28, defender only 0.6 * 0.3 = 0.18, both 0.12, neither 0.42.
    // The neither case continues as a plain 30% fight, which the attacker wins 0.21 / 0.51 of the time, loses
    // 0.21 / 0.51 of the time, and ends with both destroyed 0.09 / 0.51 of the time.
    @Test
    void onlyTheFirstRoundUsesTheBoostedDice() {
        Force attacker = of(HITS_ON_8);
        Force defender = of(HITS_ON_8);
        Force boosted = of(HITS_ON_7);
        double later = 0.21 / 0.51;

        Outcome outcome = CombatOdds.resolveFirstRound(attacker, defender, boosted, defender);

        assertThat(outcome.attackerWins()).isCloseTo(0.28 + 0.42 * later, within(EXACT));
        assertThat(outcome.defenderWins()).isCloseTo(0.18 + 0.42 * later, within(EXACT));
        assertThat(outcome.bothDestroyed()).isCloseTo(0.12 + 0.42 * 0.09 / 0.51, within(EXACT));
    }

    @Test
    void aBoostForTheDefenderHelpsTheDefender() {
        Force attacker = of(HITS_ON_8);
        Force defender = of(HITS_ON_8);
        double later = 0.21 / 0.51;

        Outcome outcome = CombatOdds.resolveFirstRound(attacker, defender, attacker, of(HITS_ON_7));

        assertThat(outcome.defenderWins()).isCloseTo(0.28 + 0.42 * later, within(EXACT));
        assertThat(outcome.attackerWins()).isCloseTo(0.18 + 0.42 * later, within(EXACT));
    }

    // A sustaining attacker that has already taken one hit is one hit from death: a plain 50% duel, in which each
    // side wins a third of the time and a third ends with both destroyed.
    @Test
    void theFightCanStartFromHitsAlreadyAbsorbed() {
        Force attacker = of(HITS_ON_6_SUSTAINING);
        Force defender = of(HITS_ON_6);

        Outcome outcome = CombatOdds.resolveDetailed(attacker, defender, attacker, defender, 1, 0)
                .outcome();

        assertThat(outcome.attackerWins()).isCloseTo(1.0 / 3, within(EXACT));
        assertThat(outcome.defenderWins()).isCloseTo(1.0 / 3, within(EXACT));
        assertThat(outcome.bothDestroyed()).isCloseTo(1.0 / 3, within(EXACT));
    }
}
