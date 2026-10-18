package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The strategy cards that protect home weigh the threat by what losing home would cost, not by whether any enemy ship
// could merely arrive.
class DefenseStrategyTest extends BaseTi4Test {

    private static final int DIPLOMACY = 2;
    private static final int CONSTRUCTION = 4;

    private AiTestGame test;
    private Tile staging;
    private Player sardakk;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(4);
        test.game.setVp(10);
        test.game.setStrategyCardSet("pok");
        Tile home = test.nekroHome();
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 1);
        sardakk = test.addSeat("200000000000000002", "sardakk", "red");
        sardakk.setFleetCC(5);
        test.place("13", "310");
        sardakk.addPlanet("trenlak");
        sardakk.addPlanet("quinarra");
        staging = test.place("46", AiTestGame.neighbourOf(AiTestGame.HOME));
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private CardPlan plan(int initiative) {
        return StrategyCardPlanner.plan(PlanInputs.of(test.game, test.nekro, PlanMoment.PICK), initiative);
    }

    private void loneCruiser() {
        test.units(staging, "space", sardakk, UnitType.Cruiser, 1);
        changed();
    }

    private void landingFleetWithAReasonToLand() {
        test.units(staging, "space", sardakk, UnitType.Carrier, 1);
        test.units(staging, "space", sardakk, UnitType.Cruiser, 2);
        test.units(staging, "space", sardakk, UnitType.Infantry, 4);
        test.game
                .getRevealedPublicObjectives()
                .put("conquer", test.game.getRevealedPublicObjectives().size() + 1);
        changed();
    }

    // A lone cruiser in reach cannot take the planet, so locking home is not worth the card. A landing party that wins
    // Mordai II and has a reason to take it makes the lock worth what home is at risk.
    @Test
    void diplomacyLocksHomeOnlyAgainstAThreatWorthWhatItWouldCostToLoseIt() {
        loneCruiser();
        assertThat(plan(DIPLOMACY).benefit(CardUse.DIPLOMACY_PROTECT)).isEmpty();

        landingFleetWithAReasonToLand();
        Benefit lock = plan(DIPLOMACY).benefit(CardUse.DIPLOMACY_PROTECT).orElseThrow();

        assertThat(lock.target()).isEqualTo(AiTestGame.HOME);
        assertThat(lock.value()).isGreaterThanOrEqualTo(CardValueScale.MIN_LOCK_EXPOSURE);
    }

    // Construction (the Prophecy of Kings card, whose primary only builds structures) hurries a PDS to a threatened
    // system. Home counts as threatened only when an enemy landing there is
    // a real risk, and a lone cruiser in reach is none.
    @Test
    void constructionHurriesAPdsHomeOnlyAgainstARealLandingThreat() {
        loneCruiser();
        assertThat(plan(CONSTRUCTION).benefit(CardUse.CONSTRUCTION_PDS).map(Benefit::target))
                .contains("");

        landingFleetWithAReasonToLand();

        assertThat(plan(CONSTRUCTION).benefit(CardUse.CONSTRUCTION_PDS).map(Benefit::target))
                .contains(AiTestGame.HOME);
    }
}
