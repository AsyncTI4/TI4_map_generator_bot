package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.tactical.ProductionPlanner.BuildOrder;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.helpers.Units.UnitType;

// The Leadership purchase keeps what the scoring reserve, a build and the extra technology need. Batch 5 showed
// Xxcha dropping the whole build reserve whenever scoring plus the full build did not fit, so it spent everything on
// tokens and built nothing. Now the keep falls back to the largest affordable part of the build, as long as that
// part is still worth a tactical action; a token spent to activate home for 2 fighters is never reserved.
class TokenPurchaseBuildReserveTest {

    private static final SpendCost SCORING = SpendCost.influence(3);
    private static final Wallet.PlanetValue INFLUENCE_PLANET = new Wallet.PlanetValue("influence", 0, 3);

    private static Wallet wallet(int tradeGoods) {
        return new Wallet(List.of(INFLUENCE_PLANET), tradeGoods, 0, 0, 1);
    }

    private static BuildOrder dreadnought() {
        return new BuildOrder("dn", "home", UnitType.Dreadnought, 1, 4, 3.0, false, false);
    }

    private static BuildOrder carrier() {
        return new BuildOrder("cv", "home", UnitType.Carrier, 1, 3, 2.0, false, false);
    }

    private static BuildOrder infantry(int count) {
        return new BuildOrder(count + "gf", "home", UnitType.Infantry, count, count / 2.0, 0.25 * count, false, false);
    }

    private static BuildOrder fighters(int count) {
        return new BuildOrder(count + "ff", "home", UnitType.Fighter, count, count / 2.0, 0.25 * count, false, false);
    }

    private static BuildPlan homeBuild() {
        return new BuildPlan(List.of(dreadnought(), carrier(), infantry(4)));
    }

    @Test
    void keepsTheFullBuildAndResearchWhenBothFit() {
        SpendCost research = SpendCost.resources(4);

        SpendCost kept = TokenPurchase.keep(wallet(20), SCORING, List.of(homeBuild()), research);

        assertThat(kept).isEqualTo(SCORING.plus(SpendCost.resources(9)).plus(research));
    }

    @Test
    void prefersTheFullBuildOverResearchWhenOnlyOneFits() {
        SpendCost kept = TokenPurchase.keep(wallet(10), SCORING, List.of(homeBuild()), SpendCost.resources(4));

        assertThat(kept).isEqualTo(SCORING.plus(SpendCost.resources(9)));
    }

    @Test
    void keepsTheLargestAffordablePartWhenScoringPlusTheFullBuildDoesNotFit() {
        // 6 trade goods after the influence planet pays scoring: the dreadnought (4) and 4 infantry (2) fit, the
        // carrier (3) does not. The old reserve dropped to scoring alone here.
        SpendCost kept = TokenPurchase.keep(wallet(6), SCORING, List.of(homeBuild()), SpendCost.NONE);

        assertThat(kept).isEqualTo(SCORING.plus(SpendCost.resources(6)));
    }

    @Test
    void prefersAPartialBuildOverResearch() {
        SpendCost kept = TokenPurchase.keep(wallet(6), SCORING, List.of(homeBuild()), SpendCost.resources(4));

        assertThat(kept).isEqualTo(SCORING.plus(SpendCost.resources(6)));
    }

    @Test
    void doesNotReserveATrivialTwoFighterHomeBuild() {
        BuildPlan twoFighters = new BuildPlan(List.of(fighters(2)));

        assertThat(TokenPurchase.worthAnActivation(twoFighters)).isFalse();
        assertThat(TokenPurchase.keep(wallet(10), SCORING, List.of(twoFighters), SpendCost.NONE))
                .isEqualTo(SCORING);
        assertThat(TokenPurchase.keep(wallet(10), SCORING, List.of(twoFighters), SpendCost.resources(4)))
                .isEqualTo(SCORING.plus(SpendCost.resources(4)));
    }

    @Test
    void doesNotReserveAPartThatShrinksToATrivialBuild() {
        // Only 4 trade goods: the dreadnought alone fits (1 unit, 4 resources), not worth a token to activate home.
        BuildPlan plan = new BuildPlan(List.of(dreadnought(), carrier(), infantry(4)));

        SpendCost kept = TokenPurchase.keep(wallet(4), SCORING, List.of(plan), SpendCost.resources(2));

        assertThat(kept).isEqualTo(SCORING.plus(SpendCost.resources(2)));
    }

    @Test
    void aSmallScoringBuildIsWorthAnActivation() {
        BuildOrder scoringCarrier = new BuildOrder("cv", "home", UnitType.Carrier, 1, 3, 2.0, true, false);

        assertThat(TokenPurchase.worthAnActivation(new BuildPlan(List.of(scoringCarrier))))
                .isTrue();
    }

    @Test
    void aSmallDefensiveBuildIsWorthAnActivation() {
        BuildOrder guard = new BuildOrder("dd", "home", UnitType.Destroyer, 1, 1, 1.0, false, true);

        assertThat(TokenPurchase.worthAnActivation(new BuildPlan(List.of(guard))))
                .isTrue();
    }

    @Test
    void anEmptyPlanIsNotWorthAnActivation() {
        assertThat(TokenPurchase.worthAnActivation(new BuildPlan(List.of()))).isFalse();
    }

    @Test
    void affordablePartSkipsAnOrderThatDoesNotFitAndKeepsLaterOnes() {
        BuildPlan part = TokenPurchase.affordablePart(homeBuild(), 6);

        assertThat(part.orders()).containsExactly(dreadnought(), infantry(4));
        assertThat(part.totalCost()).isEqualTo(6);
    }

    @Test
    void picksTheMostValuableAffordablePartAcrossDocks() {
        // A forward dock with a cheaper but richer build beats the home dock's leftover when only 5 trade goods fit.
        BuildPlan home = new BuildPlan(List.of(infantry(4), infantry(4)));
        BuildPlan forward = new BuildPlan(List.of(
                new BuildOrder("2ca", "forward", UnitType.Cruiser, 2, 4, 5.0, false, false), infantry(2), infantry(2)));

        SpendCost kept = TokenPurchase.keep(wallet(5), SCORING, List.of(home, forward), SpendCost.NONE);

        // Forward part: cruisers (4) + 2 infantry (1) = 5 resources, 4 units, value 5.5. Home fits whole: 8 infantry
        // for 4 resources, value 2. The forward part wins.
        assertThat(kept).isEqualTo(SCORING.plus(SpendCost.resources(5)));
    }

    @Test
    void aSmallerDockThatFitsWholeIsKeptWhenTheLargestDoesNot() {
        BuildPlan home = new BuildPlan(List.of(infantry(4), infantry(4)));

        SpendCost kept = TokenPurchase.keep(wallet(4), SCORING, List.of(homeBuild(), home), SpendCost.NONE);

        // homeBuild (9) does not fit; its 4-resource part is a lone dreadnought, not worth an activation. The
        // 8 infantry fit whole.
        assertThat(kept).isEqualTo(SCORING.plus(SpendCost.resources(4)));
    }
}
