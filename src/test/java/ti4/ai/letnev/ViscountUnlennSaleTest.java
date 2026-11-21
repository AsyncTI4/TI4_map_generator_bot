package ti4.ai.letnev;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Letnev may sell Viscount Unlenn for 1 trade good to either side of somebody else's space combat when the fight is
// close, 40% to 60% for each side. Whoever buys first gets the die; if nobody buys, the combat goes on.
class ViscountUnlennSaleTest extends BaseTi4Test {

    private static final String SPACE = "space";

    private AiTestGame test;
    private Player letnev;
    private String position;
    private Tile tile;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        letnev = test.addSeat(AiTestGame.SECOND_AI_ID, "letnev", "green");
        letnev.addLeader("letnevagent");
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        tile = test.place("26", position);
        test.nekro.setTg(2);
        test.sol.setTg(2);
        test.isActive(test.nekro, "action");
        test.game.setActiveSystem(position);
    }

    private void closeFight() {
        test.units(tile, SPACE, test.nekro, UnitType.Dreadnought, 3);
        test.units(tile, SPACE, test.sol, UnitType.Dreadnought, 3);
    }

    private AiPrompt combatStart() {
        return prompt("start", PromptSource.COMBAT_THREAD, NOW, "combatRoll_" + position + "_space");
    }

    private AiTurnContext letnevContext() {
        return test.contextFor(letnev, Set.of(), NOW, combatStart());
    }

    private AiPrompt offerPrompt(AiDecision.Post post, long created) {
        List<String> ids = post.buttons().stream().map(Button::getCustomId).toList();
        return prompt("offer", PromptSource.COMBAT_THREAD, created, ids, List.of());
    }

    private AiDecision.Post offered() {
        return (AiDecision.Post) ViscountUnlennSale.offer(letnevContext()).orElseThrow();
    }

    private List<String> ids(AiDecision.Post post) {
        return post.buttons().stream().map(Button::getCustomId).toList();
    }

    @Test
    void offersTheAgentToBothSidesOfACloseFight() {
        closeFight();

        AiDecision.Post post = offered();

        assertThat(ids(post))
                .contains(
                        "FFCC_nekro_unlennBuy_" + position + "_0_nekro",
                        "FFCC_nekro_unlennNo_" + position + "_0_nekro",
                        "FFCC_sol_unlennBuy_" + position + "_0_sol",
                        "FFCC_sol_unlennNo_" + position + "_0_sol");
        assertThat(post.channelId()).isEqualTo("channel-start");
    }

    @Test
    void doesNotOfferAFightThatIsNotClose() {
        test.units(tile, SPACE, test.nekro, UnitType.Dreadnought, 6);
        test.units(tile, SPACE, test.sol, UnitType.Fighter, 1);

        assertThat(ViscountUnlennSale.offer(letnevContext())).isEmpty();
    }

    @Test
    void offersOnlyToThoseWhoCanPay() {
        closeFight();
        test.sol.setTg(0);

        assertThat(ids(offered())).allMatch(id -> id.startsWith("FFCC_nekro_"));
    }

    @Test
    void doesNotOfferWhenNobodyCanPay() {
        closeFight();
        test.nekro.setTg(0);
        test.sol.setTg(0);

        assertThat(ViscountUnlennSale.offer(letnevContext())).isEmpty();
    }

    @Test
    void offersOncePerSystemAndRound() {
        closeFight();
        AiTurnContext context = letnevContext();

        assertThat(ViscountUnlennSale.offer(context)).isPresent();
        assertThat(ViscountUnlennSale.offer(context)).isEmpty();
    }

    @Test
    void doesNotOfferOnceTheRoundIsUnderway() {
        closeFight();
        test.game.setStoredValue("combatRoundTracker" + "nekro" + position + SPACE, "1");

        assertThat(ViscountUnlennSale.offer(letnevContext())).isEmpty();
    }

    @Test
    void usesTheAgentItselfInsteadWhenItIsInTheFight() {
        test.units(tile, SPACE, letnev, UnitType.Dreadnought, 3);
        test.units(tile, SPACE, test.sol, UnitType.Dreadnought, 3);

        assertThat(ViscountUnlennSale.offer(letnevContext())).isEmpty();
    }

    @Test
    void cannotSellAnAgentThatIsExhausted() {
        closeFight();
        letnev.getLeader("letnevagent").orElseThrow().setExhausted(true);

        assertThat(ViscountUnlennSale.offer(letnevContext())).isEmpty();
    }

    @Test
    void aBuyerWithTradeGoodsTakesTheOffer() {
        closeFight();
        AiPrompt offer = offerPrompt(offered(), NOW);

        Optional<AiDecision> decision = ViscountUnlennSale.answer(test.contextFor(test.nekro, Set.of(), NOW, offer));

        assertThat(AiTestGame.pressedId(decision.orElseThrow()))
                .isEqualTo("FFCC_nekro_unlennBuy_" + position + "_0_nekro");
    }

    @Test
    void aBuyerWithoutTradeGoodsDeclines() {
        closeFight();
        AiPrompt offer = offerPrompt(offered(), NOW);
        test.nekro.setTg(0);

        Optional<AiDecision> decision = ViscountUnlennSale.answer(test.contextFor(test.nekro, Set.of(), NOW, offer));

        assertThat(AiTestGame.pressedId(decision.orElseThrow()))
                .isEqualTo("FFCC_nekro_unlennNo_" + position + "_0_nekro");
    }

    @Test
    void aBuyerDeclinesOnceTheRoundHasBeenRolled() {
        closeFight();
        AiPrompt offer = offerPrompt(offered(), NOW);
        test.game.setStoredValue("combatRoundTracker" + "nekro" + position + SPACE, "1");

        Optional<AiDecision> decision = ViscountUnlennSale.answer(test.contextFor(test.nekro, Set.of(), NOW, offer));

        assertThat(AiTestGame.pressedId(decision.orElseThrow())).contains("unlennNo_");
    }

    @Test
    void doesNotAnswerAnOfferItHasAlreadyAnswered() {
        closeFight();
        AiPrompt offer = offerPrompt(offered(), NOW);
        AiTurnContext first = test.contextFor(test.nekro, Set.of(), NOW, offer);
        AiDecision.Press press =
                (AiDecision.Press) ViscountUnlennSale.answer(first).orElseThrow();
        Set<String> pressed = Set.of(AiTurnContext.pressKey(press.prompt(), press.button()));

        AiTurnContext second = test.contextFor(test.nekro, pressed, NOW, offer);

        assertThat(ViscountUnlennSale.answer(second)).isEmpty();
    }

    @Test
    void theOtherSideHoldsItsRollWhileTheOfferIsOpen() {
        closeFight();
        test.sol.setTg(0);
        AiPrompt offer = offerPrompt(offered(), NOW);
        test.sol.setTg(2);

        Optional<AiDecision> hold =
                ViscountUnlennSale.holdRoll(test.contextFor(test.sol, Set.of(), NOW + 1_000L, offer));

        assertThat(hold).containsInstanceOf(AiDecision.Wait.class);
    }

    @Test
    void stopsHoldingTheRollWhenTheOfferGoesStale() {
        closeFight();
        AiPrompt offer = offerPrompt(offered(), NOW);
        long later = NOW + ViscountUnlennSale.PATIENCE_MILLIS + 1_000L;

        assertThat(ViscountUnlennSale.holdRoll(test.contextFor(test.sol, Set.of(), later, offer)))
                .isEmpty();
    }

    @Test
    void aSeatOutsideTheFightDoesNotHold() {
        closeFight();
        AiPrompt offer = offerPrompt(offered(), NOW);

        assertThat(ViscountUnlennSale.holdRoll(test.contextFor(letnev, Set.of(), NOW + 1_000L, offer)))
                .isEmpty();
    }

    @Test
    void aFairFightIsFortyToSixtyPercentForBothSides() {
        assertThat(ViscountUnlennSale.fairFight(0.45, 0.45)).isTrue();
        assertThat(ViscountUnlennSale.fairFight(0.4, 0.6)).isTrue();
        assertThat(ViscountUnlennSale.fairFight(0.7, 0.3)).isFalse();
        assertThat(ViscountUnlennSale.fairFight(0.61, 0.39)).isFalse();
    }
}
