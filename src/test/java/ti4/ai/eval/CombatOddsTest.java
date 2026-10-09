package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.CombatOdds.Outcome;

class CombatOddsTest {

    private static final Combatant DREADNOUGHT = new Combatant(5, 1, true, 4);
    private static final Combatant CRUISER = new Combatant(7, 1, false, 2);
    private static final Combatant FIGHTER = new Combatant(9, 1, false, 0.5);
    private static final Combatant INFANTRY = new Combatant(8, 1, false, 0.5);

    @Test
    void outcomesAddUpToOne() {
        Outcome outcome = CombatOdds.resolve(List.of(CRUISER, FIGHTER), List.of(DREADNOUGHT));

        assertThat(outcome.attackerWins() + outcome.defenderWins() + outcome.bothDestroyed())
                .isCloseTo(1.0, within(1e-4));
    }

    @Test
    void identicalSidesAreEvenlyMatched() {
        Outcome outcome = CombatOdds.resolve(List.of(CRUISER), List.of(CRUISER));

        assertThat(outcome.attackerWins()).isCloseTo(outcome.defenderWins(), within(1e-6));
    }

    // One cruiser against one cruiser: each round both roll a 7+ (40%). The attacker wins when it hits and the
    // defender misses: 0.4 * 0.6 / (1 - 0.6 * 0.6) = 0.375.
    @Test
    void matchesTheClosedFormForASingleDuel() {
        Outcome outcome = CombatOdds.resolve(List.of(CRUISER), List.of(CRUISER));

        assertThat(outcome.attackerWins()).isCloseTo(0.375, within(1e-4));
        assertThat(outcome.bothDestroyed()).isCloseTo(0.25, within(1e-4));
    }

    @Test
    void overwhelmingForceAlmostAlwaysWins() {
        Outcome outcome = CombatOdds.resolve(List.of(DREADNOUGHT, DREADNOUGHT, DREADNOUGHT), List.of(FIGHTER));

        assertThat(outcome.attackerWins()).isGreaterThan(0.95);
    }

    // Sustain damage absorbs a hit, so a dreadnought beats a cruiser more often than a cruiser beats a cruiser.
    @Test
    void sustainDamageHelps() {
        double cruiserVsCruiser =
                CombatOdds.resolve(List.of(CRUISER), List.of(CRUISER)).attackerWins();
        double dreadnoughtVsCruiser =
                CombatOdds.resolve(List.of(DREADNOUGHT), List.of(CRUISER)).attackerWins();

        assertThat(dreadnoughtVsCruiser).isGreaterThan(cruiserVsCruiser);
    }

    @Test
    void moreInfantryWinsGroundCombatMoreOften() {
        double twoVsOne = CombatOdds.resolve(List.of(INFANTRY, INFANTRY), List.of(INFANTRY))
                .attackerWins();
        double fourVsOne = CombatOdds.resolve(Collections.nCopies(4, INFANTRY), List.of(INFANTRY))
                .attackerWins();

        assertThat(fourVsOne).isGreaterThan(twoVsOne);
    }

    @Test
    void anEmptySideLosesImmediately() {
        assertThat(CombatOdds.resolve(List.of(), List.of(FIGHTER)).attackerWins())
                .isZero();
        assertThat(CombatOdds.resolve(List.of(FIGHTER), List.of()).attackerWins())
                .isEqualTo(1.0);
    }
}
