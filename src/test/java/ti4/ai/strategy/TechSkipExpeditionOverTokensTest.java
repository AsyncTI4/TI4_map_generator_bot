package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.expedition.ExpeditionKind;
import ti4.ai.expedition.ExpeditionOutlook;
import ti4.ai.expedition.ExpeditionRules;
import ti4.ai.expedition.ExpeditionUnlock;
import ti4.ai.expedition.Holdings;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Round 1 of the live game aiplay1: Sardakk played Leadership with Meer (0/4, a warfare specialty) and Arinam (1/2)
// as its only ready planets, and spent both on 2 extra command tokens. Its breakthrough was still locked and the
// technology specialty slice was the last open expedition, held one apiece by the other five factions: claiming it
// would have unlocked the breakthrough and made Sardakk the Thunder's Edge holder. Leadership's 3 free tokens were
// coming anyway, so the 2 bought ones were the cheaper thing to give up.
class TechSkipExpeditionOverTokensTest extends BaseTi4Test {

    private static final int LEADERSHIP = 1;
    private static final int FREE_TOKENS = StrategyCardRules.LEADERSHIP_TOKENS;

    private AiTestGame test;
    private Player sardakk;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(1);
        test.aiIsActive("action");
        sardakk = test.nekro;
        sardakk.setFaction(test.game, "sardakk");
        sardakk.addBreakthrough("sardakkbt");
        test.place("37", "201");
        sardakk.addPlanet("meer");
        sardakk.addPlanet("arinam");
        sardakk.setTacticalCC(0);
        sardakk.setFleetCC(3);
        sardakk.setStrategicCC(0);
        sardakk.addSC(LEADERSHIP);
        claimedByOthers(ExpeditionKind.TRADE_GOODS, "sol");
        claimedByOthers(ExpeditionKind.FIVE_RES, "letnev");
        claimedByOthers(ExpeditionKind.FIVE_INF, "xxcha");
        claimedByOthers(ExpeditionKind.SECRET, "nekro");
        claimedByOthers(ExpeditionKind.ACTION_CARDS, "hacan");
    }

    private void claimedByOthers(ExpeditionKind kind, String faction) {
        test.game.getExpeditions().getExpeditionFactions().put(kind.id(), faction);
    }

    private int tokensBoughtWithThePrimary() {
        int reinforcements = StrategyCardRules.reinforcements(test.game, sardakk);
        return TokenPurchase.best(test.game, sardakk, reinforcements - FREE_TOKENS)
                .map(TokenPurchase.Purchase::tokens)
                .orElse(0);
    }

    private static AiPrompt leadershipCard() {
        return prompt("leadership", PromptSource.PUBLIC, NOW, "sc_no_follow_1", "leadershipGenerateCCButtons");
    }

    @Test
    void theLastSliceIsWorthTheBreakthroughAndThundersEdge() {
        assertThat(ExpeditionOutlook.best(
                        test.game, sardakk, Holdings.of(test.game, sardakk).atPassing()))
                .hasValueSatisfying(choice -> {
                    assertThat(choice.kind()).isEqualTo(ExpeditionKind.TECH_SKIP);
                    assertThat(choice.payment().forResources()).containsExactly("meer");
                    assertThat(choice.breakthrough()).isPositive();
                    assertThat(choice.thundersEdge()).isGreaterThan(4);
                });
    }

    @Test
    void leadershipKeepsMeerForTheExpeditionInsteadOfBuyingTokens() {
        assertThat(tokensBoughtWithThePrimary()).isZero();
        assertThat(ExpeditionUnlock.plannedPlanets(test.game, sardakk)).containsExactly("meer");
    }

    // Through the real Leadership primary: the 3 free tokens, none bought, so Meer is still ready for the slice.
    @Test
    void theLeadershipPrimaryTakesOnlyTheFreeTokens() {
        AiPrompt card = leadershipCard();
        StrategyCardRules.play(test.context(card), card, card.buttons().getFirst(), LEADERSHIP);

        StrategyCardRules.resolvePrimary(test.context(card));

        assertThat(test.memory.get("tokenGain|" + test.game.getRound()))
                .hasValueSatisfying(value -> assertThat(value).endsWith("|" + FREE_TOKENS + "|0"));
        assertThat(sardakk.getReadiedPlanets()).contains("meer");
    }

    // Once the breakthrough is unlocked and the expedition is far from finished, the slice adds little and the spare
    // influence buys tokens as before.
    @Test
    void buysTheTokensWhenTheSliceNoLongerUnlocksMuch() {
        sardakk.setBreakthroughUnlocked("sardakkbt", true);
        test.game.getExpeditions().getExpeditionFactions().replaceAll((kind, faction) -> null);

        assertThat(tokensBoughtWithThePrimary()).isEqualTo(2);
    }

    // With both planets spent, Diplomacy readies Meer for the slice, and Meer's influence is not counted as Leadership
    // tokens because the slice claims it.
    @Test
    void diplomacyReadiesTheSpecialtyPlanetForTheExpedition() {
        sardakk.exhaustPlanet("meer");
        sardakk.exhaustPlanet("arinam");

        assertThat(DiplomacyUses.readyingChoice(test.game, sardakk))
                .hasValueSatisfying(choice -> assertThat(choice.planets()).contains("meer"));
        assertThat(DiplomacyUse.eachAlone(test.game, sardakk, List.of("meer")))
                .singleElement()
                .satisfies(choice -> assertThat(choice.use()).isZero());
    }

    // Live game aiplay1, the turn after Leadership: Meer was still ready, but the end-of-turn check judged the claim
    // cheaper at the pass (Meer's influence spent at 0.1 instead of 2/3) and, with no rival holding a ready technology
    // specialty planet, saw no risk in waiting. The seat ended its turn with the slice open. A rival only needs a
    // readied or newly ready specialty planet to take the last slice, Thunder's Edge and the breakthrough with it,
    // which is worth far more than the discount, so the last slice is claimed at the end of the turn.
    @Test
    void theLastSliceIsClaimedAtTheEndOfTheTurnInsteadOfWaitingToPass() {
        assertThat(ExpeditionOutlook.claimNow(test.game, sardakk))
                .hasValueSatisfying(choice -> assertThat(choice.kind()).isEqualTo(ExpeditionKind.TECH_SKIP));
    }

    @Test
    void theEndOfTurnMenusLeadToTheExpeditionInsteadOfEndingTheTurn() {
        AiPrompt end = prompt("end", PromptSource.PUBLIC, NOW, "FFCC_sardakk_endOfTurnAbilities");
        assertThat(ExpeditionRules.endOfTurn(test.context(end), List.of(end)))
                .hasValueSatisfying(
                        decision -> assertThat(pressedId(decision)).isEqualTo("FFCC_sardakk_endOfTurnAbilities"));

        AiPrompt menu = prompt(
                "abilities",
                PromptSource.PUBLIC,
                NOW + 1,
                "FFCC_sardakk_turnEnd",
                "FFCC_sardakk_expeditionInfoAndButtons");
        assertThat(ExpeditionRules.endOfTurn(test.context(menu), List.of(menu)))
                .hasValueSatisfying(
                        decision -> assertThat(pressedId(decision)).isEqualTo("FFCC_sardakk_expeditionInfoAndButtons"));

        AiPrompt slices =
                prompt("choices", PromptSource.PUBLIC, NOW + 2, "FFCC_sardakk_TEexpedition_techSkip", "deleteButtons");
        assertThat(ExpeditionRules.next(test.context(slices)))
                .hasValueSatisfying(
                        decision -> assertThat(pressedId(decision)).isEqualTo("FFCC_sardakk_TEexpedition_techSkip"));
    }

    // Once a slice other than the last is open, the seat still waits for the cheap pass when no rival could take it.
    @Test
    void anEarlierSliceStillWaitsForTheCheapPass() {
        test.game.getExpeditions().getExpeditionFactions().put(ExpeditionKind.ACTION_CARDS.id(), null);

        assertThat(ExpeditionOutlook.claimNow(test.game, sardakk)).isEmpty();
    }

    // Live game aiplay1: with no action worth a token left, the seat passes. The pass menu carries the expedition
    // button, and on the passing turn the slice is priced at passing (its planets would go unused anyway), so even an
    // earlier slice that waits at the end of an ordinary turn is claimed there.
    @Test
    void thePassMenuClaimsTheSliceAtThePassingPrice() {
        test.game.getExpeditions().getExpeditionFactions().put(ExpeditionKind.ACTION_CARDS.id(), null);
        AiPrompt passMenu = prompt(
                "passing",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_sardakk_passForRound",
                "FFCC_sardakk_expeditionInfoAndButtons");

        assertThat(ExpeditionRules.endOfTurn(test.context(passMenu), List.of(passMenu)))
                .hasValueSatisfying(
                        decision -> assertThat(pressedId(decision)).isEqualTo("FFCC_sardakk_expeditionInfoAndButtons"));
    }
}
