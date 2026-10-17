package ti4.ai.sol;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The Sol rules are wired into the shared brain: they decide the turn, and they answer their own prompts.
class SolBrainTest extends BaseTi4Test {

    private final NekroBrain brain = new NekroBrain();
    private AiTestGame test;
    private Player sol;
    private Tile home;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        test.game.setRound(4);
        home = test.place("01", AiTestGame.HOME);
        sol.addPlanet("jord");
        test.units(home, "jord", sol, UnitType.Spacedock, 1);
        test.units(home, "jord", sol, UnitType.Infantry, 2);
        sol.setStrategicCC(3);
        sol.setTacticalCC(3);
        sol.setFleetCC(5);
        test.isActive(sol, "action");
    }

    private AiDecision decide(AiPrompt... prompts) {
        AiTurnContext context = test.contextFor(sol, Set.of(), NOW, prompts);
        return brain.decide(context);
    }

    private static AiPrompt turnMenu() {
        return prompt(
                "turn",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_sol_tacticalAction",
                "FFCC_sol_componentAction",
                "FFCC_sol_passForRound");
    }

    @Test
    void stallsWithOrbitalDropWhileItHasStrategyTokensToSpare() {
        assertThat(AiTestGame.pressedId(decide(turnMenu()))).isEqualTo("FFCC_sol_componentAction");

        AiPrompt menu = prompt(
                "menu",
                PromptSource.AI_THREAD,
                NOW + 100,
                "FFCC_sol_componentActionRes_ability_orbitalDrop",
                "deleteButtons");
        assertThat(AiTestGame.pressedId(decide(menu))).isEqualTo("FFCC_sol_componentActionRes_ability_orbitalDrop");
    }

    @Test
    void passesWhenTheStrategyTokensAreNeededElsewhere() {
        sol.setStrategicCC(1);

        assertThat(AiTestGame.pressedId(decide(turnMenu()))).isEqualTo("FFCC_sol_passForRound");
    }

    @Test
    void takesItsFreeInfantryWhenAGroundCombatOpensOnAPlanetItHolds() {
        test.isActive(test.nekro, "action");
        AiPrompt start = prompt(
                "start", PromptSource.COMBAT_THREAD, NOW, "FFCC_sol_utilizeSolCommander_jord", "combatRoll_301_jord");

        assertThat(AiTestGame.pressedId(decide(start))).isEqualTo("FFCC_sol_utilizeSolCommander_jord");
    }
}
