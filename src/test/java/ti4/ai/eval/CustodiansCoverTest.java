package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumSet;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.eval.CustodiansRace.Order;
import ti4.ai.eval.CustodiansRace.Snapshot;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Space cannon covering Mecatol Rex no longer switches the custodians race off: each seat's chance of landing is
// scaled by the chance its loaded fleet survives the cover's dice. A deciding seat with CUSTODIANS_COVER disabled keeps
// the old rule for every player, where any rival cover empties that player's arrival.
class CustodiansCoverTest extends BaseTi4Test {

    private static final String MECATOL = "000";
    private static final String NEKRO_START = "101";
    private static final String SOL_START = "104";
    private static final String NEKRO_CANNON = "102";
    private static final String SOL_CANNON = "103";
    private static final String EMPTY_SYSTEM = "46";
    private static final String WELLON_SYSTEM = "19";
    private static final String WELLON = "wellon";

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(2);
        Tile rex = test.place("18", MECATOL);
        rex.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
        test.game.setPhaseOfGame("action");
        test.nekro.setTacticalCC(3);
        test.sol.setTacticalCC(3);
        test.nekro.setTg(6);
        test.sol.setTg(6);
    }

    private void loadedCarrier(String position, Player owner, int cruisers) {
        Tile tile = test.place(EMPTY_SYSTEM, position);
        test.units(tile, "space", owner, UnitType.Carrier, 1);
        test.units(tile, "space", owner, UnitType.Infantry, 1);
        if (cruisers > 0) test.units(tile, "space", owner, UnitType.Cruiser, cruisers);
    }

    private void pdsTwoBesideMecatol(Player owner, String position) {
        Tile tile = test.place(WELLON_SYSTEM, position);
        owner.removeOwnedUnitByID("pds");
        owner.addOwnedUnitByID("pds2");
        test.units(tile, WELLON, owner, UnitType.Pds, 1);
    }

    private void disableCover(Player seat) {
        AiExperiments.disable(seat, EnumSet.of(Experiment.CUSTODIANS_COVER));
    }

    private Snapshot race() {
        return CustodiansRace.of(test.game, test.nekro);
    }

    // The old rule: a single rival PDS II beside Mecatol Rex empties the seat's arrival, so it never races.
    @Test
    void withTheExperimentDisabledOneRivalPdsTwoStillStopsTheRace() {
        loadedCarrier(NEKRO_START, test.nekro, 0);
        pdsTwoBesideMecatol(test.sol, SOL_CANNON);
        disableCover(test.nekro);

        assertThat(race().arrival(test.nekro)).isEmpty();
        assertThat(race().canLandThisRound(test.nekro)).isFalse();
        assertThat(race().coverSurvival(test.nekro)).isEqualTo(1.0);
    }

    // Sol's PDS II hits on 5: a lone carrier survives its one die four times in ten, and the race is worth that share
    // of the prize the seat would get uncontested.
    @Test
    void aLoneCarrierUnderOnePdsTwoRacesAtItsSurvivalChance() {
        loadedCarrier(NEKRO_START, test.nekro, 0);
        double uncovered = race().raceValue(test.nekro, race().arrival(test.nekro), Order.UNKNOWN);

        pdsTwoBesideMecatol(test.sol, SOL_CANNON);
        Snapshot race = race();
        OptionalInt arrival = race.arrival(test.nekro);

        assertThat(arrival).isPresent();
        assertThat(race.canLandThisRound(test.nekro)).isTrue();
        assertThat(race.coverSurvival(test.nekro)).isCloseTo(0.4, within(1e-9));
        assertThat(race.raceValue(test.nekro, arrival, Order.UNKNOWN)).isCloseTo(0.4 * uncovered, within(1e-9));
    }

    // An escorting cruiser soaks the single die, so the landing always gets through.
    @Test
    void anEscortSoaksTheSingleDie() {
        loadedCarrier(NEKRO_START, test.nekro, 1);
        pdsTwoBesideMecatol(test.sol, SOL_CANNON);

        assertThat(race().coverSurvival(test.nekro)).isEqualTo(1.0);
    }

    // A rival racing through the seat's own cover only beats it when its landing survives. Sol has Antimass
    // Deflectors, so the seat's PDS II hits it on 6. With the deciding seat's experiment disabled, Sol's arrival is
    // empty again under the old rule and poses no threat.
    @Test
    void aRivalUnderTheSeatsCoverThreatensOnlyByItsSurvivalChance() {
        loadedCarrier(SOL_START, test.sol, 0);
        pdsTwoBesideMecatol(test.nekro, NEKRO_CANNON);
        Snapshot race = race();
        int solArrival = race.arrival(test.sol).orElseThrow();
        double solSurvival = race.coverSurvival(test.sol);

        assertThat(solSurvival).isCloseTo(0.5, within(1e-9));
        assertThat(race.winChance(test.nekro, solArrival + 1, Order.UNKNOWN))
                .isCloseTo(1 - solSurvival * CustodiansRace.TAKE_FIRST, within(1e-9));

        disableCover(test.nekro);
        Snapshot oldRule = race();

        assertThat(oldRule.arrival(test.sol)).isEmpty();
        assertThat(oldRule.coverSurvival(test.sol)).isEqualTo(1.0);
        assertThat(oldRule.winChance(test.nekro, solArrival + 1, Order.UNKNOWN)).isEqualTo(1.0);
    }

    // Mixed A/B tables: a control seat (experiment off) must run the old brain even when the covered rival has the
    // experiment on. The flag is read from the deciding seat only, so the rival's own flag changes nothing.
    @Test
    void aControlSeatIgnoresTheRivalsFlagAndKeepsTheOldRule() {
        loadedCarrier(SOL_START, test.sol, 0);
        pdsTwoBesideMecatol(test.nekro, NEKRO_CANNON);
        disableCover(test.nekro);
        int solUncoveredArrival =
                CustodiansRace.of(test.game, test.sol).arrival(test.sol).orElseThrow();

        Snapshot control = race();

        assertThat(control.arrival(test.sol)).isEmpty();
        assertThat(control.canLandThisRound(test.sol)).isFalse();
        assertThat(control.winChance(test.nekro, solUncoveredArrival + 1, Order.UNKNOWN))
                .isEqualTo(1.0);
    }

    // The reverse: a treatment seat judges a control rival by the treatment rule, so the rival's covered arrival
    // still exists and is weighed by its survival.
    @Test
    void aTreatmentSeatJudgesAControlRivalByTheNewRule() {
        loadedCarrier(NEKRO_START, test.nekro, 0);
        pdsTwoBesideMecatol(test.sol, SOL_CANNON);
        disableCover(test.nekro);

        Snapshot treatment = CustodiansRace.of(test.game, test.sol);

        assertThat(treatment.arrival(test.nekro)).isPresent();
        assertThat(treatment.coverSurvival(test.nekro)).isCloseTo(0.4, within(1e-9));
    }
}
