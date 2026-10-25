package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.StrategyCard;
import ti4.ai.scoring.SpendCost;
import ti4.ai.strategy.OpeningHold;
import ti4.ai.strategy.StrategyCardRules;
import ti4.ai.tactical.ProductionPlanner;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// A Hacan-like seat waiting for Gravity Drive from a rival's Technology keeps what the secondary costs: its home
// resources and trade good are not spent on building at home or given away in deals, and its strategy token is kept
// for Technology rather than spent on another secondary.
class GravityDriveReserveTest extends BaseTi4Test {

    private static final String THIRD_ID = "7100000555555555";
    private static final int TECHNOLOGY = 7;
    private static final int TRADE = 5;
    private static final int TRADE_PRIMARY = 3;
    private static final int FOLLOW_COST = 4;
    // Sarween Tools takes 1 off a build, so a build costing 1 spends nothing.
    private static final double SARWEEN_DISCOUNT = 1;

    private AiTestGame test;
    private Player hacan;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        test.game.setRound(1);
        test.game.setPhaseOfGame("action");
        hacan = test.addSeat(THIRD_ID, "hacan", "orange");
        Tile home = test.place("16", AiTestGame.HOME);
        for (String planet : List.of("arretze", "hercant", "kamdorn")) hacan.addPlanet(planet);
        test.units(home, "space", hacan, UnitType.Carrier, 2);
        test.units(home, "arretze", hacan, UnitType.Infantry, 4);
        test.units(home, "arretze", hacan, UnitType.Spacedock, 1);
        hacan.setTacticalCC(3);
        hacan.setStrategicCC(2);
        hacan.setTg(1);
        String neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("26", neighbour);
        test.place("27", beyond(neighbour));
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    @Test
    void keepsTheSecondarysCostWhileWaiting() {
        test.sol.addSC(TECHNOLOGY);
        changed();

        assertThat(OpeningHold.awaitsGravityDrive(test.game, hacan)).isTrue();
        assertThat(ProductionPlanner.reserve(test.game, hacan).resources()).isEqualTo(FOLLOW_COST);
        assertThat(TradeBudget.freeTradeGoods(test.game, hacan)).isZero();
        assertThat(TradeBudget.spareTradeGoods(test.game, hacan, test.sol)).isZero();
        assertThat(ProductionPlanner.plan(test.game, hacan, home()).totalCost()).isLessThanOrEqualTo(SARWEEN_DISCOUNT);
    }

    // With one strategy token left it is kept for Technology, not spent on Diplomacy.
    @Test
    void keepsTheStrategyTokenForTechnology() {
        test.sol.addSC(TECHNOLOGY);
        hacan.setStrategicCC(1);
        changed();

        assertThat(OpeningHold.awaitsGravityDrive(test.game, hacan)).isTrue();
        assertThat(StrategyCardRules.hasSpareStrategyToken(test.game, hacan, StrategyCard.DIPLOMACY))
                .isFalse();
    }

    // Holding Trade, the seat expects 3 trade goods from the primary (and 6 washed commodities). Negotiate Trade Routes
    // (5 trade goods) counts on those 3 and more, so they are not counted again against the Gravity Drive follow: the
    // full 4 resources stay reserved for it instead of 4 - 3 = 1.
    @Test
    void tradeGoodsASpendObjectiveCountsOnAreNotCountedAgainForTheFollow() {
        test.sol.addSC(TECHNOLOGY);
        hacan.addSC(TRADE);
        changed();
        assertThat(OpeningHold.awaitsGravityDrive(test.game, hacan)).isTrue();
        assertThat(OpeningHold.gravityDriveReserve(test.game, hacan).resources())
                .isEqualTo(FOLLOW_COST - TRADE_PRIMARY);

        test.game.getRevealedPublicObjectives().put("trade_routes", 1);
        changed();

        assertThat(ti4.ai.scoring.ScoringReserve.expectedTradeGoodsCounted(test.game, hacan))
                .isEqualTo(5);
        assertThat(ProductionPlanner.reserve(test.game, hacan).resources()).isEqualTo(FOLLOW_COST);
    }

    // Amass Wealth's reserve and the Gravity Drive follow both stay reserved when the planets and trade goods cover the
    // two together.
    @Test
    void keepsBothReservesWhenTheyFit() {
        test.sol.addSC(TECHNOLOGY);
        hacan.addPlanet("lodor");
        hacan.setTg(8);
        test.game.getRevealedPublicObjectives().put("amass_wealth", 1);
        changed();

        assertThat(OpeningHold.awaitsGravityDrive(test.game, hacan)).isTrue();
        assertThat(ProductionPlanner.reserve(test.game, hacan)).isEqualTo(new SpendCost(7, 3, 3, 0));
    }

    // Nobody holds Technology: there is nothing to wait for, and the same seat may spend freely.
    @Test
    void spendsFreelyWithNothingToWaitFor() {
        assertThat(OpeningHold.awaitsGravityDrive(test.game, hacan)).isFalse();
        assertThat(ProductionPlanner.reserve(test.game, hacan).resources()).isZero();
        assertThat(TradeBudget.freeTradeGoods(test.game, hacan)).isEqualTo(1);
        assertThat(ProductionPlanner.plan(test.game, hacan, home()).totalCost()).isGreaterThan(SARWEEN_DISCOUNT);
    }

    private Tile home() {
        return test.game.getTileByPosition(AiTestGame.HOME);
    }

    private static String beyond(String position) {
        List<String> nearHome = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        return MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate ->
                        !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate) && !nearHome.contains(candidate))
                .findFirst()
                .orElseThrow();
    }
}
