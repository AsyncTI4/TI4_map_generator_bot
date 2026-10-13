package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.strategy.OpeningNeeds;
import ti4.ai.strategy.StrategyCardTurn;
import ti4.testUtils.BaseTi4Test;

// In round 1 a seat that wants Gravity Drive from the Technology secondary sends the holder a trade good to ask for
// Technology early. Deals cannot promise a strategy card, so an AI holder reads a no-strings trade good as that request
// and plays Technology on its next turn.
class TechnologyRequestTest extends BaseTi4Test {

    private static final int TECHNOLOGY = 7;

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        test.game.setRound(1);
        test.nekro.addSC(TECHNOLOGY);
    }

    @Test
    void readsAGiftWhileHoldingTechnologyAsARequestToPlayIt() {
        Deal gift = Deal.EMPTY.adjust("sol", "nekro", ItemType.TRADE_GOODS, 1);

        TechnologyRequest.noteGift(test.context(), test.sol, gift);

        assertThat(test.memory.has(OpeningNeeds.technologyRequestedKey(test.game)))
                .isTrue();
    }

    // A trade that asks for something back is a trade, not a request.
    @Test
    void doesNotReadATradeAsARequest() {
        Deal trade = Deal.EMPTY
                .adjust("sol", "nekro", ItemType.TRADE_GOODS, 1)
                .adjust("nekro", "sol", ItemType.COMMODITIES, 2);

        TechnologyRequest.noteGift(test.context(), test.sol, trade);

        assertThat(test.memory.has(OpeningNeeds.technologyRequestedKey(test.game)))
                .isFalse();
    }

    @Test
    void playsTechnologyOnceAskedTo() {
        test.aiIsActive("action");
        test.memory.put(OpeningNeeds.technologyRequestedKey(test.game), "sol");
        AiPrompt turn =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_nekro_tacticalAction", "FFCC_nekro_strategicAction_7");
        AiTurnContext context = test.contextFor(test.nekro, Set.of(), NOW, turn);

        assertThat(StrategyCardTurn.playIfDue(context, List.of(turn)).map(AiTestGame::pressedId))
                .contains("FFCC_nekro_strategicAction_7");
    }
}
