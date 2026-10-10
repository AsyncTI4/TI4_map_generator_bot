package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class ChecksAndBalancesTest extends BaseTi4Test {

    private AiTestGame test;
    private Player hacan;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setStrategyCardsPerPlayer(1);
        hacan = test.addSeat("200000000000000002", "hacan", "yellow");
        test.game.scorePublicObjective(hacan.getUserID(), test.game.addCustomPO("Test points", 3));
    }

    private static List<PromptButton> allCards() {
        String[] ids = IntStream.rangeClosed(1, 8)
                .mapToObj(card -> "FFCC_nekro_scPick_" + card)
                .toArray(String[]::new);
        AiPrompt picks = prompt("picks", PromptSource.PUBLIC, NOW, ids);
        return picks.enabledButtons();
    }

    // While the player with the most points can still receive a card, the picked card is the one least useful to
    // them, and it goes to them.
    @Test
    void givesTheLeaderTheCardItCanUseLeast() {
        ChecksAndBalances.Plan plan =
                ChecksAndBalances.plan(test.game, test.nekro, allCards()).orElseThrow();

        assertThat(plan.recipientFaction()).isEqualTo("hacan");
        double given = StrategyCardRanking.value(test.game, hacan, StrategyCardRanking.initiative(plan.card()));
        assertThat(IntStream.rangeClosed(1, 8).mapToDouble(card -> StrategyCardRanking.value(test.game, hacan, card)))
                .allSatisfy(value -> assertThat(value).isGreaterThanOrEqualTo(given));
    }

    // Once the leader already holds a card, the picker instead gives the most useful card to the player with the
    // fewest points.
    @Test
    void givesTheTrailingPlayerAGoodCardWhenTheLeaderIsServed() {
        hacan.addSC(5);

        ChecksAndBalances.Plan plan =
                ChecksAndBalances.plan(test.game, test.nekro, allCards()).orElseThrow();

        assertThat(plan.recipientFaction()).isEqualTo("sol");
        double given = StrategyCardRanking.value(test.game, test.sol, StrategyCardRanking.initiative(plan.card()));
        assertThat(IntStream.rangeClosed(1, 8)
                        .mapToDouble(card -> StrategyCardRanking.value(test.game, test.sol, card)))
                .allSatisfy(value -> assertThat(value).isLessThanOrEqualTo(given));
    }

    // The hand-off follows the plan made when the card was picked.
    @Test
    void handsTheCardToThePlannedRecipient() {
        AiPrompt giveAway =
                prompt("give", PromptSource.PUBLIC, NOW, "checksNBalancesPt2_2_sol", "checksNBalancesPt2_2_hacan");

        assertThat(ChecksAndBalances.recipient(test.game, test.nekro, "hacan", giveAway.enabledButtons())
                        .map(PromptButton::customId))
                .contains("checksNBalancesPt2_2_hacan");
    }
}
