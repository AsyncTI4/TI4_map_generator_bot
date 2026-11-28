package ti4.ai.hacan;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.trade.TradeRules;
import ti4.testUtils.BaseTi4Test;

// The AI seat is given Carth of Golden Sands, the Hacan agent; the rules key on the component, not the faction.
class CarthAgentRulesTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekroHome();
        test.nekro.addLeader("hacanagent");
        test.nekro.setCommodities(0);
        test.sol.setCommodities(test.sol.getCommoditiesTotal());
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private AiPrompt turnPrompt() {
        return prompt(
                "turn",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_tacticalAction",
                "FFCC_nekro_exhaustAgent_hacanagent",
                "FFCC_nekro_passForRound");
    }

    private AiPrompt picker() {
        AiPrompt picker = prompt(
                "picker",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_agentPick_hacanagent_nekro",
                "FFCC_nekro_agentPick_hacanagent_sol");
        return AiTestGame.withContent(
                picker,
                test.nekro.getRepresentationUnfogged()
                        + ", please choose the faction on which you wish to use Carth of Golden Sands, the Hacan agent.");
    }

    // The agent readies every status phase and costs no action, so with room for 2 commodities and no Trade card
    // still to replenish them, the seat takes the commodities on itself: they wash into trade goods in a deal.
    @Test
    void gainsTwoCommoditiesWhenThereIsRoomForThem() {
        assertThat(pressed(CarthAgentRules.start(test.context(turnPrompt()))))
                .isEqualTo("FFCC_nekro_exhaustAgent_hacanagent");
        assertThat(pressed(CarthAgentRules.chooseTarget(test.context(picker()))))
                .isEqualTo("FFCC_nekro_agentPick_hacanagent_nekro");
    }

    // When a partner was picked at the start of the turn, the agent goes on that partner instead of on the seat.
    @Test
    void replenishesTheTargetItPickedAtTheStart() {
        test.sol.setCommodities(0);
        TradeRules.rememberCarthTarget(test.context(), test.sol);
        test.memory.put("hacanAgent|" + test.context().turnKey(), String.valueOf(NOW));

        assertThat(pressed(CarthAgentRules.chooseTarget(test.context(picker()))))
                .isEqualTo("FFCC_nekro_agentPick_hacanagent_sol");
    }

    // When the agent is pressed from the cards-info thread the faction picker comes back there, and a Yssaril copy
    // flags each target id; the seat still picks itself.
    @Test
    void picksTheTargetFromItsOwnThreadAndAYssarilCopy() {
        test.memory.put("hacanAgent|" + test.context().turnKey(), String.valueOf(NOW));
        AiPrompt hidden = AiTestGame.withContent(
                prompt("picker", PromptSource.AI_THREAD, NOW, "FFCC_nekro_agentPick_hacanagent_nekro~cc"),
                "Please choose the faction on which you wish to use Carth of Golden Sands, the Hacan agent.");

        assertThat(pressed(CarthAgentRules.chooseTarget(test.context(hidden))))
                .isEqualTo("FFCC_nekro_agentPick_hacanagent_nekro~cc");
    }

    // With its commodities already at their maximum the 2 commodities would be lost, so the agent stays ready.
    @Test
    void keepsTheAgentWhenItsCommoditiesAreFull() {
        test.nekro.setCommodities(test.nekro.getCommoditiesTotal());

        assertThat(CarthAgentRules.start(test.context(turnPrompt()))).isEmpty();
    }

    // An unplayed Trade card will replenish the seat's commodities anyway (primary, or the secondary Hacan follows
    // for free), so gaining 2 now would just be overwritten.
    @Test
    void waitsWhileTradeCanStillReplenishItsCommodities() {
        test.sol.addSC(5);

        assertThat(CarthAgentRules.start(test.context(turnPrompt()))).isEmpty();
    }

    // Once the agent has been used, it is not pressed a second time in the same turn.
    @Test
    void usesTheAgentOncePerTurn() {
        test.memory.put("hacanAgent|" + test.context().turnKey(), "done");

        assertThat(CarthAgentRules.start(test.context(turnPrompt()))).isEmpty();
    }
}
