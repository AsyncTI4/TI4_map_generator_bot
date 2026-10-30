package ti4.ai.sol;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import java.util.Set;
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

// Claire Gibson offers a free infantry at the start of a ground combat on a planet the seat controls. It is free, so
// the seat always takes it, but only before either side has rolled.
class SolCommanderRulesTest extends BaseTi4Test {

    private static final String PLANET = "lodor";
    private static final String BUTTON = "FFCC_sol_utilizeSolCommander_lodor";

    private AiTestGame test;
    private Player sol;
    private String position;
    private Tile tile;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        tile = test.place("26", position);
        sol.addPlanet(PLANET);
        test.units(tile, PLANET, sol, UnitType.Infantry, 2);
        test.units(tile, PLANET, test.nekro, UnitType.Infantry, 3);
        test.isActive(test.nekro, "action");
        test.game.setActiveSystem(position);
    }

    private AiTurnContext context(Set<String> pressed, AiPrompt... prompts) {
        return test.contextFor(sol, pressed, NOW, prompts);
    }

    private static AiPrompt combatStart() {
        return prompt("start", PromptSource.COMBAT_THREAD, NOW, BUTTON, "combatRoll_26_lodor");
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    @Test
    void placesTheFreeInfantryBeforeItsFirstRoll() {
        assertThat(pressed(SolCommanderRules.next(context(Set.of(), combatStart()))))
                .isEqualTo(BUTTON);
    }

    @Test
    void asksOnlyOnce() {
        AiPrompt start = combatStart();
        Set<String> pressed =
                Set.of(AiTurnContext.pressKey(start, start.buttons().getFirst()));

        assertThat(SolCommanderRules.next(context(pressed, start))).isEmpty();
    }

    @Test
    void skipsItOnceTheSeatHasRolled() {
        test.game.setStoredValue("combatRoundTrackersol" + position + PLANET, "1");

        assertThat(SolCommanderRules.next(context(Set.of(), combatStart()))).isEmpty();
    }

    @Test
    void skipsItOnceTheOpponentHasRolled() {
        test.game.setStoredValue("combatRoundTrackernekro" + position + PLANET, "1");

        assertThat(SolCommanderRules.next(context(Set.of(), combatStart()))).isEmpty();
    }

    // Offered means answered: with no infantry left in its reinforcements the press places nothing, but the prompt is
    // not left standing.
    @Test
    void answersTheOfferEvenWithNoInfantryLeft() {
        sol.setUnitCap("gf", 2);

        assertThat(pressed(SolCommanderRules.next(context(Set.of(), combatStart()))))
                .isEqualTo(BUTTON);
    }

    @Test
    void ignoresAButtonThatBelongsToAnotherPlayer() {
        AiPrompt other = prompt("other", PromptSource.COMBAT_THREAD, NOW, "FFCC_nekro_utilizeSolCommander_lodor");

        assertThat(SolCommanderRules.next(context(Set.of(), other))).isEmpty();
    }
}
