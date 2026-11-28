package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.eval.FractureCampaign;
import ti4.ai.scoring.WinOdds;
import ti4.ai.scoring.WinRace;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Styx is a legendary planet, not a relic: its card A Song Like Marrow gains its holder 1 victory point when gained
// (docs/rules/thunders-edge.md). The win threat used to add that point whenever the rival had a space dock near an
// ingress. With WIN_THREAT_FEASIBLE it counts only at the chance the rival's fleet can actually take Styx this round.
class StyxWinThreatTest extends BaseTi4Test {

    private FractureScenario fracture;
    private Player sardakk;

    @BeforeEach
    void setUp() {
        fracture = new FractureScenario();
        sardakk = fracture.board.sardakk;
        fracture.board.test.game.setVp(10);
        fracture.board.test.game.setPhaseOfGame("action");
        fracture.board.test.game.scorePublicObjective(
                sardakk.getUserID(), fracture.board.test.game.addCustomPO("Test points", 9));
        sardakk.setTacticalCC(3);
        sardakk.setFleetCC(12);
    }

    private Standings standings() {
        return new DefenseOutlook(fracture.board.test.game, fracture.nekro()).standings();
    }

    private void sardakkFleetBesideStyx() {
        var beside = fracture.fracture("frac3");
        fracture.board.test.units(beside, "space", sardakk, UnitType.Dreadnought, 5);
        fracture.board.test.units(beside, "space", sardakk, UnitType.Carrier, 2);
        fracture.board.test.units(beside, "space", sardakk, UnitType.Infantry, 4);
    }

    // A space dock on the ingress system: the old feared-Styx condition.
    private void sardakkDockOnTheIngress() {
        sardakk.addPlanet("lodor");
        fracture.board.test.units(fracture.ingress, "lodor", sardakk, UnitType.Spacedock, 1);
    }

    @Test
    void aFleetThatCanTakeStyxThisRoundMakesTheRivalAThreat() {
        sardakkFleetBesideStyx();

        double take = StyxTake.thisRound(fracture.board.test.game, sardakk);
        assertThat(take).isGreaterThan(0.5);
        assertThat(WinOdds.reachesGoal(fracture.board.test.game, fracture.nekro(), sardakk))
                .isCloseTo(take, within(1e-9));
        // More likely than not to finish before Nekro, which has nothing to score: it wins first.
        assertThat(WinRace.winnersBefore(fracture.board.test.game, fracture.nekro()))
                .containsExactly(sardakk);
        assertThat(standings().winThreat(sardakk)).isEqualTo(1.0);
    }

    @Test
    void aDockNearTheFractureWithoutAFleetIsNoThreat() {
        sardakkDockOnTheIngress();

        assertThat(FractureCampaign.dock(fracture.board.test.game, sardakk)).isPresent();
        assertThat(StyxTake.thisRound(fracture.board.test.game, sardakk)).isZero();
        assertThat(standings().winThreat(sardakk)).isZero();
        assertThat(WinRace.canWinThisRound(fracture.board.test.game, fracture.nekro(), sardakk))
                .isFalse();
    }

    // With the experiment off, the dock alone still reads as a Styx point and a win this round.
    @Test
    void withTheExperimentDisabledTheDockStillCountsAsAStyxPoint() {
        sardakkDockOnTheIngress();
        AiExperiments.disable(fracture.nekro(), Set.of(Experiment.WIN_THREAT_FEASIBLE));

        assertThat(WinRace.canWinThisRound(fracture.board.test.game, fracture.nekro(), sardakk))
                .isTrue();
        assertThat(standings().winThreat(sardakk)).isCloseTo(1.0 / 3, within(1e-9));
    }

    // A rival already holding Styx has its point in its score; holding it adds nothing more.
    @Test
    void aHeldStyxIsNotCountedAgain() {
        sardakkFleetBesideStyx();
        sardakk.addPlanet("styx");

        assertThat(StyxTake.thisRound(fracture.board.test.game, sardakk)).isZero();
    }
}
