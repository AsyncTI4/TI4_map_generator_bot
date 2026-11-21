package ti4.ai.xxcha;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.strategy.ReadyingValue;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Ggrocuto Rinn costs an action but no token. Mid-round it is worth using when the readied planet raises what the
// seat is about to spend (production, research, Leadership tokens, the custodians) by more than the tactical action
// it replaces; it is the stall when only a stalling move is on offer; and an unscored spend objective keeps it for
// the status phase.
class XxchaAgentTimingTest extends BaseTi4Test {

    private static final int LEADERSHIP = 1;
    private static final int TECHNOLOGY = 7;

    private AiTestGame test;
    private Player xxcha;

    // Xxcha holds its home planets, ready, with a space dock on Archon Ren, three tactic tokens, and Sol still to act.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        xxcha = test.addSeat(AiTestGame.SECOND_AI_ID, "xxcha", "green");
        test.isActive(xxcha, "action");
        Tile home = test.place("14", AiTestGame.HOME);
        xxcha.addPlanet("archonren");
        xxcha.addPlanet("archontau");
        test.units(home, "archonren", xxcha, UnitType.Spacedock, 1);
        xxcha.addLeader("xxchaagent");
        xxcha.setTacticalCC(3);
        xxcha.setFleetCC(3);
        xxcha.setStrategicCC(2);
    }

    private void holdExhausted(String tileId, String position, String... planets) {
        test.place(tileId, position);
        for (String planet : planets) {
            xxcha.addPlanet(planet);
            xxcha.exhaustPlanet(planet);
        }
    }

    private AiTurnContext context() {
        return test.contextFor(xxcha, Set.of(), NOW, turn());
    }

    private static ti4.ai.perception.AiPrompt turn() {
        return prompt(
                "turn",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_xxcha_tacticalAction",
                "FFCC_xxcha_componentAction",
                "FFCC_xxcha_passForRound");
    }

    private void tacticalPlanScoring(double score) {
        TacticalRules.remember(
                context(), new TacticalPlan(TacticalPlan.Kind.EXPAND, AiTestGame.HOME, List.of(), Map.of(), score));
    }

    private Optional<AiDecision> insteadOfTacticalAction() {
        AiTurnContext context = context();
        return XxchaAgentRules.insteadOfTacticalAction(context, Prompts.thisTurn(context));
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private void reveal(String objective) {
        test.game
                .getRevealedPublicObjectives()
                .put(objective, test.game.getRevealedPublicObjectives().size() + 1);
    }

    // Jord (4/2) is exhausted and a dock is free: readying it lets the dock build 4 resources more than it can now, so
    // the agent goes first when the tactical action on offer is a modest one.
    @Test
    void readiesAPlanetBeforeProductionItWouldFund() {
        xxcha.exhaustPlanet("archonren");
        xxcha.exhaustPlanet("archontau");
        holdExhausted("01", "201", "jord");
        tacticalPlanScoring(1.2);

        assertThat(ReadyingValue.bestSingle(test.game, xxcha, xxcha.getExhaustedPlanets())
                        .orElseThrow()
                        .planet())
                .isEqualTo("jord");
        assertThat(pressed(insteadOfTacticalAction())).isEqualTo("FFCC_xxcha_componentAction");
    }

    // A tactical action that scores more than the readied planet is worth is taken first; the agent can still be used
    // before passing.
    @Test
    void takesAStrongerTacticalActionFirst() {
        xxcha.exhaustPlanet("archonren");
        xxcha.exhaustPlanet("archontau");
        holdExhausted("01", "201", "jord");
        tacticalPlanScoring(50);

        assertThat(insteadOfTacticalAction()).isEmpty();
    }

    // With no tactic token there is no production to fund, so readying the planet is worth nothing before a spend.
    @Test
    void ignoresProductionWithoutATacticToken() {
        xxcha.setTacticalCC(0);
        xxcha.exhaustPlanet("archonren");
        xxcha.exhaustPlanet("archontau");
        holdExhausted("01", "201", "jord");
        tacticalPlanScoring(1.2);

        assertThat(insteadOfTacticalAction()).isEmpty();
    }

    // Sol's Leadership is still to come. Meer (0/4) is exhausted: readying it buys a command token for 3 influence.
    @Test
    void readiesAnInfluencePlanetBeforeLeadershipGoesOff() {
        xxcha.setTacticalCC(0);
        xxcha.setStrategicCC(2);
        holdExhausted("37", "201", "meer");
        test.sol.addSC(LEADERSHIP);
        tacticalPlanScoring(1.2);

        assertThat(ReadyingValue.bestSingle(test.game, xxcha, List.of("meer"))
                        .orElseThrow()
                        .value())
                .isGreaterThanOrEqualTo(1.5);
        assertThat(pressed(insteadOfTacticalAction())).isEqualTo("FFCC_xxcha_componentAction");
    }

    @Test
    void ignoresLeadershipOnceItHasBeenPlayed() {
        xxcha.setTacticalCC(0);
        holdExhausted("37", "201", "meer");
        test.sol.addSC(LEADERSHIP);
        test.game.setSCPlayed(LEADERSHIP, true);
        tacticalPlanScoring(1.2);

        assertThat(insteadOfTacticalAction()).isEmpty();
    }

    // The custodians cost 6 influence. Archon Ren's 3 are ready; readying Meer's 4 pays them, so the agent goes first
    // while a loaded carrier waits one system from Mecatol Rex.
    @Test
    void readiesAnInfluencePlanetToPayTheCustodians() {
        holdExhausted("37", "201", "meer");
        Tile rex = test.place("18", "000");
        rex.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
        Tile next = test.place("26", AiTestGame.neighbourOf("000"));
        test.units(next, "space", xxcha, UnitType.Carrier, 1);
        test.units(next, "space", xxcha, UnitType.Infantry, 1);
        tacticalPlanScoring(1.2);

        assertThat(ReadyingValue.bestSingle(test.game, xxcha, List.of("meer"))
                        .orElseThrow()
                        .value())
                .isGreaterThanOrEqualTo(1.5);
        assertThat(pressed(insteadOfTacticalAction())).isEqualTo("FFCC_xxcha_componentAction");
    }

    // Tarmann (1/0, a biotic specialty) is exhausted and its skip cannot be used. Hyper Metabolism needs two biotic
    // technologies and Xxcha owns one (Neural Motivator), so with Technology still to come, readying Tarmann puts it in
    // reach.
    @Test
    void readiesATechnologySkipPlanetBeforeResearch() {
        xxcha.setTacticalCC(0);
        holdExhausted("22", "201", "tarmann");
        test.sol.addSC(TECHNOLOGY);
        xxcha.addTech("nm");
        tacticalPlanScoring(1.2);

        ReadyingValue.Pick pick =
                ReadyingValue.bestSingle(test.game, xxcha, List.of("tarmann")).orElseThrow();
        assertThat(pick.techSkipValue()).isGreaterThan(0);
    }

    @Test
    void aTechnologySkipIsWorthNothingOnceTheTechnologyCardIsPlayed() {
        xxcha.setTacticalCC(0);
        holdExhausted("22", "201", "tarmann");
        xxcha.addTech("nm");
        test.sol.addSC(TECHNOLOGY);
        test.game.setSCPlayed(TECHNOLOGY, true);

        assertThat(ReadyingValue.bestSingle(test.game, xxcha, List.of("tarmann"))
                        .orElseThrow()
                        .techSkipValue())
                .isZero();
    }

    // Erect a Monument is revealed and Abyz would complete the payment at the status phase: the agent is kept for
    // that, even though Jord could fund a build now.
    @Test
    void keepsTheAgentForAStatusPhaseSpendObjective() {
        xxcha.exhaustPlanet("archonren");
        xxcha.exhaustPlanet("archontau");
        holdExhausted("01", "201", "jord");
        holdExhausted("38", "202", "abyz");
        reveal("monument");
        tacticalPlanScoring(1.2);

        assertThat(insteadOfTacticalAction()).isEmpty();
    }

    // An objective the agent cannot complete (Found a Golden Age needs 16 resources) still raises the bar: a modest use
    // is held back for the status phase, where readying a planet may yet matter.
    @Test
    void anObjectiveItCannotCompleteStillRaisesTheBarForAnEarlyUse() {
        xxcha.exhaustPlanet("archonren");
        xxcha.exhaustPlanet("archontau");
        holdExhausted("01", "201", "jord");
        reveal("golden_age");
        tacticalPlanScoring(1.2);

        assertThat(ReadyingValue.bestSingle(test.game, xxcha, List.of("jord"))
                        .orElseThrow()
                        .unlocksScore())
                .isFalse();
        assertThat(insteadOfTacticalAction()).isEmpty();
    }

    // Only a stalling move on offer (a plan below the worthwhile score) and Sol still acting: the agent is the stall.
    @Test
    void isTheStallWhenOnlyAStallingMoveIsOnOffer() {
        holdExhausted("38", "201", "abyz");
        tacticalPlanScoring(0.3);

        assertThat(pressed(insteadOfTacticalAction())).isEqualTo("FFCC_xxcha_componentAction");
    }

    @Test
    void doesNotStallWhenEveryoneElseHasPassed() {
        holdExhausted("38", "201", "abyz");
        test.sol.setPassed(true);
        test.nekro.setPassed(true);
        tacticalPlanScoring(0.3);

        assertThat(insteadOfTacticalAction()).isEmpty();
    }

    @Test
    void doesNotStallWithoutAnExhaustedPlanet() {
        tacticalPlanScoring(0.3);

        assertThat(insteadOfTacticalAction()).isEmpty();
    }
}
