package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.game.Tile;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Harrugh Gefhara (the Hacan hero) and Auto-Factories (the Hacan breakthrough), given to the AI seat. Mordai II gives
// the home dock production 6.
class HacanProductionTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        test.nekro.setFleetCC(5);
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "mordaiii", test.nekro, UnitType.Infantry, 5);
    }

    private void unlockHero() {
        test.nekro.addLeader("hacanhero");
        test.nekro.getLeader("hacanhero").orElseThrow().setLocked(false);
    }

    private DefenseOutlook defense() {
        return new DefenseOutlook(test.game, test.nekro);
    }

    // With the hero, cost is no limit: the plan fills all 6 production with units worth well over 10 resources
    // (the flagship and two mechs), and that is big enough to purge the hero for.
    @Test
    void plansAFullFreeBuildWorthPurgingTheHeroFor() {
        unlockHero();

        BuildPlan free = ProductionPlanner.freeBuild(test.game, test.nekro, home, defense())
                .orElseThrow();

        assertThat(free.units()).isEqualTo(Helper.getProductionValue(test.nekro, test.game, home, false));
        assertThat(free.totalCost()).isGreaterThanOrEqualTo(10);
        assertThat(free.units(UnitType.Flagship)).isEqualTo(1);
    }

    // The flagship is already out and the fleet pool has no room for another ship, so all that could be built is
    // mechs and infantry: far too little to spend the hero on.
    @Test
    void keepsTheHeroForABiggerBuild() {
        unlockHero();
        test.units(home, "space", test.nekro, UnitType.Flagship, 1);
        test.nekro.setFleetCC(3);

        assertThat(ProductionPlanner.freeBuild(test.game, test.nekro, home, defense()))
                .isEmpty();
    }

    @Test
    void plansNoFreeBuildWhileTheHeroIsLocked() {
        test.nekro.addLeader("hacanhero");

        assertThat(ProductionPlanner.freeBuild(test.game, test.nekro, home, defense()))
                .isEmpty();
    }

    // In the payment step of the free build the seat purges the hero instead of exhausting planets, then closes the
    // payment with nothing spent.
    @Test
    void purgesTheHeroInsteadOfPaying() {
        unlockHero();
        test.aiIsActive("action");
        test.game.setStoredValue("currentActionSummarynekro", " Activated 301 (Mordai II).");
        test.game.setActiveSystem(AiTestGame.HOME);
        test.memory.put(TacticalRules.FREE_BUILD_KEY + test.context().turnKey(), "planned");
        test.game.setStoredValue("producedUnitCostFornekro", "12");
        AiPrompt payment = prompt(
                "payment",
                PromptSource.PUBLIC,
                NOW + 10,
                List.of("spend_mordaiii_res", "purgeHacanHero", "deleteButtons_tacticalAction"),
                List.of("Mordai II", "Purge Hacan Hero", "Done Exhausting Planets"));

        assertThat(pressedId(TacticalRules.continueAction(test.context(payment)).orElseThrow()))
                .isEqualTo("purgeHacanHero");

        test.nekro.removeLeader("hacanhero");
        AiPrompt paid = prompt(
                "payment",
                PromptSource.PUBLIC,
                NOW + 10,
                List.of("spend_mordaiii_res", "deleteButtons_tacticalAction"),
                List.of("Mordai II", "Done Exhausting Planets"));
        assertThat(pressedId(TacticalRules.continueAction(test.context(paid)).orElseThrow()))
                .isEqualTo("deleteButtons_tacticalAction");
    }

    // The planned flagship is the only ship; with Auto-Factories two destroyers make it 3 non-fighter ships, which
    // gains a fleet token. Without the breakthrough the same dock builds no destroyers.
    @Test
    void topsUpToThreeShipsForTheAutoFactoriesFleetToken() {
        test.nekro.setTg(10);
        BuildPlan without = ProductionPlanner.plan(test.game, test.nekro, home);

        test.nekro.addBreakthrough("hacanbt");
        test.nekro.setBreakthroughUnlocked("hacanbt", true);
        BuildPlan with = ProductionPlanner.plan(test.game, test.nekro, home);

        assertThat(without.units(UnitType.Destroyer)).isZero();
        assertThat(with.units(UnitType.Destroyer)).isEqualTo(2);
        assertThat(with.units(UnitType.Flagship)
                        + with.units(UnitType.Destroyer)
                        + with.units(UnitType.Dreadnought)
                        + with.units(UnitType.Carrier))
                .isEqualTo(3);
    }
}
