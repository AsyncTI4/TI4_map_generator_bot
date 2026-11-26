package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.CombatOdds.Detailed;
import ti4.ai.eval.CombatOdds.Force;

// Combat odds depend only on the two forces, so the same fight is worked out once and shared by every seat and save.
class CombatOddsCacheTest {

    private static final Combatant CRUISER = new Combatant(7, 1, false, 2);
    private static final Combatant DREADNOUGHT = new Combatant(5, 1, true, 4);

    @BeforeEach
    @AfterEach
    void forget() {
        CombatOdds.forget();
    }

    @Test
    void theSameFightIsWorkedOutOnce() {
        Detailed first =
                CombatOdds.resolveDetailed(Force.of(List.of(CRUISER, CRUISER)), Force.of(List.of(DREADNOUGHT)));
        Detailed again =
                CombatOdds.resolveDetailed(Force.of(List.of(CRUISER, CRUISER)), Force.of(List.of(DREADNOUGHT)));

        assertThat(again).isSameAs(first);
        assertThat(CombatOdds.cachedCount()).isEqualTo(1);
    }

    @Test
    void aDifferentFightIsWorkedOutAgain() {
        Detailed two = CombatOdds.resolveDetailed(Force.of(List.of(CRUISER, CRUISER)), Force.of(List.of(DREADNOUGHT)));
        Detailed one = CombatOdds.resolveDetailed(Force.of(List.of(CRUISER)), Force.of(List.of(DREADNOUGHT)));

        assertThat(one.outcome().attackerWins()).isLessThan(two.outcome().attackerWins());
        assertThat(CombatOdds.cachedCount()).isEqualTo(2);
    }

    // A caller changing its list afterwards must not change the fight the cache remembers.
    @Test
    void aForceKeepsItsOwnCopyOfTheUnits() {
        List<Combatant> units = new ArrayList<>(List.of(CRUISER));
        Force force = Force.of(units);
        units.add(DREADNOUGHT);

        assertThat(force.units()).containsExactly(CRUISER);
    }
}
