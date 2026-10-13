package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.strategy.OpeningOutlook.Boost;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.image.PositionMapper;
import ti4.testUtils.BaseTi4Test;

// A Hacan-like opening: two carriers at home, one planet system a move away and the rest of the slice two moves out,
// a blue technology for Gravity Drive but only 3 resources, one short of following Technology. The seat gets Gravity
// Drive in round 1 only by holding Technology, or by raising the missing trade good before Technology is played.
class GravityDriveOpeningTest extends BaseTi4Test {

    private static final String THIRD_ID = "7100000555555555";
    private static final int TRADE = 5;
    private static final int TECHNOLOGY = 7;

    private AiTestGame test;
    private Player hacan;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        test.game.setRound(1);
        hacan = test.addSeat(THIRD_ID, "hacan", "orange");
        Tile home = test.place("16", AiTestGame.HOME);
        for (String planet : List.of("arretze", "hercant", "kamdorn")) hacan.addPlanet(planet);
        test.units(home, "space", hacan, UnitType.Carrier, 2);
        test.units(home, "arretze", hacan, UnitType.Infantry, 4);
        hacan.setTacticalCC(3);
        hacan.setStrategicCC(2);
        String neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
        test.place("26", neighbour);
        test.place("27", beyond(neighbour));
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private double technologyValue(PlanMoment moment) {
        return StrategyCardPlanner.plan(PlanInputs.of(test.game, hacan, moment), TECHNOLOGY)
                .value();
    }

    // Three resources cannot pay the 4 for the secondary, so following earns no Gravity Drive reach. Picking
    // Technology itself does: the primary researches it for free.
    @Test
    void creditsGravityDriveReachWhenPickingTechnology() {
        assertThat(OpeningOutlook.gain(test.game, hacan, Boost.GRAVITY_DRIVE)).isZero();
        double holderReach = OpeningOutlook.gravityDriveGainAsTechnologyHolder(test.game, hacan);
        assertThat(holderReach).isGreaterThan(0);

        assertThat(technologyValue(PlanMoment.PICK)).isGreaterThan(technologyValue(PlanMoment.ACTION));
    }

    // Once the seat holds Technology the reach is credited either way, so the pick adds nothing on top.
    @Test
    void addsNothingOnceTheSeatHoldsTechnology() {
        hacan.addSC(TECHNOLOGY);
        changed();

        assertThat(technologyValue(PlanMoment.PICK)).isEqualTo(technologyValue(PlanMoment.ACTION));
    }

    // Commodities washed with an AI neighbour become trade goods on the seat's own turn, covering the missing resource.
    @Test
    void countsCommoditiesItCanWashWithANeighbour() {
        test.sol.addSC(TECHNOLOGY);
        hacan.setCommodities(2);
        test.sol.setCommodities(3);
        Tile next = test.place("25", otherNeighbour());
        test.units(next, "space", test.sol, UnitType.Carrier, 1);
        changed();

        assertThat(OpeningOutlook.gain(test.game, hacan, Boost.GRAVITY_DRIVE)).isGreaterThan(0);
        assertThat(OpeningNeeds.wantsTechnologySoon(test.game, hacan)).isTrue();
    }

    // A neighbouring AI still holds Trade: Hacan follows it for free (Masters of Trade) and the holder washes the
    // replenished commodities, so the secondary will be paid for once Trade comes round. Until then Hacan keeps a
    // carrier at home for the system two moves out, and is worth denying for a Technology holder.
    @Test
    void expectsTheRivalTradeWashAndKeepsACarrierHome() {
        test.sol.addSC(TECHNOLOGY);
        test.sol.addSC(TRADE);
        neighbourSol();

        assertThat(OpeningOutlook.gain(test.game, hacan, Boost.GRAVITY_DRIVE)).isGreaterThan(0);
        assertThat(OpeningHold.awaitsGravityDrive(test.game, hacan)).isTrue();
        assertThat(TechnologyTiming.deniable(test.game, hacan)).isTrue();
        TacticalPlan plan = TacticalPlanner.best(test.game, hacan, List.of()).orElseThrow();
        assertThat(carriersLeavingHome(plan)).isLessThan(2);
    }

    // With no Trade to come there is nothing to wait for: both carriers go, the second to press on next round.
    @Test
    void doesNotWaitWithoutAnExpectedWash() {
        test.sol.addSC(TECHNOLOGY);
        neighbourSol();

        assertThat(OpeningHold.awaitsGravityDrive(test.game, hacan)).isFalse();
        TacticalPlan plan = TacticalPlanner.best(test.game, hacan, List.of()).orElseThrow();
        assertThat(carriersLeavingHome(plan)).isEqualTo(2);
    }

    private void neighbourSol() {
        Tile next = test.place("25", otherNeighbour());
        test.units(next, "space", test.sol, UnitType.Carrier, 1);
        changed();
    }

    private static int carriersLeavingHome(TacticalPlan plan) {
        return plan.moves().stream()
                .filter(move -> AiTestGame.HOME.equals(move.origin()) && move.type() == UnitType.Carrier)
                .mapToInt(TacticalPlan.UnitMove::count)
                .sum();
    }

    // Commodities with no one to wash them with raise nothing.
    @Test
    void commoditiesWithoutAPartnerRaiseNothing() {
        test.sol.addSC(TECHNOLOGY);
        hacan.setCommodities(2);
        changed();

        assertThat(OpeningOutlook.gain(test.game, hacan, Boost.GRAVITY_DRIVE)).isZero();
    }

    // Holding Trade while another seat holds Technology: the primary's 3 trade goods pay for the secondary, so Trade is
    // played to open the round, before Technology comes round.
    @Test
    void tradeFundsTheTechnologySecondary() {
        test.sol.addSC(TECHNOLOGY);
        hacan.addSC(TRADE);
        changed();

        assertThat(OpeningOutlook.gain(test.game, hacan, Boost.GRAVITY_DRIVE)).isGreaterThan(0);
        CardPlan trade = StrategyCardPlanner.plan(PlanInputs.of(test.game, hacan, PlanMoment.ACTION), TRADE);
        assertThat(trade.benefit(CardUse.OPENING_EXPANSION).map(CardPlan.Benefit::value))
                .hasValueSatisfying(value -> assertThat(value).isGreaterThan(0));
        assertThat(CardTiming.of(PlanInputs.of(test.game, hacan, PlanMoment.ACTION), trade))
                .isEqualTo(CardTiming.Timing.NOW);
    }

    // Without a Technology holder to follow there is nothing for Trade to fund.
    @Test
    void tradeFundsNothingWithoutATechnologyHolder() {
        hacan.addSC(TRADE);
        changed();

        assertThat(OpeningOutlook.gravityDriveGainFromTrade(test.game, hacan, false))
                .isZero();
    }

    private static String otherNeighbour() {
        String neighbour = AiTestGame.neighbourOf(AiTestGame.HOME);
        return PositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position) && !position.equals(neighbour))
                .findFirst()
                .orElseThrow();
    }

    private static String beyond(String position) {
        List<String> nearHome = PositionMapper.getAdjacentTilePositions(AiTestGame.HOME);
        return PositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate ->
                        !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate) && !nearHome.contains(candidate))
                .findFirst()
                .orElseThrow();
    }
}
