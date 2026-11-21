package ti4.ai.winnu;

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
import ti4.ai.strategy.StrategyCardRules;
import ti4.testUtils.BaseTi4Test;

// Mathis Mathinus (Winnu hero): ACTION, perform the primary of any strategy card, then choose other players who may
// perform its secondary, then purge. The AI uses it for Imperial when that scores: from the component action menu it
// plays the hero, picks Imperial from the bot's unowned "winnuHero_<initiative>" buttons, lets nobody follow, and then
// resolves the primary from the card message the bot posts (with no follow buttons).
class MathisMathinusRulesTest extends BaseTi4Test {

    private static final int IMPERIAL = 8;
    private static final long LATER = NOW + 1_000L;

    private WinnuTestBoard board;
    private AiPrompt turn;

    @BeforeEach
    void setUp() {
        board = new WinnuTestBoard();
        board.winnuActive("action");
        board.unlockLeader(MathisMathinusRules.HERO);
        for (int card = 1; card <= IMPERIAL; card++) board.test.game.setScTradeGood(card, 0);
        turn = prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_winnu_componentAction", "FFCC_winnu_passForRound");
    }

    // Holding Mecatol Rex, Imperial is a point: worth playing the hero before passing.
    @Test
    void playsTheHeroForTheImperialPointBeforePassing() {
        board.holdMecatol();

        assertThat(MathisMathinusRules.beforePassing(context(Set.of(), turn), List.of(turn))
                        .map(AiTestGame::pressedId))
                .contains("FFCC_winnu_componentAction");
    }

    // One point is not enough to give up a tactical action for: that waits for two points or the win.
    @Test
    void keepsItsTacticalActionForASinglePoint() {
        board.holdMecatol();

        assertThat(MathisMathinusRules.insteadOfTacticalAction(context(Set.of(), turn), List.of(turn)))
                .isEmpty();
    }

    @Test
    void playsItInsteadOfATacticalActionWhenThePointWinsTheGame() {
        board.holdMecatol();
        board.test.game.setVp(10);
        board.test.points(board.winnu, 9);

        assertThat(MathisMathinusRules.insteadOfTacticalAction(context(Set.of(), turn), List.of(turn))
                        .map(AiTestGame::pressedId))
                .contains("FFCC_winnu_componentAction");
    }

    @Test
    void keepsTheHeroWhenImperialScoresNothing() {
        assertThat(MathisMathinusRules.beforePassing(context(Set.of(), turn), List.of(turn)))
                .isEmpty();
    }

    @Test
    void walksThroughTheHeroPrompts() {
        board.holdMecatol();
        AiTurnContext start = context(Set.of(), turn);
        MathisMathinusRules.beforePassing(start, List.of(turn));

        AiPrompt menu = prompt("menu", PromptSource.PUBLIC, LATER, "FFCC_winnu_componentActionRes_leader_winnuhero");
        assertThat(MathisMathinusRules.next(context(Set.of(), menu)).map(AiTestGame::pressedId))
                .contains("FFCC_winnu_componentActionRes_leader_winnuhero");

        AiPrompt picks = prompt("picks", PromptSource.PUBLIC, LATER, "winnuHero_1", "winnuHero_7", "winnuHero_8");
        assertThat(MathisMathinusRules.next(context(Set.of(), picks)).map(AiTestGame::pressedId))
                .contains("winnuHero_" + IMPERIAL);

        AiPrompt followers = prompt(
                "followers",
                PromptSource.PUBLIC,
                LATER,
                List.of(
                        "FFCC_winnu_winnuHeroAllowFollow_8_sol",
                        "FFCC_winnu_winnuHeroAllowFollow_8_nekro",
                        "deleteButtons"),
                List.of("Sol", "Nekro", "Done Choosing"));
        assertThat(MathisMathinusRules.next(context(Set.of(), followers)).map(AiTestGame::pressedId))
                .contains("deleteButtons");
    }

    // The primary comes from the hero's card message, which has no follow buttons; holding Mecatol it scores the point.
    @Test
    void resolvesThePrimaryFromTheHerosCardMessage() {
        board.holdMecatol();
        AiTurnContext start = context(Set.of(), turn);
        StrategyCardRules.beginOverruledPrimary(start, IMPERIAL);
        AiPrompt card = AiTestGame.withContent(
                prompt("card", PromptSource.PUBLIC, LATER, "sc_draw_so", "score_imperial", "scoreAnObjective"),
                board.test.game.getSCEmojiWordRepresentation(IMPERIAL) + " played by " + board.winnu.getRepresentation()
                        + ".\n\nPlease indicate your choice with these buttons.");

        assertThat(StrategyCardRules.resolvePrimary(context(Set.of(), card)).map(AiTestGame::pressedId))
                .contains("score_imperial");
    }

    private AiTurnContext context(Set<String> pressed, AiPrompt... prompts) {
        return board.test.contextFor(board.winnu, pressed, NOW, prompts);
    }
}
