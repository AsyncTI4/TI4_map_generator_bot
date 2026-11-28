package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.eval.BoardView;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Warfare's far reach lets a fleet move twice. For a Nekro carrier two systems from Mecatol Rex, reaching it this round
// is only worth what it adds to the custodians race: the seat must carry ground forces and be able to pay the six
// influence, and the gain is the race value of landing this round over landing when it would anyway. A rival that
// would get there first takes the point, so arriving earlier matters only when the race is contested. Self-play
// rollouts (seed 5004) showed Nekro picking Warfare in round 2 at 0 trade goods and about two influence on the
// strength of the whole custodians point, then trailing the table by four points two rounds later.
class WarfareFarReachTest extends BaseTi4Test {

    private static final int WARFARE = 6;
    private static final String MECATOL = "000";
    private static final String EMPTY_SYSTEM = "46";

    private AiTestGame test;
    private Tile staging;
    private Tile mecatol;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("strategy");
        mecatol = test.place("18", MECATOL);
        mecatol.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
        test.place("47", "101");
        staging = test.place("48", "201");
        test.units(staging, "space", test.nekro, UnitType.Carrier, 1);
    }

    @Test
    void valuesBeatingARivalToTheCustodians() {
        test.units(staging, "space", test.nekro, UnitType.Infantry, 2);
        test.nekro.setTg(8);
        rivalBesideMecatol();

        double fullPrize = CardValueScale.VICTORY_POINT
                + BoardView.planetValue(mecatol.getPlanetUnitHolders().getFirst());
        // Without far reach Sol lands first; with it the two arrive the same round, a coin flip at pick time.
        assertThat(farReachOnMecatol()).isPositive().isLessThan(CardValueScale.FAR_REACH_SHARE * fullPrize);
    }

    @Test
    void addsNothingWhenNoRivalCanReachMecatolFirst() {
        test.units(staging, "space", test.nekro, UnitType.Infantry, 2);
        test.nekro.setTg(8);

        assertThat(farReachOnMecatol()).isZero();
    }

    @Test
    void ignoresMecatolWhenTheSeatCannotPayTheCustodians() {
        test.units(staging, "space", test.nekro, UnitType.Infantry, 2);
        test.nekro.setTg(0);
        rivalBesideMecatol();

        assertThat(farReachOnMecatol()).isZero();
    }

    @Test
    void ignoresMecatolWhenTheFleetCarriesNoGroundForces() {
        test.nekro.setTg(8);
        rivalBesideMecatol();

        assertThat(farReachOnMecatol()).isZero();
    }

    private void rivalBesideMecatol() {
        Tile beside = test.place(EMPTY_SYSTEM, "104");
        test.units(beside, "space", test.sol, UnitType.Carrier, 1);
        test.units(beside, "space", test.sol, UnitType.Infantry, 2);
        test.sol.setTg(6);
    }

    private double farReachOnMecatol() {
        PlanInputs in = PlanInputs.of(test.game, test.nekro, PlanMoment.PICK);
        return WarfareUses.benefits(in, WARFARE).stream()
                .filter(benefit -> benefit.use() == CardUse.WARFARE_FAR_REACH)
                .filter(benefit -> MECATOL.equals(benefit.target()))
                .mapToDouble(Benefit::value)
                .max()
                .orElse(0);
    }
}
