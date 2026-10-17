package ti4.ai.sol;

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
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

// Jace X returns every command token on the board, so a system activated earlier in the round can be activated again.
// The seat plays it when its command token on a fleet's system is what keeps its best tactical action out of reach.
class SolHeroRulesTest extends BaseTi4Test {

    private AiTestGame test;
    private Player sol;
    private Tile home;
    private Tile target;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        test.game.setRound(4);
        home = test.place("01", AiTestGame.HOME);
        sol.addPlanet("jord");
        test.units(home, "jord", sol, UnitType.Spacedock, 1);
        test.units(home, "jord", sol, UnitType.Infantry, 4);
        test.units(home, "space", sol, UnitType.Carrier, 2);
        test.units(home, "space", sol, UnitType.Cruiser, 2);
        target = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        sol.setTacticalCC(3);
        sol.setStrategicCC(2);
        sol.setFleetCC(5);
        sol.addLeader("solhero");
        sol.unsafeGetLeader("solhero").setLocked(false);
        test.isActive(sol, "action");
    }

    private void fleetIsPinnedByItsOwnToken() {
        home.addCC(Mapper.getCCID(sol.getColor()));
    }

    private AiTurnContext context(AiPrompt... prompts) {
        return test.contextFor(sol, Set.of(), NOW, prompts);
    }

    private static AiPrompt turnMenu() {
        return prompt(
                "turn",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_sol_tacticalAction",
                "FFCC_sol_componentAction",
                "FFCC_sol_passForRound");
    }

    private static AiPrompt componentMenu() {
        return prompt(
                "menu",
                PromptSource.AI_THREAD,
                NOW,
                "FFCC_sol_componentActionRes_leader_solhero",
                "FFCC_sol_componentActionRes_ability_generic");
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private Optional<AiDecision> instead() {
        AiTurnContext context = context(turnMenu());
        return SolHeroRules.insteadOfTacticalAction(context, Prompts.thisTurn(context));
    }

    // The only fleet sits in a system holding the seat's own token, so the free planet next door is out of reach until
    // the tokens come off.
    @Test
    void playsTheHeroWhenItsOwnTokenBlocksTheBestAction() {
        fleetIsPinnedByItsOwnToken();
        assertThat(TacticalPlanner.ranked(test.game, sol)).isEmpty();

        assertThat(pressed(instead())).isEqualTo("FFCC_sol_componentAction");
        assertThat(pressed(SolHeroRules.next(context(componentMenu()))))
                .isEqualTo("FFCC_sol_componentActionRes_leader_solhero");
        assertThat(SolHeroRules.next(context(componentMenu()))).isEmpty();
    }

    @Test
    void keepsTheHeroWhileTheActionIsNotBlocked() {
        assertThat(instead()).isEmpty();
    }

    @Test
    void keepsTheHeroInTheOpeningRounds() {
        fleetIsPinnedByItsOwnToken();
        test.game.setRound(2);

        assertThat(instead()).isEmpty();
    }

    @Test
    void needsATacticalTokenToUseTheFreedSystem() {
        fleetIsPinnedByItsOwnToken();
        sol.setTacticalCC(0);

        assertThat(instead()).isEmpty();
    }

    @Test
    void needsTheHeroToBeUnlocked() {
        fleetIsPinnedByItsOwnToken();
        sol.unsafeGetLeader("solhero").setLocked(true);

        assertThat(instead()).isEmpty();
    }

    @Test
    void usesItInPlaceOfPassingToo() {
        fleetIsPinnedByItsOwnToken();
        AiTurnContext context = context(turnMenu());

        assertThat(pressed(SolHeroRules.beforePassing(context, Prompts.thisTurn(context))))
                .isEqualTo("FFCC_sol_componentAction");
    }

    @Test
    void stopsWaitingOnceTheTurnCanEnd() {
        fleetIsPinnedByItsOwnToken();
        instead();
        AiPrompt over = prompt("over", PromptSource.PUBLIC, NOW + 1000, List.of("FFCC_sol_turnEnd"), List.of());

        assertThat(SolHeroRules.next(context(over))).isEmpty();
        assertThat(SolHeroRules.next(context(componentMenu()))).isEmpty();
    }
}
