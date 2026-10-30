package ti4.ai.xxcha;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// The engine offers Peace Accords as a public message with one green button per claimable planet, owned by the Xxcha
// seat, and a Decline button.
class PeaceAccordsRulesTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(1);
        test.aiIsActive("action");
        test.nekroHome();
        test.nekro.addAbility("peace_accords");
        String neighbour = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position))
                .findFirst()
                .orElseThrow();
        test.place("38", neighbour);
    }

    private static AiPrompt offer(long created, String... planets) {
        String[] ids = new String[planets.length + 1];
        for (int i = 0; i < planets.length; i++) ids[i] = "FFCC_nekro_peaceAccords_" + planets[i];
        ids[planets.length] = "deleteButtons";
        return prompt("peace-" + created, PromptSource.PUBLIC, created, ids);
    }

    private String pressed(AiPrompt... prompts) {
        return PeaceAccordsRules.resolve(test.context(prompts))
                .map(AiTestGame::pressedId)
                .orElse("");
    }

    @Test
    void claimsTheMostValuablePlanetOffered() {
        assertThat(pressed(offer(NOW, "fria", "abyz"))).isEqualTo("FFCC_nekro_peaceAccords_abyz");
    }

    @Test
    void claimsAnyPlanetItIsOfferedEvenAnUnimportantOne() {
        assertThat(pressed(offer(NOW, "fria"))).isEqualTo("FFCC_nekro_peaceAccords_fria");
    }

    @Test
    void declinesWhenNoPlanetOfferedCanStillBeClaimed() {
        assertThat(pressed(offer(NOW, "rarron"))).isEqualTo("deleteButtons");
    }

    @Test
    void ignoresAnOfferFromLongAgo() {
        assertThat(pressed(offer(NOW - 31 * 60_000L, "abyz"))).isEmpty();
    }

    @Test
    void ignoresAnOfferToAnotherSeat() {
        AiPrompt other = prompt("other", PromptSource.PUBLIC, NOW, "FFCC_sol_peaceAccords_abyz", "deleteButtons");

        assertThat(pressed(other)).isEmpty();
    }

    @Test
    void doesNotPressTheSameButtonTwice() {
        AiPrompt offer = offer(NOW, "abyz");
        String key = AiTurnContext.pressKey(offer, offer.buttons().getFirst());

        assertThat(PeaceAccordsRules.resolve(test.context(Set.of(key), offer))).isEmpty();
    }

    @Test
    void doesNothingForASeatWithoutTheAbility() {
        test.nekro.removeAbility("peace_accords");

        assertThat(pressed(offer(NOW, "abyz"))).isEmpty();
    }

    @Test
    void theBrainAnswersTheOffer() {
        assertThat(AiTestGame.pressedId(new NekroBrain().decide(test.context(offer(NOW, "fria", "abyz")))))
                .isEqualTo("FFCC_nekro_peaceAccords_abyz");
    }
}
