package ti4.ai.winnu;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.Adjacency;
import ti4.ai.eval.MovementGraph;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.testUtils.BaseTi4Test;

// Lazax Gate Folding: while the Winnu do not control Mecatol Rex, their tactical actions treat its system as holding
// an alpha and a beta wormhole, so ships next to (or in) an alpha or beta system can reach Mecatol in one move. Once
// they hold it, the card's ACTION places 1 infantry there.
class LazaxGateFoldingTest extends BaseTi4Test {

    // Far from Mecatol (two rings out), so only the wormhole makes it reachable in one move.
    private static final String LODOR = "301";

    private WinnuTestBoard board;

    @BeforeEach
    void setUp() {
        board = new WinnuTestBoard();
        board.test.place("26", LODOR);
        board.winnu.addTech(LazaxGateFolding.TECH);
    }

    @Test
    void anAlphaSystemLeadsToMecatolWhileTheWinnuDoNotHoldIt() {
        assertThat(LazaxGateFolding.movementLinks(board.test.game, board.winnu, LODOR))
                .containsExactly(WinnuTestBoard.MECATOL_POSITION);
        assertThat(Adjacency.forMovement(board.test.game, board.winnu, LODOR))
                .contains(WinnuTestBoard.MECATOL_POSITION);
        assertThat(MovementGraph.reach(board.test.game, board.winnu, LODOR, 1))
                .containsKey(WinnuTestBoard.MECATOL_POSITION);
    }

    @Test
    void noShortcutOnceMecatolIsHeld() {
        board.holdMecatol();

        assertThat(LazaxGateFolding.movementLinks(board.test.game, board.winnu, LODOR))
                .isEmpty();
    }

    @Test
    void noShortcutWithoutTheTechnology() {
        assertThat(LazaxGateFolding.movementLinks(board.test.game, board.test.sol, LODOR))
                .isEmpty();
        assertThat(Adjacency.forMovement(board.test.game, board.test.sol, LODOR))
                .doesNotContain(WinnuTestBoard.MECATOL_POSITION);
    }

    // Holding Mecatol, the free infantry is placed before passing: the component action menu first, then the card.
    @Test
    void placesTheInfantryOnMecatolBeforePassing() {
        board.holdMecatol();
        board.winnuActive("action");
        AiPrompt turn =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_winnu_componentAction", "FFCC_winnu_passForRound");

        Optional<AiDecision> open = LazaxGateFolding.beforePassing(context(turn), List.of(turn));

        assertThat(open.map(AiTestGame::pressedId)).contains("FFCC_winnu_componentAction");
        AiPrompt menu = prompt("menu", PromptSource.PUBLIC, NOW, "FFCC_winnu_exhaustTech_lgf");
        assertThat(LazaxGateFolding.next(context(menu)).map(AiTestGame::pressedId))
                .contains("FFCC_winnu_exhaustTech_lgf");
        assertThat(LazaxGateFolding.beforePassing(context(turn), List.of(turn))).isEmpty();
    }

    @Test
    void keepsTheCardWithoutMecatol() {
        board.winnuActive("action");
        AiPrompt turn = prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_winnu_componentAction");

        assertThat(LazaxGateFolding.beforePassing(context(turn), List.of(turn))).isEmpty();
    }

    private AiTurnContext context(AiPrompt... prompts) {
        return board.test.contextFor(board.winnu, Set.of(), NOW, prompts);
    }
}
