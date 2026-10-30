package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.CombatOdds.Detailed;
import ti4.ai.eval.CombatOdds.Force;
import ti4.ai.eval.CombatOdds.Outcome;

// resolveDetailed adds the expected cost each side loses to the win chances. These cases are deterministic: a
// combatant that hits on 1 always hits, one that hits on 11 never does.
class CombatOddsDetailedTest {

    private static final Combatant ALWAYS_HITS_CHEAP = new Combatant(1, 1, false, 2);
    private static final Combatant NEVER_HITS_DEAR = new Combatant(11, 1, false, 3);
    private static final Combatant SUSTAINING_KILLER = new Combatant(1, 1, true, 4);
    private static final Combatant CRUISER = new Combatant(7, 1, false, 2);
    private static final Combatant DREADNOUGHT = new Combatant(5, 1, true, 4);
    private static final Combatant FIGHTER = new Combatant(9, 1, false, 0.5);

    private static Detailed duel(List<Combatant> attacker, List<Combatant> defender) {
        return CombatOdds.resolveDetailed(Force.of(attacker), Force.of(defender));
    }

    @Test
    void anAlwaysHittingAttackerLosesNothingAndKillsTheDefender() {
        Detailed detailed = duel(List.of(ALWAYS_HITS_CHEAP), List.of(NEVER_HITS_DEAR));

        assertThat(detailed.outcome().attackerWins()).isCloseTo(1.0, within(1e-9));
        assertThat(detailed.attackerCostLost()).isZero();
        assertThat(detailed.attackerLosesUnit()).isZero();
        assertThat(detailed.defenderCostLost()).isCloseTo(3.0, within(1e-9));
        assertThat(detailed.defenderLosesUnit()).isCloseTo(1.0, within(1e-9));
        assertThat(detailed.attackerCostLostIfWin()).isZero();
    }

    @Test
    void aNeverHittingAttackerLosesEverythingAndKillsNothing() {
        Detailed detailed = duel(List.of(NEVER_HITS_DEAR), List.of(ALWAYS_HITS_CHEAP));

        assertThat(detailed.outcome().attackerWins()).isZero();
        assertThat(detailed.attackerCostLost()).isCloseTo(3.0, within(1e-9));
        assertThat(detailed.attackerLosesUnit()).isCloseTo(1.0, within(1e-9));
        assertThat(detailed.defenderCostLost()).isZero();
        assertThat(detailed.defenderLosesUnit()).isZero();
        assertThat(detailed.attackerCostLostIfWin()).isZero();
    }

    @Test
    void bothSidesLoseTheirUnitWhenBothAlwaysHit() {
        Detailed detailed = duel(List.of(ALWAYS_HITS_CHEAP), List.of(ALWAYS_HITS_CHEAP));

        assertThat(detailed.outcome().bothDestroyed()).isCloseTo(1.0, within(1e-9));
        assertThat(detailed.attackerCostLost()).isCloseTo(2.0, within(1e-9));
        assertThat(detailed.defenderCostLost()).isCloseTo(2.0, within(1e-9));
        assertThat(detailed.attackerCostLostIfWin()).isZero();
    }

    // The sustaining attacker absorbs the defender's one hit and kills it: the win costs nothing. The same duel with a
    // plain unit ends with both destroyed.
    @Test
    void aSustainAbsorbsTheFirstHit() {
        Detailed sustained = duel(List.of(SUSTAINING_KILLER), List.of(ALWAYS_HITS_CHEAP));
        Detailed plain = duel(List.of(new Combatant(1, 1, false, 4)), List.of(ALWAYS_HITS_CHEAP));

        assertThat(sustained.outcome().attackerWins()).isCloseTo(1.0, within(1e-9));
        assertThat(sustained.attackerCostLost()).isZero();
        assertThat(sustained.attackerLosesUnit()).isZero();
        assertThat(sustained.attackerCostLostIfWin()).isZero();
        assertThat(plain.outcome().bothDestroyed()).isCloseTo(1.0, within(1e-9));
        assertThat(plain.attackerCostLost()).isCloseTo(4.0, within(1e-9));
    }

