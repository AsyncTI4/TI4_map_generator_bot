package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

// A seat holding Strategize needs a strategy token to play it. When the Leadership purchase would otherwise stop at
// three tokens, it buys one more, which lands in the empty strategy pool.
class StrategizeLeadershipPurchaseTest extends BaseTi4Test {

    private static final int LEADERSHIP = 1;
    private static final int HAND_INDEX = 412;

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        test.aiIsActive("action");
        test.place("29", "201");
        test.place("24", "202");
        test.nekro.addPlanet("qucenn");
        test.nekro.addPlanet("rarron");
        test.nekro.addPlanet("meharxull");
        test.nekro.setTg(4);
        test.nekro.setStrategicCC(0);
        test.nekro.setTacticalCC(2);
        test.nekro.setFleetCC(3);
        test.sol.addSC(LEADERSHIP);
        test.game.setSCPlayed(LEADERSHIP, true);
        test.game.setActivePlayerID(test.sol.getUserID());
    }

    private static AiPrompt leadershipCard() {
        return prompt("leadership", PromptSource.PUBLIC, NOW, "sc_no_follow_1", "leadershipGenerateCCButtons");
    }

    private int tokensBoughtByFollowing() {
        StrategyCardRules.follow(test.context(leadershipCard()));
        String request = test.memory.get("tokenGain|" + test.game.getRound()).orElseThrow();
        return Integer.parseInt(request.substring(request.lastIndexOf('|') + 1));
    }

    @Test
    void followsLeadershipForAtMostThreeTokensByDefault() {
        assertThat(tokensBoughtByFollowing()).isEqualTo(TokenPurchase.MAX_TOKENS);
    }

    @Test
    void buysAFourthTokenForStrategizeWhenTheStrategyPoolIsEmpty() {
        test.nekro.setActionCard("strategize3", HAND_INDEX);

        assertThat(tokensBoughtByFollowing()).isEqualTo(TokenPurchase.MAX_TOKENS + 1);
    }

    @Test
    void buysNoExtraTokenOnceAStrategyTokenIsKept() {
        test.nekro.setActionCard("strategize3", HAND_INDEX);
        test.nekro.setStrategicCC(1);

        assertThat(tokensBoughtByFollowing()).isEqualTo(TokenPurchase.MAX_TOKENS);
    }

    @Test
    void buysNoExtraTokenForAStrategizeThatWasPlayedOrDiscarded() {
        test.nekro.setActionCard("strategize3", HAND_INDEX);
        test.nekro.getActionCards().remove("strategize3");

        assertThat(tokensBoughtByFollowing()).isEqualTo(TokenPurchase.MAX_TOKENS);
    }

    @Test
    void theExtraTokenIsBoundedByTheReinforcements() {
        test.nekro.setActionCard("strategize3", HAND_INDEX);
        test.nekro.setTacticalCC(test.nekro.getCommandTokenLimit() - 3 - 3);

        assertThat(StrategyCardRules.reinforcements(test.game, test.nekro)).isEqualTo(3);
        assertThat(tokensBoughtByFollowing()).isEqualTo(3);
    }
}
