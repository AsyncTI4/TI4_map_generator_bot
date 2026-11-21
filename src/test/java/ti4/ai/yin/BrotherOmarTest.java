package ti4.ai.yin;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;
import static ti4.ai.yin.YinTestBoard.PLANET;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.strategy.PrerequisiteSkips;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// Brother Omar satisfies one green prerequisite, and for a technology another player owns the Yin may return an
// infantry to ignore every prerequisite. Research planning stops exhausting a green specialty planet the commander
// covers, and prefers the infantry to a specialty planet; the seat then returns that infantry honestly.
class BrotherOmarTest extends BaseTi4Test {

    private static final String BIOTIC_PLANET = "newalbion";

    private YinTestBoard board;
    private Tile garden;

    @BeforeEach
    void setUp() {
        board = new YinTestBoard();
        garden = board.test.place("27", AiTestGame.HOME);
        board.yin.addPlanet(BIOTIC_PLANET);
    }

    @Test
    void withoutTheCommanderAGreenPrerequisiteCostsTheBioticPlanet() {
        PrerequisiteSkips.Plan plan = PrerequisiteSkips.of(board.test.game, board.yin, Mapper.getTech("dxa"));

        assertThat(plan.planets()).containsExactly(BIOTIC_PLANET);
    }

    @Test
    void theCommanderCoversOneGreenPrerequisite() {
        board.unlockCommander();

        PrerequisiteSkips.Plan plan = PrerequisiteSkips.of(board.test.game, board.yin, Mapper.getTech("dxa"));

        assertThat(plan.planets()).isEmpty();
        assertThat(plan.returnsInfantry()).isFalse();
    }

    @Test
    void anInfantryIgnoresThePrerequisitesOfATechnologyAnotherPlayerOwns() {
        board.unlockCommander();
        board.sol.addTech("hm");
        board.units(PLANET, board.yin, UnitType.Infantry, 1);

        PrerequisiteSkips.Plan plan = PrerequisiteSkips.of(board.test.game, board.yin, Mapper.getTech("hm"));

        assertThat(plan.returnsInfantry()).isTrue();
        assertThat(plan.planets()).isEmpty();
        assertThat(plan.valueLost()).isEqualTo(BrotherOmar.INFANTRY_VALUE);
    }

    @Test
    void noInfantryOnTheBoardMeansNoIgnoring() {
        board.unlockCommander();
        board.sol.addTech("hm");

        assertThat(PrerequisiteSkips.of(board.test.game, board.yin, Mapper.getTech("hm"))
                        .returnsInfantry())
                .isFalse();
    }

    @Test
    void returnsTheInfantryFromItsLargestGarrison() {
        board.unlockCommander();
        board.units(PLANET, board.yin, UnitType.Infantry, 3);
        board.test.units(garden, BIOTIC_PLANET, board.yin, UnitType.Infantry, 1);
        var research = board.context(NOW);
        BrotherOmarRules.expect(research);
        AiPrompt payment = prompt(
                "payment",
                PromptSource.AI_THREAD,
                NOW + 1000,
                List.of("spend_darien_inf", BrotherOmarRules.STEP_ONE, "deleteButtons_technology"),
                List.of("Darien", "Remove Infantry via Yin Commander", "Done Exhausting Planets"));

        assertThat(BrotherOmarRules.returnInfantry(board.context(NOW + 2000, payment))
                        .map(AiTestGame::pressedId))
                .contains(BrotherOmarRules.STEP_ONE);

        AiPrompt removal = AiTestGame.withContent(
                prompt(
                        "removal",
                        PromptSource.AI_THREAD,
                        NOW + 3000,
                        BrotherOmarRules.REMOVAL_PREFIX + garden.getPosition() + "_" + BIOTIC_PLANET,
                        BrotherOmarRules.REMOVAL_PREFIX + board.position + "_" + PLANET),
                board.yin.getRepresentationUnfogged() + " use buttons to remove 1 infantry");
        assertThat(BrotherOmarRules.returnInfantry(board.context(NOW + 4000, removal))
                        .map(AiTestGame::pressedId))
                .contains(BrotherOmarRules.REMOVAL_PREFIX + board.position + "_" + PLANET);
        assertThat(BrotherOmarRules.returnInfantry(board.context(NOW + 5000, removal)))
                .isEmpty();
    }

    @Test
    void touchesNothingUnlessItPlannedToUseTheCommander() {
        AiPrompt payment =
                prompt("payment", PromptSource.AI_THREAD, NOW, BrotherOmarRules.STEP_ONE, "deleteButtons_technology");

        assertThat(BrotherOmarRules.returnInfantry(board.context(NOW, payment))).isEmpty();
    }
}
