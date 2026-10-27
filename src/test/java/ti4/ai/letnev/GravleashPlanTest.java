package ti4.ai.letnev;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.StrategyCard;
import ti4.ai.expedition.BreakthroughValue;
import ti4.ai.tactical.ProductionPlanner;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The Letnev breakthrough is a priority in every game, Gravity Drive follows it, a diverse fleet gets the larger
// combat bonus, and in rounds four and five the Imperial or Warfare card is the way to use the hero.
class GravleashPlanTest extends BaseTi4Test {

    private AiTestGame test;
    private Player letnev;
    private Tile home;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        letnev = test.nekro;
        letnev.setTg(10);
        test.game.setRound(4);
    }

    private void unlockBreakthrough() {
        letnev.addBreakthrough("letnevbt");
        letnev.setBreakthroughUnlocked("letnevbt", true);
    }

    private void unlockHero() {
        letnev.addLeader("letnevhero");
        letnev.getLeader("letnevhero").orElseThrow().setLocked(false);
    }

    @Test
    void aLockedLetnevBreakthroughIsWorthAtLeastTheHacanFloor() {
        Player seat = test.addSeat("200000000000000011", "letnev", "green");
        seat.addBreakthrough("letnevbt");

        assertThat(BreakthroughValue.isPriority(seat)).isTrue();
        assertThat(BreakthroughValue.of(test.game, seat)).isGreaterThanOrEqualTo(BreakthroughValue.PRIORITY_FLOOR);
    }

    @Test
    void gravityDriveIsWorthMoreOnceTheBreakthroughIsUnlocked() {
        assertThat(GravleashPlan.driveBonus(letnev, "gd")).isZero();

        unlockBreakthrough();

        assertThat(GravleashPlan.driveBonus(letnev, "gd")).isPositive();
        assertThat(GravleashPlan.driveBonus(letnev, "cv2")).isZero();
    }

    @Test
    void imperialAndWarfareAreWorthMoreToAHeroHolderInRoundsFourAndFive() {
        unlockHero();

        assertThat(GravleashPlan.cardBonus(test.game, letnev, StrategyCard.IMPERIAL))
                .isPositive();
        assertThat(GravleashPlan.cardBonus(test.game, letnev, StrategyCard.WARFARE))
                .isZero();
        unlockBreakthrough();
        assertThat(GravleashPlan.cardBonus(test.game, letnev, StrategyCard.WARFARE))
                .isPositive();
        assertThat(GravleashPlan.cardBonus(test.game, letnev, StrategyCard.TRADE))
                .isZero();
        test.game.setRound(3);
        assertThat(GravleashPlan.cardBonus(test.game, letnev, StrategyCard.IMPERIAL))
                .isZero();
    }

    private Set<UnitType> shipTypes(BuildPlan plan) {
        return plan.orders().stream()
                .map(ProductionPlanner.BuildOrder::type)
                .filter(type -> type != UnitType.Infantry && type != UnitType.Fighter && type != UnitType.Mech)
                .collect(Collectors.toSet());
    }

    @Test
    void theBreakthroughBuildsADiverseFleetForTheCombatBonus() {
        test.nekro.setFleetCC(6);
        test.units(home, "space", letnev, UnitType.Carrier, 1);
        int without = shipTypes(ProductionPlanner.plan(test.game, letnev, home)).size();

        unlockBreakthrough();
        BuildPlan with = ProductionPlanner.plan(test.game, letnev, home);

        assertThat(shipTypes(with).size()).isGreaterThanOrEqualTo(Math.max(2, without));
        assertThat(with.units(UnitType.Destroyer) + with.units(UnitType.Cruiser))
                .isPositive();
    }
}
