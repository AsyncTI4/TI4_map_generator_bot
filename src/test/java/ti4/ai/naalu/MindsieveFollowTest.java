package ti4.ai.naalu;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.StrategyCard;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.strategy.StrategyCardRules;
import ti4.testUtils.BaseTi4Test;

// Mindsieve: when the bot serves another player's strategy card, a Naalu holder with the breakthrough gets one
// "Send <note>" button per promissory note in hand, in its private thread. The seat (the test game's "nekro" seat,
// given the breakthrough) follows Sol's Construction with a note instead of a strategy token when the cheapest note
// it may give costs no more than the token it saves.
class MindsieveFollowTest extends BaseTi4Test {

    private static final int CONSTRUCTION = 4;
    private static final String GIFT = "mindsieveFollow_4_gift";
    private static final String AGREEMENT = "mindsieveFollow_4_black_ta";
    private static final String DOCK_BUTTON = "construction_spacedock";

    private AiTestGame test;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        test.aiIsActive("action");
        test.nekroHome();
        test.game.setVp(10);
        test.nekro.setStrategicCC(2);
        test.nekro.setTacticalCC(3);
        test.nekro.addBreakthrough(MindsieveFollow.BREAKTHROUGH);
        test.nekro.setBreakthroughUnlocked(MindsieveFollow.BREAKTHROUGH, true);
        test.nekro.setPromissoryNote("gift");
        test.nekro.addOwnedPromissoryNoteByID("black_ta");
        test.nekro.setPromissoryNote("black_ta");
        test.sol.addSC(CONSTRUCTION);
        test.game.setSCPlayed(CONSTRUCTION, true);
        test.game.setActivePlayerID(test.sol.getUserID());
        test.place("26", "101");
        test.nekro.addPlanet("lodor");
    }

    private static AiPrompt offer() {
        return prompt("mindsieve", PromptSource.AI_THREAD, NOW, GIFT, AGREEMENT, "deleteButtons");
    }

    private static AiPrompt constructionCard() {
        return prompt("construction", PromptSource.PUBLIC, NOW, "sc_no_follow_4", "construction_pds", DOCK_BUTTON);
    }

    private Optional<String> follow(AiPrompt... prompts) {
        test.game.setLastModifiedDate(modified++);
        return StrategyCardRules.follow(test.context(prompts)).map(AiTestGame::pressedId);
    }

    private Optional<String> giveFirst() {
        AiPrompt card = constructionCard();
        AiDecision press = AiDecision.press(card, card.buttons().get(2), "follow");
        return MindsieveFollow.giveNoteFirst(test.context(offer()), CONSTRUCTION, press)
                .map(AiTestGame::pressedId);
    }

    // With two strategy tokens left a token is worth 1 trade good, as much as the gift: the note goes instead.
    @Test
    void givesTheCheapestNoteInsteadOfAToken() {
        assertThat(giveFirst()).hasValue(GIFT);
    }

    // With three tokens a token is cheap (0.75) and the gift (1.0) costs more: it pays with a token.
    @Test
    void keepsItsNotesWhenTokensArePlentiful() {
        test.nekro.setStrategicCC(3);

        assertThat(giveFirst()).isEmpty();
    }

    @Test
    void needsTheBreakthroughUnlocked() {
        test.nekro.setBreakthroughUnlocked(MindsieveFollow.BREAKTHROUGH, false);

        assertThat(giveFirst()).isEmpty();
    }

    // A note in its play area cannot be given.
    @Test
    void neverGivesANoteFromItsPlayArea() {
        test.nekro.addPromissoryNoteToPlayArea("gift");

        assertThat(giveFirst()).isEmpty();
    }

    // Wired into following: the note is sent first, then the secondary is resolved as usual.
    @Test
    void theFollowSendsTheNoteFirst() {
        assertThat(follow(constructionCard(), offer())).hasValue(GIFT);
    }

    @Test
    void thenResolvesTheSecondaryWithoutAToken() {
        assertThat(follow(constructionCard(), offer())).hasValue(GIFT);
        test.nekro.addFollowedSC(CONSTRUCTION);

        assertThat(follow(constructionCard())).hasValue(DOCK_BUTTON);
    }

    // Out of strategy tokens it still follows, paying with the note.
    @Test
    void followsWithoutAnyStrategyToken() {
        test.nekro.setStrategicCC(0);

        assertThat(MindsieveFollow.covers(test.context(offer()), StrategyCard.CONSTRUCTION))
                .isTrue();
        assertThat(follow(constructionCard(), offer())).hasValue(GIFT);
    }

    // The bot's Trade follow button spends a strategy token even after a Mindsieve follow, so Trade is left alone.
    @Test
    void neverForTrade() {
        test.sol.addSC(5);
        test.game.setSCPlayed(5, true);
        AiPrompt trade = prompt("mindsieve5", PromptSource.AI_THREAD, NOW, "mindsieveFollow_5_gift");

        assertThat(MindsieveFollow.cheapestGift(test.context(trade), 5)).isEmpty();
    }

    @Test
    void withoutTheBreakthroughItDoesNotFollowWithoutATokenEither() {
        test.nekro.setStrategicCC(0);
        test.nekro.setBreakthroughUnlocked(MindsieveFollow.BREAKTHROUGH, false);

        assertThat(follow(constructionCard(), offer())).isEmpty();
    }
}
