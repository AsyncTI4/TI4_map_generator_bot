package ti4.ai.sardakk;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
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

// The Sardakk rules are wired into the brain: the agent offer shows up in the public window and is answered at the
// end of the seat's own tactical action, and the breakthrough is claimed once the combat is won.
class SardakkBrainTest extends BaseTi4Test {

    private final NekroBrain brain = new NekroBrain();

    private AiTestGame test;
    private Player sardakk;
    private Tile tile;
    private String position;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        sardakk = test.addSeat(AiTestGame.SECOND_AI_ID, "sardakk", "orange");
        test.nekroHome();
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        tile = test.place("26", position);
        sardakk.addPlanet("lodor");
        test.units(tile, "lodor", sardakk, UnitType.Infantry, 2);
        test.isActive(sardakk, "action");
        test.game.setActiveSystem(position);
        sardakk.setTacticalCC(3);
        sardakk.setStrategicCC(2);
        sardakk.setFleetCC(3);
    }

    private Optional<AiDecision> decide(AiPrompt... prompts) {
        AiTurnContext context = test.contextFor(sardakk, Set.of(), NOW, prompts);
        return Optional.of(brain.decide(context));
    }

    // The agent offer is owned by the seat since the agent-module migration, so it needs no public window.
    @Test
    void theAgentOfferIsNoLongerAPublicWindow() {
        assertThat(brain.publicWindowHandlerPrefixes()).doesNotContain(TroAgentRules.USE_PREFIX);
    }

    @Test
    void usesTheAgentRightAfterTakingAPlanet() {
        sardakk.addLeader("sardakkagent");
        test.game.setStoredValue("planetsTakenThisRound", "lodor");
        AiPrompt offer = prompt(
                "agent",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_sardakk_exhaustAgent_sardakkagent_" + position + "_lodor",
                "deleteButtons");
        AiPrompt turn =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_sardakk_turnEnd", "FFCC_sardakk_doAnotherAction");

        assertThat(decide(turn, offer).map(AiTestGame::pressedId))
                .hasValue("FFCC_sardakk_exhaustAgent_sardakkagent_" + position + "_lodor");
    }

    @Test
    void endsTheTurnWhenTheAgentIsSpent() {
        sardakk.addLeader("sardakkagent");
        sardakk.getLeader("sardakkagent").orElseThrow().setExhausted(true);
        AiPrompt turn =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_sardakk_turnEnd", "FFCC_sardakk_doAnotherAction");
        AiPrompt offer = prompt(
                "agent", PromptSource.PUBLIC, NOW, "FFCC_sardakk_exhaustAgent_sardakkagent_" + position + "_lodor");

        assertThat(decide(turn, offer).map(AiTestGame::pressedId)).hasValue("FFCC_sardakk_turnEnd");
    }

    @Test
    void claimsTheBreakthroughAfterAWonCombatEvenOutsideItsOwnTurn() {
        sardakk.addBreakthrough("sardakkbt");
        sardakk.getBreakthroughUnlocked().put("sardakkbt", true);
        test.isActive(test.sol, "action");
        test.units(tile, "space", sardakk, UnitType.Cruiser, 2);
        test.game.setStoredValue("combatRoundTracker" + sardakk.getFaction() + position + "space", "1");
        AiPrompt reminder = prompt("combat", PromptSource.COMBAT_THREAD, NOW, "FFCC_sardakk_sardakkbtRes");

        assertThat(decide(reminder).map(AiTestGame::pressedId)).hasValue("FFCC_sardakk_sardakkbtRes");
    }
}
