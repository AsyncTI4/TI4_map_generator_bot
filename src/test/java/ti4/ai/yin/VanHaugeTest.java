package ti4.ai.yin;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.yin.YinTestBoard.SPACE;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.eval.CombatOdds;
import ti4.ai.eval.CombatOdds.Combatant;
import ti4.ai.eval.CombatOdds.Force;
import ti4.ai.eval.CombatOdds.Outcome;
import ti4.ai.tactical.CombatForecast;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The Van Hauge destroys every ship in its system when it is destroyed. Destroying the Yin fleet means destroying the
// flagship (the loss order keeps it last), so nobody can win a space combat against it: the best an attacker gets is
// both fleets gone.
class VanHaugeTest extends BaseTi4Test {

    private static Force dreadnoughts(int count) {
        return Force.of(java.util.Collections.nCopies(count, new Combatant(5, 1, true, 4, UnitType.Dreadnought)));
    }

    private static Force flagship() {
        return Force.of(List.of(new Combatant(9, 2, true, 8, UnitType.Flagship)));
    }

    @Test
    void anAttackerCannotWinAgainstADetonatingDefender() {
        Outcome plain = CombatOdds.resolve(dreadnoughts(4), flagship());
        Outcome bomb = CombatOdds.resolve(dreadnoughts(4), flagship().detonating());

        assertThat(plain.attackerWins()).isGreaterThan(0.9);
        assertThat(bomb.attackerWins()).isZero();
        assertThat(bomb.bothDestroyed()).isGreaterThan(0.9);
    }

    @Test
    void theAttackerLosesItsWholeFleetWhenTheFlagshipGoesDown() {
        CombatOdds.Detailed bomb =
                CombatOdds.resolveDetailed(dreadnoughts(2), flagship().detonating());

        assertThat(bomb.attackerCostLost()).isGreaterThan(7.5);
    }

    @Test
    void aYinFlagshipDetonatesButAnyOtherDoesNot() {
        YinTestBoard board = new YinTestBoard();
        assertThat(VanHauge.applyTo(board.yin, flagship()).detonates()).isTrue();
        assertThat(VanHauge.applyTo(board.sol, flagship()).detonates()).isFalse();
        assertThat(VanHauge.applyTo(board.yin, dreadnoughts(1)).detonates()).isFalse();
    }

    @Test
    void enemiesSeeNoWayToBeatTheVanHaugeInTheirPlans() {
        YinTestBoard board = new YinTestBoard();
        board.units(SPACE, board.sol, UnitType.Dreadnought, 4);
        board.units(SPACE, board.yin, UnitType.Flagship, 1);

        assertThat(CombatForecast.spaceOutlook(board.test.game, board.sol, board.position)
                        .orElseThrow()
                        .win())
                .isZero();
    }
}
