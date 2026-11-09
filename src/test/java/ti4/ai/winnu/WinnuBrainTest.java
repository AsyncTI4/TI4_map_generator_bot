package ti4.ai.winnu;

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
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The Winnu rules are wired into the brain: the unowned Reclaimer offer and the hero's Imperial card message are public
// windows the seat can see, the Reclaimer offer is answered before the turn is ended, and Lazax Gate Folding is used in
// place of passing.
class WinnuBrainTest extends BaseTi4Test {

    private final NekroBrain brain = new NekroBrain();

    private WinnuTestBoard board;

    @BeforeEach
    void setUp() {
        board = new WinnuTestBoard();
        board.test.game.setVp(10);
        board.winnuActive("action");
    }

    @Test
    void theUnownedWinnuButtonsArePublicWindows() {
        assertThat(brain.publicWindowHandlerPrefixes())
                .contains(ReclaimerRules.PREFIX, MathisMathinusRules.IMPERIAL_POINT, MathisMathinusRules.PICK_PREFIX);
    }

    @Test
    void answersTheReclaimerOfferBeforeEndingTheTurn() {
        Tile lodor = board.test.place("26", "101");
        board.winnu.addPlanet("lodor");
        board.test.units(lodor, "lodor", board.winnu, UnitType.Mech, 1);
        AiPrompt offer = prompt(
                "reclaimer",
                PromptSource.PUBLIC,
                NOW,
                "winnuStructure_sd_lodor",
                "winnuStructure_pds_lodor",
                "deleteButtons");
        AiPrompt turn = prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_winnu_turnEnd", "FFCC_winnu_doAnotherAction");

        assertThat(AiTestGame.pressedId(brain.decide(context(turn, offer)))).isEqualTo("winnuStructure_sd_lodor");
    }

    @Test
    void usesLazaxGateFoldingInPlaceOfPassing() {
        board.holdMecatol();
        board.winnu.addTech(LazaxGateFolding.TECH);
        board.winnu.setTacticalCC(0);
        AiPrompt turn =
                prompt("turn", PromptSource.PUBLIC, NOW, "FFCC_winnu_componentAction", "FFCC_winnu_passForRound");

        assertThat(AiTestGame.pressedId(brain.decide(context(turn)))).isEqualTo("FFCC_winnu_componentAction");
        assertThat(board.test.memoryOf(board.winnu).snapshot().keySet())
                .anyMatch(key -> key.startsWith("lazaxGateFolding|"));
    }

    private AiTurnContext context(AiPrompt... prompts) {
        return board.test.contextFor(board.winnu, Set.of(), NOW, prompts);
    }
}
