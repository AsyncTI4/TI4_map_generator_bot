package ti4.ai.tech;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// At the start of the status phase a player with ships in an entropic scar may spend a strategy token to gain one of
// its faction technologies, prerequisites ignored. The bot offers them as buttons on a public message.
class EntropicScarRulesTest extends BaseTi4Test {

    private static final String SCAR_TOKEN = "token_entropicscar_async.png";
    private static final AiPrompt OFFER = AiTestGame.prompt(
            "scar",
            PromptSource.PUBLIC,
            NOW,
            List.of("FFCC_sol_entropicScar_ac2", "FFCC_sol_entropicScar_so2", "deleteButtons"),
            List.of("Advanced Carrier II", "Spec Ops II", "No Thanks"));

    private AiTestGame test;
    private Tile scar;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        scar = test.place("24", AiTestGame.neighbourOf(AiTestGame.HOME));
        scar.addToken(SCAR_TOKEN, "space");
        test.units(scar, "space", test.sol, UnitType.Cruiser, 1);
        test.sol.setStrategicCC(2);
        test.game.setPhaseOfGame("statusScoring");
    }

    // Sol's two faction technologies are offered; Advanced Carrier II is the better one to have.
    @Test
    void takesTheBestFactionTechnologyForAStrategyToken() {
        assertThat(pressedId(next(OFFER).orElseThrow())).isEqualTo("FFCC_sol_entropicScar_ac2");
    }

    @Test
    void takesTheOtherOneOnceItOwnsTheFirst() {
        test.sol.addTech("ac2");

        assertThat(pressedId(next(OFFER).orElseThrow())).isEqualTo("FFCC_sol_entropicScar_so2");
    }

    // Holding Adapt New Strategies with one faction technology already, the second one is the point: it is bought with
    // the last strategy token even though it is the lesser technology.
    @Test
    void buysTheTechnologyThatCompletesAdaptNewStrategies() {
        test.sol.setSecret("ans");
        test.sol.addTech("ac2");
        test.sol.setStrategicCC(1);

        assertThat(pressedId(next(OFFER).orElseThrow())).isEqualTo("FFCC_sol_entropicScar_so2");
    }

    // With no strategy token to spend the offer is declined, which also removes the message.
    @Test
    void declinesWithoutAStrategyToken() {
        test.sol.setStrategicCC(0);

        assertThat(pressedId(next(OFFER).orElseThrow())).isEqualTo("deleteButtons");
    }

    @Test
    void declinesWhenBothTechnologiesAreOwned() {
        test.sol.addTech("ac2");
        test.sol.addTech("so2");

        assertThat(next(OFFER)).isEmpty();
    }

    // An old offer is not used after the ships have left the scar, or outside the status phase.
    @Test
    void ignoresAnOfferWithoutShipsInTheScarOrOutsideTheStatusPhase() {
        test.game.setPhaseOfGame("action");
        assertThat(next(OFFER)).isEmpty();

        test.game.setPhaseOfGame("statusScoring");
        scar.removeAllUnits(test.sol.getColorID());
        assertThat(next(OFFER)).isEmpty();
    }

    @Test
    void doesNotPressTheSameButtonTwice() {
        AiDecision first = next(OFFER).orElseThrow();
        Set<String> pressed = Set.of(
                OFFER.messageId() + "|" + ((AiDecision.Press) first).button().customId());

        Optional<AiDecision> second = EntropicScarRules.next(test.contextFor(test.sol, pressed, NOW, OFFER));

        assertThat(second).isPresent();
        assertThat(pressedId(second.get())).isNotEqualTo(pressedId(first));
    }

    private Optional<AiDecision> next(AiPrompt prompt) {
        return EntropicScarRules.next(test.contextFor(test.sol, Set.of(), NOW, prompt));
    }
}