    // Non-Euclidean Shielding adds a zero-cost combatant that never rolls. A hit that lands on it is a destroyed
    // combatant but not a lost unit.
    @Test
    void aZeroCostPhantomIsNeverALostUnit() {
        Combatant phantom = new Combatant(11, 0, false, 0);
        Combatant twoHitsDefender = new Combatant(1, 2, false, 2);
        Detailed detailed = duel(List.of(SUSTAINING_KILLER, phantom), List.of(twoHitsDefender));

        assertThat(detailed.outcome().attackerWins()).isCloseTo(1.0, within(1e-9));
        assertThat(detailed.attackerCostLost()).isZero();
        assertThat(detailed.attackerLosesUnit()).isZero();
        assertThat(detailed.defenderLosesUnit()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void anAttackerWithoutHitPointsLosesNothing() {
        Detailed detailed = duel(List.of(), List.of(CRUISER));

        assertThat(detailed.outcome().defenderWins()).isCloseTo(1.0, within(1e-9));
        assertThat(detailed.attackerCostLost()).isZero();
        assertThat(detailed.defenderCostLost()).isZero();
        assertThat(detailed.attackerCostLostIfWin()).isZero();
    }

    @Test
    void aDefenderWithoutHitPointsLosesNothing() {
        Detailed detailed = duel(List.of(CRUISER), List.of());

        assertThat(detailed.outcome().attackerWins()).isCloseTo(1.0, within(1e-9));
        assertThat(detailed.attackerCostLost()).isZero();
        assertThat(detailed.defenderCostLost()).isZero();
    }

    // One cruiser each: the attacker wins 0.375 of the time without ever losing its cruiser (a win means the
    // defender's cruiser died first), loses it otherwise, and the defender loses its cruiser whenever it does not win
    // outright.
    @Test
    void expectedLossesFollowTheClosedFormForASingleDuel() {
        Detailed detailed = duel(List.of(CRUISER), List.of(CRUISER));

        assertThat(detailed.outcome().attackerWins()).isCloseTo(0.375, within(1e-4));
        assertThat(detailed.attackerCostLostIfWin()).isZero();
        assertThat(detailed.attackerLosesUnit()).isCloseTo(0.625, within(1e-4));
        assertThat(detailed.attackerCostLost()).isCloseTo(2 * 0.625, within(1e-4));
        assertThat(detailed.defenderLosesUnit()).isCloseTo(0.625, within(1e-4));
    }

    @Test
    void aWinningAttackerWithSeveralUnitsOnlyCountsTheLossesOfTheWins() {
        Detailed detailed = duel(List.of(DREADNOUGHT, CRUISER, CRUISER), List.of(CRUISER, FIGHTER));

        assertThat(detailed.outcome().attackerWins()).isGreaterThan(0.5);
        assertThat(detailed.attackerCostLostIfWin()).isBetween(0.0, detailed.attackerCostLost() / 0.5);
        assertThat(detailed.attackerCostLost())
                .isGreaterThanOrEqualTo(
                        detailed.attackerCostLostIfWin() * detailed.outcome().attackerWins());
    }

    // The plain resolve is the same calculation with the losses left out.
    @Test
    void resolveGivesTheOutcomeOfResolveDetailed() {
        Force attacker = Force.of(List.of(DREADNOUGHT, CRUISER, FIGHTER));
        Force defender = Force.of(List.of(CRUISER, CRUISER, FIGHTER));

        Outcome plain = CombatOdds.resolve(attacker, defender);
        Outcome detailed = CombatOdds.resolveDetailed(attacker, defender).outcome();

        assertThat(detailed).isEqualTo(plain);
    }
}
