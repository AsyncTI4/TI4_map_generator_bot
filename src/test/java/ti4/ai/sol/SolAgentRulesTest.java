package ti4.ai.sol;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.CombatMoment;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Evelyn Delouis gives one ground force an extra die for a combat round. The seat uses it in its own ground combats
// when the die moves the odds enough, and not in other players' combats.
class SolAgentRulesTest extends BaseTi4Test {

    private static final String PLANET = "lodor";

    private AiTestGame test;
    private Player sol;
    private String position;
    private Tile tile;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        sol = test.sol;
        sol.addLeader("solagent");
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        tile = test.place("26", position);
        test.isActive(sol, "action");
        test.game.setActiveSystem(position);
        sol.addPlanet(PLANET);
    }

    private void fight(int solInfantry, int nekroInfantry) {
        test.units(tile, PLANET, sol, UnitType.Infantry, solInfantry);
        test.units(tile, PLANET, test.nekro, UnitType.Infantry, nekroInfantry);
    }

    private CombatMoment moment() {
        return new CombatMoment(position, PLANET, true, 0);
    }

    private AiTurnContext context(AiPrompt... prompts) {
        return test.contextFor(sol, java.util.Set.of(), NOW, prompts);
    }

    private static AiPrompt offer() {
        return prompt("offer", PromptSource.COMBAT_THREAD, NOW, "FFCC_sol_getAgentSelection_solagent");
    }

    private static AiPrompt targets() {
        return prompt(
                "targets",
                PromptSource.COMBAT_THREAD,
                NOW + 1000,
                "exhaustAgent_solagent_sol",
                "exhaustAgent_solagent_nekro");
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    @Test
    void usesTheAgentInACloseGroundCombatOfItsOwnThenPicksItself() {
        fight(3, 3);

        assertThat(pressed(SolAgentRules.atRoundStart(context(offer()), moment())))
                .isEqualTo("FFCC_sol_getAgentSelection_solagent");
        assertThat(SolAgentRules.atRoundStart(context(offer()), moment())).containsInstanceOf(AiDecision.Wait.class);

        assertThat(pressed(SolAgentRules.chooseTarget(context(targets())))).isEqualTo("exhaustAgent_solagent_sol");
        assertThat(SolAgentRules.chooseTarget(context(targets()))).isEmpty();
        assertThat(SolAgentRules.atRoundStart(context(offer()), moment())).isEmpty();
    }

    @Test
    void keepsTheAgentWhenTheExtraDieChangesLittle() {
        fight(8, 1);

        assertThat(SolAgentRules.atRoundStart(context(offer()), moment())).isEmpty();
    }

    @Test
    void usesTheAgentInAnEvenSmallFightBecauseItReadiesEveryRound() {
        fight(1, 1);

        assertThat(pressed(SolAgentRules.atRoundStart(context(offer()), moment())))
                .isEqualTo("FFCC_sol_getAgentSelection_solagent");
    }

    @Test
    void keepsTheAgentOnAHopelessFight() {
        fight(1, 12);

        assertThat(SolAgentRules.atRoundStart(context(offer()), moment())).isEmpty();
    }

    @Test
    void usesTheAgentWhenAMechIsAmongTheDefenders() {
        fight(2, 3);
        test.units(tile, PLANET, sol, UnitType.Mech, 1);

        assertThat(pressed(SolAgentRules.atRoundStart(context(offer()), moment())))
                .isEqualTo("FFCC_sol_getAgentSelection_solagent");
    }

    @Test
    void keepsTheAgentForCombatsItDoesNotFightIn() {
        test.units(tile, PLANET, test.nekro, UnitType.Infantry, 3);

        assertThat(SolAgentRules.atRoundStart(context(offer()), moment())).isEmpty();
    }

    @Test
    void doesNothingForSpaceCombatOrAnExhaustedAgent() {
        fight(3, 3);
        assertThat(SolAgentRules.atRoundStart(context(offer()), new CombatMoment(position, "space", false, 0)))
                .isEmpty();

        sol.getLeader("solagent").orElseThrow().setExhausted(true);

        assertThat(SolAgentRules.atRoundStart(context(offer()), moment())).isEmpty();
    }

    @Test
    void asksOncePerRound() {
        fight(3, 3);
        SolAgentRules.atRoundStart(context(offer()), moment());
        SolAgentRules.chooseTarget(context(targets()));

        assertThat(SolAgentRules.atRoundStart(context(offer()), moment())).isEmpty();
    }

    @Test
    void waitsNoLongerThanItTakesForTheTargetPrompt() {
        fight(3, 3);
        SolAgentRules.atRoundStart(context(offer()), moment());

        AiTurnContext late = test.contextFor(sol, java.util.Set.of(), NOW + 60_000L);

        assertThat(SolAgentRules.atRoundStart(late, moment())).isEmpty();
    }
}
